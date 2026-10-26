package br.com.fiap.oficina.auth.infrastructure.persistence

import br.com.fiap.oficina.auth.domain.CPF
import br.com.fiap.oficina.auth.domain.ClienteId
import br.com.fiap.oficina.auth.domain.StatusCliente
import br.com.fiap.oficina.auth.support.CPF_VALIDO_DIGITOS
import br.com.fiap.oficina.auth.support.FakeDataSource
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.sql.SQLException
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JdbcClienteRepositoryTest {

    private val cpf = requireNotNull(CPF.parse(CPF_VALIDO_DIGITOS))

    private fun linha(
        id: String = "b3f1c2d4-0000-4000-8000-000000000009",
        nome: String = "Joana Souza",
        status: String? = "ATIVO",
    ) = mapOf("id" to id, "nome" to nome, "status" to status)

    @Test
    fun `mapeia a linha encontrada para o cliente`() {
        val ds = FakeDataSource(listOf(linha()))

        val cliente = JdbcClienteRepository(ds).buscarPorCpf(cpf)

        assertEquals(
            ClienteId(UUID.fromString("b3f1c2d4-0000-4000-8000-000000000009")),
            cliente?.id,
        )
        assertEquals("Joana Souza", cliente?.nome)
        assertEquals(StatusCliente.ATIVO, cliente?.status)
    }

    @Test
    fun `devolve null quando nao ha linha`() {
        assertNull(JdbcClienteRepository(FakeDataSource()).buscarPorCpf(cpf))
    }

    @Test
    fun `consulta o CPF sem mascara e com mascara - cobre as duas gravacoes do legado`() {
        val ds = FakeDataSource(listOf(linha()))

        JdbcClienteRepository(ds).buscarPorCpf(cpf)

        val statement = requireNotNull(ds.conexao.statement)
        assertEquals(
            mapOf<Int, String?>(1 to "52998224725", 2 to "529.982.247-25"),
            statement.parametros.toMap(),
        )
    }

    @Test
    fun `usa consulta parametrizada e limitada a uma linha`() {
        val ds = FakeDataSource(listOf(linha()))

        JdbcClienteRepository(ds).buscarPorCpf(cpf)

        val sql = requireNotNull(ds.conexao.statement).sql
        assertEquals("SELECT id, nome, status FROM cliente WHERE cpf IN (?, ?) LIMIT 1", sql)
        assertTrue("?" in sql, "o CPF nunca deve ser concatenado no SQL")
    }

    @ParameterizedTest
    @CsvSource(
        "ATIVO, ATIVO",
        "ativo, ATIVO",
        "INATIVO, INATIVO",
        "BLOQUEADO, INATIVO",
        "'', INATIVO",
    )
    fun `status do banco e traduzido pelo dominio`(bruto: String, esperado: StatusCliente) {
        val ds = FakeDataSource(listOf(linha(status = bruto)))

        assertEquals(esperado, JdbcClienteRepository(ds).buscarPorCpf(cpf)?.status)
    }

    @Test
    fun `status nulo no banco resulta em INATIVO`() {
        val ds = FakeDataSource(listOf(linha(status = null)))

        assertEquals(StatusCliente.INATIVO, JdbcClienteRepository(ds).buscarPorCpf(cpf)?.status)
    }

    @Test
    fun `conexao, statement e result set sao fechados apos a consulta`() {
        val ds = FakeDataSource(listOf(linha()))

        JdbcClienteRepository(ds).buscarPorCpf(cpf)

        assertTrue(ds.conexao.fechada, "conexao devolvida ao pool")
        assertTrue(requireNotNull(ds.conexao.statement).fechado, "statement fechado")
        assertTrue(ds.resultado.fechado, "result set fechado")
    }

    @Test
    fun `conexao e fechada mesmo quando a consulta falha`() {
        val ds = FakeDataSource(falha = SQLException("conexao perdida"))

        assertFailsWith<SQLException> { JdbcClienteRepository(ds).buscarPorCpf(cpf) }

        assertTrue(ds.conexao.fechada, "conexao precisa voltar ao pool mesmo em erro")
    }

    @Test
    fun `uma consulta usa exatamente uma conexao`() {
        val ds = FakeDataSource(listOf(linha()))

        JdbcClienteRepository(ds).buscarPorCpf(cpf)

        assertEquals(1, ds.conexoesSolicitadas)
    }

    @Test
    fun `id invalido no banco propaga erro em vez de devolver cliente corrompido`() {
        val ds = FakeDataSource(listOf(linha(id = "nao-e-uuid")))

        assertFailsWith<IllegalArgumentException> { JdbcClienteRepository(ds).buscarPorCpf(cpf) }
    }

    @Test
    fun `nome vazio no banco propaga erro de invariante do dominio`() {
        val ds = FakeDataSource(listOf(linha(nome = " ")))

        assertFailsWith<IllegalArgumentException> { JdbcClienteRepository(ds).buscarPorCpf(cpf) }
    }
}
