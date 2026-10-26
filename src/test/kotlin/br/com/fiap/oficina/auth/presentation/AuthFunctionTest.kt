package br.com.fiap.oficina.auth.presentation

import com.google.cloud.functions.HttpFunction
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A classe de entrada é referenciada por nome totalmente qualificado no Dockerfile e no invoker local.
 * Renomear ou mover o pacote compila sem erro e só quebra em produção — por isso o nome é testado.
 */
class AuthFunctionTest {
    private val fqn = "br.com.fiap.oficina.auth.presentation.AuthFunction"

    @Test
    fun `a classe de entrada implementa o contrato do Functions Framework`() {
        assertTrue(HttpFunction::class.java.isAssignableFrom(AuthFunction::class.java))
        assertEquals(fqn, AuthFunction::class.java.name)
    }

    @Test
    fun `a classe de entrada tem construtor publico sem argumentos`() {
        // O invoker instancia a função por reflexão.
        assertTrue(AuthFunction::class.java.getDeclaredConstructor().canAccess(null))
    }

    @Test
    fun `o target do ENTRYPOINT da imagem aponta para a classe existente`() {
        val dockerfile = File("Dockerfile")
        check(dockerfile.exists()) { "Dockerfile nao encontrado em ${dockerfile.absolutePath}" }

        val declarado =
            Regex(""""--target",\s*"([\w.]+)"""")
                .find(dockerfile.readText())
                ?.groupValues
                ?.get(1)

        assertEquals(fqn, declarado, "--target do Dockerfile divergiu da classe")
    }

    @Test
    fun `o target do invoker local aponta para a classe existente`() {
        val build = File("build.gradle.kts")

        val declarado =
            Regex(""""--target",\s*"([\w.]+)"""")
                .find(build.readText())
                ?.groupValues
                ?.get(1)

        assertEquals(fqn, declarado, "--target da task runFunction divergiu da classe")
    }
}
