package br.com.fiap.oficina.auth.infrastructure.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TokenSettingsTest {
    private val completo =
        mapOf(
            "ISSUER" to "https://auth.oficina.dev",
            "AUDIENCE" to "oficina-app",
            "TOKEN_TTL_SECONDS" to "600",
            "JWT_PRIVATE_KEY" to "-----BEGIN PRIVATE KEY-----\nabc\n-----END PRIVATE KEY-----",
        )

    private fun env(valores: Map<String, String?>): (String) -> String? = valores::get

    @Test
    fun `le todas as variaveis de ambiente`() {
        val settings = TokenSettings.fromEnv(env(completo))

        assertEquals("https://auth.oficina.dev", settings.issuer)
        assertEquals("oficina-app", settings.audience)
        assertEquals(Duration.ofSeconds(600), settings.ttl)
        assertEquals(completo["JWT_PRIVATE_KEY"], settings.privateKeyPem)
    }

    @Test
    fun `remove barras finais do issuer para nao gerar URLs duplicadas`() {
        val settings = TokenSettings.fromEnv(env(completo + ("ISSUER" to "https://auth.oficina.dev///")))

        assertEquals("https://auth.oficina.dev", settings.issuer)
    }

    @Test
    fun `audience tem valor padrao quando ausente`() {
        val settings = TokenSettings.fromEnv(env(completo - "AUDIENCE"))

        assertEquals("oficina", settings.audience)
    }

    @Test
    fun `ttl tem padrao de uma hora quando ausente`() {
        val settings = TokenSettings.fromEnv(env(completo - "TOKEN_TTL_SECONDS"))

        assertEquals(Duration.ofHours(1), settings.ttl)
    }

    @ParameterizedTest
    @ValueSource(strings = ["ISSUER", "JWT_PRIVATE_KEY"])
    fun `variavel obrigatoria ausente falha com mensagem explicita`(nome: String) {
        val erro =
            assertFailsWith<IllegalStateException> {
                TokenSettings.fromEnv(env(completo - nome))
            }

        assertEquals("Variável de ambiente $nome ausente", erro.message)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   "])
    fun `variavel obrigatoria em branco e tratada como ausente`(valor: String) {
        assertFailsWith<IllegalStateException> {
            TokenSettings.fromEnv(env(completo + ("ISSUER" to valor)))
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["auth.oficina.dev", "ftp://auth.oficina.dev", "oficina"])
    fun `issuer precisa ser uma URL`(issuer: String) {
        val erro =
            assertFailsWith<IllegalArgumentException> {
                TokenSettings.fromEnv(env(completo + ("ISSUER" to issuer)))
            }

        assertEquals("ISSUER deve ser uma URL", erro.message)
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "-1", "-3600"])
    fun `ttl precisa ser positivo`(ttl: String) {
        val erro =
            assertFailsWith<IllegalArgumentException> {
                TokenSettings.fromEnv(env(completo + ("TOKEN_TTL_SECONDS" to ttl)))
            }

        assertEquals("TOKEN_TTL_SECONDS deve ser positivo", erro.message)
    }

    @Test
    fun `ttl nao numerico falha`() {
        assertFailsWith<NumberFormatException> {
            TokenSettings.fromEnv(env(completo + ("TOKEN_TTL_SECONDS" to "uma-hora")))
        }
    }

    @Test
    fun `toString nao expoe a chave privada`() {
        val texto = TokenSettings.fromEnv(env(completo)).toString()

        assertTrue("BEGIN PRIVATE KEY" !in texto, "chave privada vazou em: $texto")
        assertTrue("abc" !in texto, "chave privada vazou em: $texto")
        assertTrue("issuer=https://auth.oficina.dev" in texto)
    }
}
