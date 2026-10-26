package br.com.fiap.oficina.auth.presentation.http

import br.com.fiap.oficina.auth.application.port.ChavesPublicas
import br.com.fiap.oficina.auth.application.port.TokenEmitido
import br.com.fiap.oficina.auth.application.usecase.ResultadoAutenticacao
import br.com.fiap.oficina.auth.infrastructure.config.TokenSettings
import br.com.fiap.oficina.auth.support.AutenticarClienteUseCaseFake
import br.com.fiap.oficina.auth.support.CPF_VALIDO
import br.com.fiap.oficina.auth.support.FakeHttpRequest
import br.com.fiap.oficina.auth.support.FakeHttpResponse
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthHttpHandlerTest {

    private val mapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val settings = TokenSettings(
        issuer = "https://auth.oficina.dev",
        audience = "oficina",
        ttl = Duration.ofHours(1),
        privateKeyPem = "irrelevante-para-o-handler",
    )

    private val chaves = object : ChavesPublicas {
        override fun jwks(): String = """{"keys":[{"kty":"RSA","kid":"abc"}]}"""
    }

    private var resposta: (String?) -> ResultadoAutenticacao = {
        ResultadoAutenticacao.Autenticado(TokenEmitido("token-assinado", 3600))
    }

    private val useCase get() = AutenticarClienteUseCaseFake(resposta)

    private fun chamar(
        metodo: String,
        caminho: String,
        corpo: String = "",
        casoDeUso: AutenticarClienteUseCaseFake = useCase,
    ): FakeHttpResponse {
        val res = FakeHttpResponse()
        AuthHttpHandler(casoDeUso, chaves, settings, mapper)
            .handle(FakeHttpRequest(metodo, caminho, corpo), res)
        return res
    }

    private fun FakeHttpResponse.json(): JsonNode = mapper.readTree(corpo)

    @Nested
    inner class EmissaoDeToken {

        @Test
        fun `CPF de cliente ativo devolve 200 com token no formato OAuth2`() {
            val res = chamar("POST", "/auth/token", """{"cpf":"$CPF_VALIDO"}""")

            assertEquals(200, res.status)
            assertEquals("application/json; charset=utf-8", res.tipoDeConteudo)
            with(res.json()) {
                assertEquals("token-assinado", get("access_token").asText())
                assertEquals("Bearer", get("token_type").asText())
                assertEquals(3600, get("expires_in").asInt())
            }
        }

        @Test
        fun `resposta com token nunca pode ser cacheada`() {
            val res = chamar("POST", "/auth/token", """{"cpf":"$CPF_VALIDO"}""")

            assertEquals("no-store", res.cabecalho("Cache-Control"))
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

            val res = chamar(
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
            assertEquals("no-store", res.cabecalho("Cache-Control"))
        }

        @Test
        fun `cliente nao autorizado devolve 401 invalid_grant`() {
            resposta = { ResultadoAutenticacao.NaoAutorizado }

            val res = chamar("POST", "/auth/token", """{"cpf":"$CPF_VALIDO"}""")

            assertEquals(401, res.status)
            assertEquals("invalid_grant", res.json().get("error").asText())
            assertEquals("Cliente não autorizado", res.json().get("error_description").asText())
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
            ]
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
            assertEquals("application/json; charset=utf-8", res.tipoDeConteudo)
            assertEquals("public, max-age=300", res.cabecalho("Cache-Control"))
            assertEquals("abc", res.json().get("keys").first().get("kid").asText())
        }

        @Test
        fun `discovery descreve os endpoints a partir do issuer`() {
            val res = chamar("GET", "/.well-known/openid-configuration")

            assertEquals(200, res.status)
            assertEquals("public, max-age=3600", res.cabecalho("Cache-Control"))
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
        fun `rota ou metodo desconhecido devolve 404 not_found`(metodo: String, caminho: String) {
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
            assertEquals("no-store", res.cabecalho("Cache-Control"))
        }

        @Test
        fun `falha ao gerar o JWKS tambem vira 503`() {
            val chavesQuebradas = object : ChavesPublicas {
                override fun jwks(): String = error("chave indisponivel")
            }
            val res = FakeHttpResponse()

            AuthHttpHandler(useCase, chavesQuebradas, settings, mapper)
                .handle(FakeHttpRequest("GET", "/.well-known/jwks.json"), res)

            assertEquals(503, res.status)
            assertEquals("temporarily_unavailable", res.json().get("error").asText())
        }
    }

    @Test
    fun `caminhos publicos estao definidos como constantes do contrato`() {
        assertEquals("/auth/token", AuthHttpHandler.TOKEN_PATH)
        assertEquals("/.well-known/jwks.json", AuthHttpHandler.JWKS_PATH)
        assertEquals("/.well-known/openid-configuration", AuthHttpHandler.DISCOVERY_PATH)
    }
}
