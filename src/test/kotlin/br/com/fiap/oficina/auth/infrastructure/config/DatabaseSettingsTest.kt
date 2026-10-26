package br.com.fiap.oficina.auth.infrastructure.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DatabaseSettingsTest {

    private val completo = mapOf(
        "DB_URL" to "jdbc:postgresql://10.0.0.5:5432/oficina",
        "DB_USER" to "auth_ro",
        "DB_PASSWORD" to "s3nh4-super-secreta",
    )

    private fun env(valores: Map<String, String?>): (String) -> String? = valores::get

    @Test
    fun `le todas as variaveis de ambiente`() {
        val settings = DatabaseSettings.fromEnv(env(completo))

        assertEquals("jdbc:postgresql://10.0.0.5:5432/oficina", settings.url)
        assertEquals("auth_ro", settings.user)
        assertEquals("s3nh4-super-secreta", settings.password)
    }

    @ParameterizedTest
    @ValueSource(strings = ["DB_URL", "DB_USER", "DB_PASSWORD"])
    fun `toda variavel e obrigatoria`(nome: String) {
        val erro = assertFailsWith<IllegalStateException> {
            DatabaseSettings.fromEnv(env(completo - nome))
        }

        assertEquals("Variável de ambiente $nome ausente", erro.message)
    }

    @ParameterizedTest
    @ValueSource(strings = ["DB_URL", "DB_USER", "DB_PASSWORD"])
    fun `variavel em branco e tratada como ausente`(nome: String) {
        assertFailsWith<IllegalStateException> {
            DatabaseSettings.fromEnv(env(completo + (nome to "  ")))
        }
    }

    @Test
    fun `toString nao expoe a senha`() {
        val texto = DatabaseSettings.fromEnv(env(completo)).toString()

        assertTrue("s3nh4-super-secreta" !in texto, "senha vazou em: $texto")
        assertTrue("password" !in texto, "senha vazou em: $texto")
        assertEquals(
            "DatabaseSettings(url=jdbc:postgresql://10.0.0.5:5432/oficina, user=auth_ro)",
            texto,
        )
    }
}
