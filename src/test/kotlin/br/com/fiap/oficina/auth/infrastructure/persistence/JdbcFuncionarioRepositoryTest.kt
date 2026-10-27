package br.com.fiap.oficina.auth.infrastructure.persistence

import br.com.fiap.oficina.auth.domain.CPF
import br.com.fiap.oficina.auth.domain.Papel
import br.com.fiap.oficina.auth.domain.UsuarioId
import br.com.fiap.oficina.auth.support.CPF_VALIDO_DIGITOS
import br.com.fiap.oficina.auth.support.FakeDataSource
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.sql.SQLException
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JdbcFuncionarioRepositoryTest {
    private val cpf = requireNotNull(CPF.parse(CPF_VALIDO_DIGITOS))

    private fun linha(
        id: String = "3f5f33b0-4f1f-4a76-9ef8-1dc8b8d1a1b3",
        nome: String = "Carlos Pereira",
        cargo: String? = "ATENDENTE",
    ) = mapOf("id" to id, "nome" to nome, "cargo" to cargo)

    @Test
    fun `mapeia a linha encontrada para o funcionario`() {
        val ds = FakeDataSource(listOf(linha()))

        val funcionario = JdbcFuncionarioRepository(ds).buscarPorCpf(cpf)

        assertEquals(
            UsuarioId(UUID.fromString("3f5f33b0-4f1f-4a76-9ef8-1dc8b8d1a1b3")),
            funcionario?.id,
        )
        assertEquals("Carlos Pereira", funcionario?.nome)
        assertEquals(Papel.ATENDENTE, funcionario?.papel)
    }

    @Test
    fun `devolve null quando nao ha linha`() {
        assertNull(JdbcFuncionarioRepository(FakeDataSource()).buscarPorCpf(cpf))
    }

    @ParameterizedTest
    @CsvSource(
        "ATENDENTE, ATENDENTE",
        "atendente, ATENDENTE",
        "MECANICO, MECANICO",
        "ADMIN, ADMIN",
    )
    fun `cargo do banco e traduzido para o papel pelo dominio`(
        bruto: String,
        esperado: Papel,
    ) {
        val ds = FakeDataSource(listOf(linha(cargo = bruto)))

        assertEquals(esperado, JdbcFuncionarioRepository(ds).buscarPorCpf(cpf)?.papel)
    }

    /**
     * `cargo` pertence a uma tabela que este serviço só lê: um valor novo cadastrado lá precisa
     * negar aquele login, não derrubar a autenticação de todo mundo com exceção.
     */
    @ParameterizedTest
    @ValueSource(strings = ["GERENTE", "", " ", "CLIENTE", "1"])
    fun `cargo que o dominio nao reconhece nao autentica e nao lanca erro`(bruto: String) {
        val ds = FakeDataSource(listOf(linha(cargo = bruto)))

        assertNull(JdbcFuncionarioRepository(ds).buscarPorCpf(cpf))
    }

    @Test
    fun `cargo nulo no banco nao autentica`() {
        val ds = FakeDataSource(listOf(linha(cargo = null)))

        assertNull(JdbcFuncionarioRepository(ds).buscarPorCpf(cpf))
    }

    @Test
    fun `consulta o CPF sem mascara e com mascara`() {
        val ds = FakeDataSource(listOf(linha()))

        JdbcFuncionarioRepository(ds).buscarPorCpf(cpf)

        val statement = requireNotNull(ds.conexao.statement)
        assertEquals(
            mapOf<Int, String?>(1 to "52998224725", 2 to "529.982.247-25"),
            statement.parametros.toMap(),
        )
    }

    @Test
    fun `usa consulta parametrizada e limitada a uma linha`() {
        val ds = FakeDataSource(listOf(linha()))

        JdbcFuncionarioRepository(ds).buscarPorCpf(cpf)

        val sql = requireNotNull(ds.conexao.statement).sql
        assertEquals("SELECT id, nome, cargo FROM funcionario WHERE cpf IN (?, ?) LIMIT 1", sql)
        assertTrue("?" in sql, "o CPF nunca deve ser concatenado no SQL")
    }

    @Test
    fun `a consulta nao le colunas alem das concedidas ao papel auth_ro`() {
        val ds = FakeDataSource(listOf(linha()))

        JdbcFuncionarioRepository(ds).buscarPorCpf(cpf)

        val sql = requireNotNull(ds.conexao.statement).sql
        assertTrue("*" !in sql, "SELECT * pediria colunas sem GRANT")
    }

    @Test
    fun `conexao, statement e result set sao fechados apos a consulta`() {
        val ds = FakeDataSource(listOf(linha()))

        JdbcFuncionarioRepository(ds).buscarPorCpf(cpf)

        assertTrue(ds.conexao.fechada, "conexao devolvida ao pool")
        assertTrue(requireNotNull(ds.conexao.statement).fechado, "statement fechado")
        assertTrue(ds.resultado.fechado, "result set fechado")
    }

    @Test
    fun `conexao e fechada mesmo quando a consulta falha`() {
        val ds = FakeDataSource(falha = SQLException("conexao perdida"))

        assertFailsWith<SQLException> { JdbcFuncionarioRepository(ds).buscarPorCpf(cpf) }

        assertTrue(ds.conexao.fechada, "conexao precisa voltar ao pool mesmo em erro")
    }

    @Test
    fun `uma consulta usa exatamente uma conexao`() {
        val ds = FakeDataSource(listOf(linha()))

        JdbcFuncionarioRepository(ds).buscarPorCpf(cpf)

        assertEquals(1, ds.conexoesSolicitadas)
    }

    @Test
    fun `id invalido no banco propaga erro em vez de devolver funcionario corrompido`() {
        val ds = FakeDataSource(listOf(linha(id = "nao-e-uuid")))

        assertFailsWith<IllegalArgumentException> { JdbcFuncionarioRepository(ds).buscarPorCpf(cpf) }
    }

    @Test
    fun `nome vazio no banco propaga erro de invariante do dominio`() {
        val ds = FakeDataSource(listOf(linha(nome = " ")))

        assertFailsWith<IllegalArgumentException> { JdbcFuncionarioRepository(ds).buscarPorCpf(cpf) }
    }
}
