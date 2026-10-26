package br.com.fiap.oficina.auth.presentation

import com.google.cloud.functions.HttpFunction
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A classe de entrada é referenciada por nome totalmente qualificado no deploy e no invoker local.
 * Renomear ou mover o pacote compila sem erro e só quebra em produção — por isso o nome é testado.
 */
class AuthFunctionTest {

    private val fqn = "br.com.fiap.oficina.auth.presentation.AuthFunction"

    @Test
    fun `a classe de entrada implementa o contrato do Cloud Functions`() {
        assertTrue(HttpFunction::class.java.isAssignableFrom(AuthFunction::class.java))
        assertEquals(fqn, AuthFunction::class.java.name)
    }

    @Test
    fun `a classe de entrada tem construtor publico sem argumentos`() {
        // O runtime do Cloud Functions instancia a função por reflexão.
        assertTrue(AuthFunction::class.java.getDeclaredConstructor().canAccess(null))
    }

    @Test
    fun `o entry-point do workflow de deploy aponta para a classe existente`() {
        val workflow = File(".github/workflows/deploy.yml")
        check(workflow.exists()) { "workflow de deploy nao encontrado em ${workflow.absolutePath}" }

        val declarado = Regex("""--entry-point=([\w.]+)""")
            .find(workflow.readText())
            ?.groupValues
            ?.get(1)

        assertEquals(fqn, declarado, "--entry-point do deploy.yml divergiu da classe")
    }

    @Test
    fun `o target do invoker local aponta para a classe existente`() {
        val build = File("build.gradle.kts")

        val declarado = Regex(""""--target",\s*"([\w.]+)"""")
            .find(build.readText())
            ?.groupValues
            ?.get(1)

        assertEquals(fqn, declarado, "--target da task runFunction divergiu da classe")
    }
}
