package br.com.fiap.oficina.auth.presentation

import org.junit.jupiter.api.Test
import java.io.File
import java.lang.reflect.Modifier
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * O entrypoint da imagem é resolvido por nome, não pelo compilador: renomear o arquivo, mover o
 * pacote ou trocar o `mainClass` compila sem erro e só quebra ao subir o container.
 */
class ServidorTest {
    private val fqnEsperado = "br.com.fiap.oficina.auth.presentation.ServidorKt"

    @Test
    fun `sem PORT no ambiente o servidor escuta na 8080 - contrato do Cloud Run`() {
        assertEquals(8080, PORTA_PADRAO)
        assertEquals(PORTA_PADRAO, portaDeEscuta { null })
    }

    @Test
    fun `PORT do ambiente vence o padrao`() {
        assertEquals(9090, portaDeEscuta { if (it == "PORT") "9090" else null })
    }

    @Test
    fun `PORT invalido cai no padrao em vez de derrubar a instancia na partida`() {
        assertEquals(PORTA_PADRAO, portaDeEscuta { "oito mil e oitenta" })
    }

    @Test
    fun `o mainClass declarado no build aponta para o main deste pacote`() {
        val declarado =
            Regex("""mainClass\.set\("([\w.]+)"\)""")
                .find(File("build.gradle.kts").readText())
                ?.groupValues
                ?.get(1)

        assertEquals(fqnEsperado, declarado, "mainClass do build divergiu do Servidor.kt")

        val main =
            Class
                .forName(declarado)
                .getDeclaredMethod("main", Array<String>::class.java)

        assertTrue(Modifier.isStatic(main.modifiers), "a JVM so chama main estatico")
    }

    @Test
    fun `a imagem executa o fat JAR pelo manifesto`() {
        val dockerfile = File("Dockerfile")
        check(dockerfile.exists()) { "Dockerfile nao encontrado em ${dockerfile.absolutePath}" }

        val texto = dockerfile.readText()

        assertTrue(
            """ENTRYPOINT ["java", "-jar", "app.jar"]""" in texto,
            "o ENTRYPOINT precisa executar o fat JAR, que carrega o Main-Class do manifesto",
        )
        assertTrue(
            "--target" !in texto,
            "sobrou configuração do Functions Framework no Dockerfile",
        )
    }
}
