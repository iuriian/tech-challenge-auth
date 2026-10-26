package br.com.fiap.oficina.auth.presentation.http

import br.com.fiap.oficina.auth.application.port.ChavesPublicas
import br.com.fiap.oficina.auth.application.usecase.AutenticarClienteUseCase
import br.com.fiap.oficina.auth.application.usecase.ResultadoAutenticacao
import br.com.fiap.oficina.auth.infrastructure.config.TokenSettings
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.cloud.functions.HttpRequest
import com.google.cloud.functions.HttpResponse
import java.util.logging.Level
import java.util.logging.Logger

class AuthHttpHandler(
    private val autenticar: AutenticarClienteUseCase,
    private val chaves: ChavesPublicas,
    private val settings: TokenSettings,
    private val mapper: ObjectMapper,
) {
    // Esta é a fronteira externa da função: qualquer exceção que escape aqui vira
    // stack trace na resposta ao cliente. O catch amplo é deliberado e traduz
    // qualquer falha inesperada em 503, registrando o detalhe apenas no log.
    @Suppress("TooGenericExceptionCaught")
    fun handle(
        req: HttpRequest,
        res: HttpResponse,
    ) {
        val path = req.path.trimEnd('/')
        try {
            when {
                req.method == "GET" && path.endsWith(JWKS_PATH) ->
                    responder(res, 200, chaves.jwks(), CACHE_JWKS)

                req.method == "GET" && path.endsWith(DISCOVERY_PATH) ->
                    responder(res, 200, mapper.writeValueAsString(discovery()), CACHE_DISCOVERY)

                req.method == "POST" && path.endsWith(TOKEN_PATH) ->
                    emitirToken(req, res)

                else -> erro(res, 404, "not_found", "Rota inexistente")
            }
        } catch (e: Exception) {
            log.log(Level.SEVERE, "Falha ao processar ${req.method} $path", e)
            erro(res, 503, "temporarily_unavailable", "Serviço temporariamente indisponível")
        }
    }

    private fun emitirToken(
        req: HttpRequest,
        res: HttpResponse,
    ) {
        val cpf = runCatching { mapper.readValue<TokenRequest>(req.reader).cpf }.getOrNull()

        when (val resultado = autenticar.executar(cpf)) {
            is ResultadoAutenticacao.Autenticado ->
                responder(
                    res,
                    200,
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
                erro(res, 400, "invalid_request", "CPF inválido")

            ResultadoAutenticacao.NaoAutorizado -> {
                log.info("Autenticação negada")
                erro(res, 401, "invalid_grant", "Cliente não autorizado")
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

    private fun responder(
        res: HttpResponse,
        status: Int,
        json: String,
        cacheControl: String,
    ) {
        res.setStatusCode(status)
        res.setContentType("application/json; charset=utf-8")
        res.appendHeader("Cache-Control", cacheControl)
        res.writer.write(json)
    }

    private fun erro(
        res: HttpResponse,
        status: Int,
        codigo: String,
        descricao: String,
    ) = responder(
        res,
        status,
        mapper.writeValueAsString(mapOf("error" to codigo, "error_description" to descricao)),
        NO_STORE,
    )

    companion object {
        const val TOKEN_PATH = "/auth/token"
        const val JWKS_PATH = "/.well-known/jwks.json"
        const val DISCOVERY_PATH = "/.well-known/openid-configuration"

        private const val CACHE_JWKS = "public, max-age=300"
        private const val CACHE_DISCOVERY = "public, max-age=3600"
        private const val NO_STORE = "no-store"

        private val log: Logger = Logger.getLogger(AuthHttpHandler::class.java.name)
    }
}
