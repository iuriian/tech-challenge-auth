package br.com.fiap.oficina.auth.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertEmpty
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regras estruturais da arquitetura hexagonal. Testes de unidade continuam passando quando alguém
 * acopla o domínio ao banco; estas regras não.
 */
class ArquiteturaTest {
    private val producao = Konsist.scopeFromDirectory("src/main/kotlin")

    private val raiz = "br.com.fiap.oficina.auth"

    private fun arquivosDe(camada: String) =
        producao.files
            .filter { it.packagee?.name?.startsWith("$raiz.$camada") == true }

    private fun importesDeProjeto(camada: String) =
        arquivosDe(camada)
            .flatMap { arquivo -> arquivo.imports.map { arquivo.name to it.name } }
            .filter { (_, importe) -> importe.startsWith(raiz) }

    @Test
    fun `cada camada esperada existe - protege os demais testes de um falso positivo`() {
        listOf("domain", "application", "infrastructure", "presentation").forEach { camada ->
            check(arquivosDe(camada).isNotEmpty()) { "camada $camada nao encontrada" }
        }
    }

    @Test
    fun `o dominio nao conhece nenhuma outra camada`() {
        val violacoes =
            importesDeProjeto("domain")
                .filterNot { (_, importe) -> importe.startsWith("$raiz.domain") }

        check(violacoes.isEmpty()) { "dominio acoplado a outras camadas: $violacoes" }
    }

    @Test
    fun `a aplicacao depende apenas de si mesma e do dominio`() {
        val permitidos = listOf("$raiz.application", "$raiz.domain")
        val violacoes =
            importesDeProjeto("application")
                .filterNot { (_, importe) -> permitidos.any(importe::startsWith) }

        check(violacoes.isEmpty()) { "aplicacao acoplada a infraestrutura/apresentacao: $violacoes" }
    }

    @Test
    fun `as portas da aplicacao nao conhecem detalhes tecnicos`() {
        val violacoes =
            arquivosDe("application.port")
                .flatMap { arquivo -> arquivo.imports.map { arquivo.name to it.name } }
                .filter { (_, importe) ->
                    importe.startsWith("$raiz.infrastructure") ||
                        importe.startsWith("$raiz.presentation") ||
                        importe.startsWith("com.nimbusds") ||
                        importe.startsWith("io.ktor") ||
                        importe.startsWith("java.sql") ||
                        importe.startsWith("javax.sql")
                }

        check(violacoes.isEmpty()) { "porta vazando detalhe tecnico: $violacoes" }
    }

    @Test
    fun `o dominio nao usa frameworks`() {
        val proibidos =
            listOf(
                "com.nimbusds",
                "io.ktor",
                "com.zaxxer",
                "org.koin",
                "javax.sql",
                "java.sql",
                "com.fasterxml",
            )
        val violacoes =
            arquivosDe("domain")
                .flatMap { arquivo -> arquivo.imports.map { arquivo.name to it.name } }
                .filter { (_, importe) -> proibidos.any(importe::startsWith) }

        check(violacoes.isEmpty()) { "framework no dominio: $violacoes" }
    }

    @Test
    fun `a infraestrutura nao depende da apresentacao - excecao do modulo de DI`() {
        val violacoes =
            importesDeProjeto("infrastructure")
                .filter { (arquivo, importe) ->
                    importe.startsWith("$raiz.presentation") && arquivo != "Modules"
                }

        check(violacoes.isEmpty()) { "infraestrutura acoplada a apresentacao: $violacoes" }
    }

    @Test
    fun `implementacoes de caso de uso ficam na camada de aplicacao e seguem a nomenclatura`() {
        producao
            .classes()
            .filter { it.name.endsWith("UseCaseImpl") }
            .assertTrue { it.resideInPackage("..application.usecase..") }
    }

    /** Interfaces seladas na mesma camada modelam resultados, nao contratos de entrada. */
    @Test
    fun `contratos de caso de uso terminam com UseCase`() {
        producao
            .interfaces()
            .filter { it.resideInPackage("..application.usecase..") && !it.hasSealedModifier }
            .assertTrue { it.name.endsWith("UseCase") }
    }

    @Test
    fun `resultados de caso de uso sao modelados como hierarquia selada`() {
        producao
            .interfaces()
            .filter { it.resideInPackage("..application.usecase..") && it.hasSealedModifier }
            .assertTrue { it.name.startsWith("Resultado") }
    }

    @Test
    fun `repositorios do dominio sao interfaces - a implementacao mora na infraestrutura`() {
        producao
            .classes()
            .filter { it.resideInPackage("..domain..") }
            .assertTrue { !it.name.endsWith("Repository") }
    }

    @Test
    fun `nenhum println no codigo de producao - logs vao para o java util logging`() {
        producao
            .functions(includeNested = true, includeLocal = true)
            .filter { it.text.contains("println(") }
            .assertEmpty()
    }

    @Test
    fun `classes de dominio sao imutaveis`() {
        producao
            .classes()
            .filter { it.resideInPackage("..domain..") }
            .flatMap { it.properties() }
            .assertTrue { !it.isVar }
    }

    @Test
    fun `todo arquivo de producao declara pacote sob a raiz do projeto`() {
        producao.files.assertTrue { it.packagee?.name?.startsWith(raiz) == true }
    }
}
