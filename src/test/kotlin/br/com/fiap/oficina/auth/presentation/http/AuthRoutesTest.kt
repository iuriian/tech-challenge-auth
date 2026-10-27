package br.com.fiap.oficina.auth.presentation.http

import br.com.fiap.oficina.auth.application.port.ChavesPublicas
import br.com.fiap.oficina.auth.application.port.TokenEmitido
import br.com.fiap.oficina.auth.application.usecase.ResultadoAutenticacao
import br.com.fiap.oficina.auth.infrastructure.config.TokenSettings
import br.com.fiap.oficina.auth.support.AutenticarUsuarioUseCaseFake
import br.com.fiap.oficina.auth.support.CPF_VALIDO
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.withCharset
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthRoutesTest {
    private val mapper =
        jacksonObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val settings =
        TokenSettings(
            issuer = "https://auth.oficina.dev",
            audience = "oficina",
            ttl = Duration.ofHours(1),
            privateKeyPem = "irrelevante-para-as-rotas",
        )

    private val chaves =
        object : ChavesPublicas {
            override fun jwks(): String = """{"keys":[{"kty":"RSA","kid":"abc"}]}"""
        }

    private var resposta: (String?) -> ResultadoAutenticacao = {
        ResultadoAutenticacao.Autenticado(TokenEmitido("token-assinado", 3600))
    }

    private val useCase get() = AutenticarUsuarioUseCaseFake(resposta)

    private val jsonUtf8 =
        ContentType.Application.Json
            .withCharset(Charsets.UTF_8)
            .toString()

    /**
     * Resposta já materializada: o cliente de teste só existe dentro do escopo do [testApplication],
     * então o corpo é lido antes de o servidor cair.
     */
    private class Resposta(
        val status: Int,
        val corpo: String,
        val tipoDeConteudo: String?,
        private val cabecalhos: Headers,
    ) {
        fun cabecalho(nome: String): String? = cabecalhos[nome]
    }

    private fun chamar(
        metodo: String,
        caminho: String,
        corpo: String = "",
        casoDeUso: AutenticarUsuarioUseCaseFake = useCase,
        chavesPublicas: ChavesPublicas = chaves,
    ): Resposta {
        lateinit var capturada: Resposta

        testApplication {
            application { AuthRoutes(casoDeUso, chavesPublicas, settings, mapper).configurar(this) }

            val http =
                client.request(caminho) {
                    method = HttpMethod.parse(metodo)
                    if (corpo.isNotEmpty()) {
                        contentType(ContentType.Application.Json)
                        setBody(corpo)
                    }
                }

            capturada =
                Resposta(
                    status = http.status.value,
                    corpo = http.bodyAsText(),
                    tipoDeConteudo = http.contentType()?.toString(),
                    cabecalhos = http.headers,
                )
        }

        return capturada
    }

    private fun Resposta.json(): JsonNode = mapper.readTree(corpo)

    @Nested
    inner class EmissaoDeToken {
        @Test
        fun `CPF de usuario apto devolve 200 com token no formato OAuth2`() {
            val res = chamar("POST", "/auth/token", """{"cpf":"$CPF_VALIDO"}""")

            assertEquals(200, res.status)
            assertEquals(jsonUtf8, res.tipoDeConteudo)
            with(res.json()) {
                assertEquals("token-assinado", get("access_token").asText())
                assertEquals("Bearer", get("token_type").asText())
                assertEquals(3600, get("expires_in").asInt())
            }
        }

        @Test
        fun `resposta com token nunca pode ser cacheada`() {
            val res = chamar("POST", "/auth/token", """{"cpf":"$CPF_VALIDO"}""")

            assertEquals("no-store", res.cabecalho(HttpHeaders.CacheControl))
        }

        @Test
        fun `o CPF do corpo e repassado ao caso de uso`() {
            val casoDeUso = useCase

            chamar("POST", "/auth/token", """{"cpf":"$CPF_VALIDO"}""", casoDeUso)

            assertEquals(listOf<String?>(CPF_VALIDO), casoDeUso.cpfsRecebidos.toList())
        }

        @Test
        fun `campos extras no corpo sao ignorados`() {
            val casoDeUso = useCase

            val res =
                chamar(
                    "POST",
                    "/auth/token",
                    """{"cpf":"$CPF_VALIDO","senha":"123","extra":true}""",
                    casoDeUso,
                )

            assertEquals(200, res.status)
            assertEquals(listOf<String?>(CPF_VALIDO), casoDeUso.cpfsRecebidos.toList())
        }

        @Test
        fun `CPF invalido devolve 400 invalid_request`() {
            resposta = { ResultadoAutenticacao.CpfInvalido }

            val res = chamar("POST", "/auth/token", """{"cpf":"000"}""")

            assertEquals(400, res.status)
            assertEquals("invalid_request", res.json().get("error").asText())
            assertEquals("CPF inválido", res.json().get("error_description").asText())
            assertEquals("no-store", res.cabecalho(HttpHeaders.CacheControl))
        }

        @Test
        fun `usuario nao autorizado devolve 401 invalid_grant`() {
            resposta = { ResultadoAutenticacao.NaoAutorizado }

            val res = chamar("POST", "/auth/token", """{"cpf":"$CPF_VALIDO"}""")

            assertEquals(401, res.status)
            assertEquals("invalid_grant", res.json().get("error").asText())
            assertEquals("Usuário não autorizado", res.json().get("error_description").asText())
        }

        @Test
        fun `erro 401 nao revela se o CPF existe ou esta inativo`() {
            resposta = { ResultadoAutenticacao.NaoAutorizado }

            val corpo = chamar("POST", "/auth/token", """{"cpf":"$CPF_VALIDO"}""").corpo

            assertTrue(CPF_VALIDO !in corpo, "CPF vazou na resposta: $corpo")
            assertTrue("inativo" !in corpo.lowercase())
            assertTrue("nao encontrado" !in corpo.lowercase())
        }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "",
                "   ",
                "nao-e-json",
                "{",
                "{\"cpf\":}",
                "[]",
                "{\"cpf\":{\"numero\":1}}",
                "{\"cpf\":[\"12345678909\"]}",
            ],
        )
        fun `corpo ausente ou malformado e tratado como CPF invalido`(corpo: String) {
            resposta = { ResultadoAutenticacao.CpfInvalido }
            val casoDeUso = useCase

            val res = chamar("POST", "/auth/token", corpo, casoDeUso)

            assertEquals(400, res.status)
            assertEquals(listOf(null), casoDeUso.cpfsRecebidos.toList())
        }

        @Test
        fun `cpf enviado como numero e convertido para texto`() {
            val casoDeUso = useCase

            val res = chamar("POST", "/auth/token", """{"cpf":12345678909}""", casoDeUso)

            assertEquals(200, res.status)
            assertEquals(listOf<String?>("12345678909"), casoDeUso.cpfsRecebidos.toList())
        }

        @Test
        fun `corpo JSON sem o campo cpf e tratado como CPF invalido`() {
            resposta = { ResultadoAutenticacao.CpfInvalido }
            val casoDeUso = useCase

            val res = chamar("POST", "/auth/token", """{"documento":"123"}""", casoDeUso)

            assertEquals(400, res.status)
            assertEquals(listOf(null), casoDeUso.cpfsRecebidos.toList())
        }
    }

    @Nested
    inner class ChavesPublicasEndpoint {
        @Test
        fun `jwks e publicado com cache curto`() {
            val res = chamar("GET", "/.well-known/jwks.json")

            assertEquals(200, res.status)
            assertEquals(jsonUtf8, res.tipoDeConteudo)
            assertEquals("public, max-age=300", res.cabecalho(HttpHeaders.CacheControl))
            assertEquals(
                "abc",
                res
                    .json()
                    .get("keys")
                    .first()
                    .get("kid")
                    .asText(),
            )
        }

        @Test
        fun `discovery descreve os endpoints a partir do issuer`() {
            val res = chamar("GET", "/.well-known/openid-configuration")

            assertEquals(200, res.status)
            assertEquals("public, max-age=3600", res.cabecalho(HttpHeaders.CacheControl))
            with(res.json()) {
                assertEquals("https://auth.oficina.dev", get("issuer").asText())
                assertEquals(
                    "https://auth.oficina.dev/.well-known/jwks.json",
                    get("jwks_uri").asText(),
                )
                assertEquals(
                    "https://auth.oficina.dev/auth/token",
                    get("token_endpoint").asText(),
                )
                assertEquals(
                    listOf("RS256"),
                    get("id_token_signing_alg_values_supported").map { it.asText() },
                )
                assertEquals(listOf("token"), get("response_types_supported").map { it.asText() })
                assertEquals(listOf("public"), get("subject_types_supported").map { it.asText() })
            }
        }
    }

    @Nested
    inner class Roteamento {
        @ParameterizedTest
        @CsvSource(
            "POST, /auth/token, 200",
            "POST, /auth/token/, 200",
            "POST, /v1/auth/token, 200",
            "GET, /.well-known/jwks.json, 200",
            "GET, /.well-known/jwks.json/, 200",
            "GET, /.well-known/openid-configuration, 200",
            "GET, /v1/.well-known/openid-configuration, 200",
        )
        fun `rotas conhecidas respondem - prefixo do gateway e barra final toleradas`(
            metodo: String,
            caminho: String,
            esperado: Int,
        ) {
            assertEquals(esperado, chamar(metodo, caminho, """{"cpf":"$CPF_VALIDO"}""").status)
        }

        @ParameterizedTest
        @CsvSource(
            "GET, /auth/token",
            "PUT, /auth/token",
            "DELETE, /auth/token",
            "POST, /.well-known/jwks.json",
            "POST, /.well-known/openid-configuration",
            "GET, /",
            "GET, /auth",
            "GET, /auth/tokens",
            "GET, /.well-known/jwks",
        )
        fun `rota ou metodo desconhecido devolve 404 not_found`(
            metodo: String,
            caminho: String,
        ) {
            val res = chamar(metodo, caminho)

            assertEquals(404, res.status)
            assertEquals("not_found", res.json().get("error").asText())
            assertEquals("Rota inexistente", res.json().get("error_description").asText())
        }
    }

    @Nested
    inner class FalhasInesperadas {
        @Test
        fun `excecao do caso de uso vira 503 sem expor detalhes internos`() {
            resposta = { throw IllegalStateException("banco fora do ar: senha=segredo") }

            val res = chamar("POST", "/auth/token", """{"cpf":"$CPF_VALIDO"}""")

            assertEquals(503, res.status)
            assertEquals("temporarily_unavailable", res.json().get("error").asText())
            assertEquals(
                "Serviço temporariamente indisponível",
                res.json().get("error_description").asText(),
            )
            assertTrue("segredo" !in res.corpo, "detalhe interno vazou: ${res.corpo}")
            assertEquals("no-store", res.cabecalho(HttpHeaders.CacheControl))
        }

        @Test
        fun `falha ao gerar o JWKS tambem vira 503`() {
            val chavesQuebradas =
                object : ChavesPublicas {
                    override fun jwks(): String = error("chave indisponivel")
                }

            val res =
                chamar(
                    "GET",
                    "/.well-known/jwks.json",
                    chavesPublicas = chavesQuebradas,
                )

            assertEquals(503, res.status)
            assertEquals("temporarily_unavailable", res.json().get("error").asText())
        }
    }

    @Test
    fun `caminhos publicos estao definidos como constantes do contrato`() {
        assertEquals("/auth/token", AuthRoutes.TOKEN_PATH)
        assertEquals("/.well-known/jwks.json", AuthRoutes.JWKS_PATH)
        assertEquals("/.well-known/openid-configuration", AuthRoutes.DISCOVERY_PATH)
    }
}
