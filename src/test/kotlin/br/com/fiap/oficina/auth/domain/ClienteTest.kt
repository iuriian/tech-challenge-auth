package br.com.fiap.oficina.auth.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClienteTest {
    private val id = UsuarioId(UUID.randomUUID())

    @Test
    fun `papel do cliente e sempre CLIENTE - nao vem do banco`() {
        assertEquals(Papel.CLIENTE, Cliente(id, "Joana", StatusCliente.ATIVO).papel)
        assertEquals(Papel.CLIENTE, Cliente(id, "Joana", StatusCliente.INATIVO).papel)
    }

    @Test
    fun `cliente ativo pode autenticar`() {
        assertTrue(Cliente(id, "Joana", StatusCliente.ATIVO).podeAutenticar())
    }

    @ParameterizedTest
    @EnumSource(StatusCliente::class, names = ["ATIVO"], mode = EnumSource.Mode.EXCLUDE)
    fun `cliente em qualquer status diferente de ativo nao pode autenticar`(status: StatusCliente) {
        assertFalse(Cliente(id, "Joana", status).podeAutenticar())
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\t", "\n", "   "])
    fun `nome em branco e rejeitado na criacao`(nome: String) {
        val erro = assertFailsWith<IllegalArgumentException> { Cliente(id, nome, StatusCliente.ATIVO) }
        assertEquals("Nome do cliente não pode ser vazio", erro.message)
    }

    @Test
    fun `nome preenchido e aceito`() {
        assertEquals("Joana", Cliente(id, "Joana", StatusCliente.ATIVO).nome)
    }
}

class UsuarioIdTest {
    @Test
    fun `toString devolve o UUID cru para uso como subject do token`() {
        val uuid = UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301")

        assertEquals("3f2504e0-4f89-11d3-9a0c-0305e82c3301", UsuarioId(uuid).toString())
    }

    @Test
    fun `ids com o mesmo UUID sao iguais`() {
        val uuid = UUID.randomUUID()

        assertEquals(UsuarioId(uuid), UsuarioId(uuid))
    }
}
