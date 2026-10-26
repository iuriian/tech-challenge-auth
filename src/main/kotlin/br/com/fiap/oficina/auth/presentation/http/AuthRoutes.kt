package br.com.fiap.oficina.auth.presentation.http

import br.com.fiap.oficina.auth.application.port.ChavesPublicas
import br.com.fiap.oficina.auth.application.usecase.AutenticarClienteUseCase
import br.com.fiap.oficina.auth.application.usecase.ResultadoAutenticacao
import br.com.fiap.oficina.auth.infrastructure.config.TokenSettings
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.IgnoreTrailingSlash
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Fronteira externa do serviço: traduz HTTP em casos de uso e, na volta, qualquer resultado ou
 * falha em resposta JSON. Nada que aconteça aqui pode escapar como stack trace para o cliente.
 */
class AuthRoutes(
    private val autenticar: AutenticarClienteUseCase,
    private val chaves: ChavesPublicas,
    private val settings: TokenSettings,
    private val mapper: ObjectMapper,
) {
    fun configurar(app: Application) {
        // O API Gateway repassa o caminho exato declarado no OpenAPI, mas clientes
        // gerados e curl mandam /auth/token/ sem avisar.
        app.install(IgnoreTrailingSlash)

        app.install(StatusPages) {
            // O catch amplo é deliberado: qualquer falha inesperada vira 503 e o
            // detalhe (mensagem de driver com credencial, por exemplo) fica no log.
            exception<Throwable> { call, causa ->
                log.log(
                    Level.SEVERE,
                    "Falha ao processar ${call.request.httpMethod.value} ${call.request.path()}",
                    causa,
                )
                call.erro(HttpStatusCode.ServiceUnavailable, "temporarily_unavailable", INDISPONIVEL)
            }

            // Método inesperado em rota conhecida responde como rota inexistente: o
            // contrato publicado no gateway não prevê 405, e o 404 não conta ao
            // cliente quais métodos existem.
            status(HttpStatusCode.NotFound, HttpStatusCode.MethodNotAllowed) { call, _ ->
                call.erro(HttpStatusCode.NotFound, "not_found", "Rota inexistente")
            }
        }

        app.routing { registrar() }
    }

    private fun Route.registrar() {
        PREFIXOS.forEach { prefixo ->
            post(prefixo + TOKEN_PATH) { call.emitirToken() }

            get(prefixo + JWKS_PATH) {
                call.responder(HttpStatusCode.OK, chaves.jwks(), CACHE_JWKS)
            }

            get(prefixo + DISCOVERY_PATH) {
                call.responder(HttpStatusCode.OK, mapper.writeValueAsString(discovery()), CACHE_DISCOVERY)
            }
        }
    }

    private suspend fun ApplicationCall.emitirToken() {
        val cpf = runCatching { mapper.readValue<TokenRequest>(receiveText()).cpf }.getOrNull()

        // O caso de uso é bloqueante (consulta JDBC e assinatura RSA). Fora do
        // dispatcher de I/O ele prenderia as threads do event loop do CIO.
        val resultado = withContext(Dispatchers.IO) { autenticar.executar(cpf) }

        when (resultado) {
            is ResultadoAutenticacao.Autenticado ->
                responder(
                    HttpStatusCode.OK,
                    mapper.writeValueAsString(
                        mapOf(
                            "access_token" to resultado.token.accessToken,
                            "token_type" to "Bearer",
                            "expires_in" to resultado.token.expiraEmSegundos,
                        ),
                    ),
                    NO_STORE,
                )

            ResultadoAutenticacao.CpfInvalido ->
                erro(HttpStatusCode.BadRequest, "invalid_request", "CPF inválido")

            ResultadoAutenticacao.NaoAutorizado -> {
                log.info("Autenticação negada")
                erro(HttpStatusCode.Unauthorized, "invalid_grant", "Cliente não autorizado")
            }
        }
    }

    private fun discovery(): Map<String, Any> =
        mapOf(
            "issuer" to settings.issuer,
            "jwks_uri" to "${settings.issuer}$JWKS_PATH",
            "token_endpoint" to "${settings.issuer}$TOKEN_PATH",
            "id_token_signing_alg_values_supported" to listOf("RS256"),
            "response_types_supported" to listOf("token"),
            "subject_types_supported" to listOf("public"),
        )

    private suspend fun ApplicationCall.responder(
        status: HttpStatusCode,
        json: String,
        cacheControl: String,
    ) {
        response.header(HttpHeaders.CacheControl, cacheControl)
        respondText(json, JSON_UTF8, status)
    }

    private suspend fun ApplicationCall.erro(
        status: HttpStatusCode,
        codigo: String,
        descricao: String,
    ) = responder(
        status,
        mapper.writeValueAsString(mapOf("error" to codigo, "error_description" to descricao)),
        NO_STORE,
    )

    companion object {
        const val TOKEN_PATH = "/auth/token"
        const val JWKS_PATH = "/.well-known/jwks.json"
        const val DISCOVERY_PATH = "/.well-known/openid-configuration"

        // Cada rota é registrada também sob um primeiro segmento livre, para o caso
        // de o gateway passar a expor tudo atrás de um prefixo de versão.
        private val PREFIXOS = listOf("", "/{prefixo}")

        private val JSON_UTF8 = ContentType.Application.Json.withCharset(Charsets.UTF_8)

        private const val INDISPONIVEL = "Serviço temporariamente indisponível"
        private const val CACHE_JWKS = "public, max-age=300"
        private const val CACHE_DISCOVERY = "public, max-age=3600"
        private const val NO_STORE = "no-store"

        private val log: Logger = Logger.getLogger(AuthRoutes::class.java.name)
    }
}
