package br.com.fiap.oficina.auth.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FuncionarioTest {
    private val id = UsuarioId(UUID.randomUUID())

    @ParameterizedTest
    @EnumSource(Papel::class, names = ["CLIENTE"], mode = EnumSource.Mode.EXCLUDE)
    fun `funcionario com cargo reconhecido pode autenticar`(papel: Papel) {
        assertTrue(Funcionario(id, "Carlos", papel).podeAutenticar())
    }

    @Test
    fun `papel de cliente e rejeitado na criacao - evita confusao de privilegio`() {
        val erro = assertFailsWith<IllegalArgumentException> { Funcionario(id, "Carlos", Papel.CLIENTE) }

        assertEquals("Funcionário não pode autenticar como cliente", erro.message)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\t", "\n", "   "])
    fun `nome em branco e rejeitado na criacao`(nome: String) {
        val erro = assertFailsWith<IllegalArgumentException> { Funcionario(id, nome, Papel.MECANICO) }

        assertEquals("Nome do funcionário não pode ser vazio", erro.message)
    }

    @Test
    fun `nome preenchido e aceito`() {
        assertEquals("Carlos", Funcionario(id, "Carlos", Papel.ADMIN).nome)
    }
}

class PapelTest {
    @ParameterizedTest
    @EnumSource(Papel::class, names = ["CLIENTE"], mode = EnumSource.Mode.EXCLUDE)
    fun `todo papel que nao e CLIENTE e um cargo valido de funcionario`(papel: Papel) {
        assertEquals(papel, Papel.cargoDe(papel.name))
    }

    @ParameterizedTest
    @ValueSource(strings = ["atendente", " MECANICO ", "Admin", "aDmIn"])
    fun `cargo e normalizado antes de ser traduzido`(bruto: String) {
        assertEquals(Papel.cargoDe(bruto.trim().uppercase()), Papel.cargoDe(bruto))
    }

    @ParameterizedTest
    @ValueSource(strings = ["CLIENTE", "cliente", " Cliente "])
    fun `CLIENTE nao e cargo - uma linha de funcionario nunca emite token de cliente`(bruto: String) {
        assertEquals(null, Papel.cargoDe(bruto))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "GERENTE", "ATENDENTE_SENIOR", "1", "null"])
    fun `cargo desconhecido nao vira papel`(bruto: String) {
        assertEquals(null, Papel.cargoDe(bruto))
    }

    @Test
    fun `cargo nulo nao vira papel`() {
        assertEquals(null, Papel.cargoDe(null))
    }

    @Test
    fun `o conjunto de papeis e exatamente o contrato publicado no claim roles`() {
        assertEquals(
            listOf("CLIENTE", "ATENDENTE", "MECANICO", "ADMIN"),
            Papel.entries.map { it.name },
        )
    }
}
