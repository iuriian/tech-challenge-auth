package br.com.fiap.oficina.auth.application.usecase

import br.com.fiap.oficina.auth.application.port.TokenEmitido
import br.com.fiap.oficina.auth.domain.StatusCliente
import br.com.fiap.oficina.auth.support.CPF_VALIDO
import br.com.fiap.oficina.auth.support.CPF_VALIDO_DIGITOS
import br.com.fiap.oficina.auth.support.ClienteRepositoryFake
import br.com.fiap.oficina.auth.support.EmissorDeTokenFake
import br.com.fiap.oficina.auth.support.OUTRO_CPF_VALIDO
import br.com.fiap.oficina.auth.support.cliente
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AutenticarClienteUseCaseImplTest {
    private val emissor = EmissorDeTokenFake(TokenEmitido("token-assinado", 900))

    private fun useCase(clientes: ClienteRepositoryFake) = AutenticarClienteUseCaseImpl(clientes, emissor)

    @Test
    fun `cliente ativo recebe token emitido`() {
        val ativo = cliente(nome = "Joana", status = StatusCliente.ATIVO)

        val resultado = useCase(ClienteRepositoryFake.com(CPF_VALIDO, ativo)).executar(CPF_VALIDO)

        val autenticado = assertIs<ResultadoAutenticacao.Autenticado>(resultado)
        assertEquals(TokenEmitido("token-assinado", 900), autenticado.token)
        assertEquals(listOf(ativo), emissor.emissoes)
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["", "123", "00000000000", "529.982.247-26", "abcdefghijk"])
    fun `CPF ausente ou invalido nao chega ao repositorio`(cpf: String?) {
        val clientes = ClienteRepositoryFake.vazio()

        val resultado = useCase(clientes).executar(cpf)

        assertEquals(ResultadoAutenticacao.CpfInvalido, resultado)
        assertTrue(clientes.consultas.isEmpty(), "repositorio nao deve ser consultado")
        assertTrue(emissor.emissoes.isEmpty(), "token nao deve ser emitido")
    }

    @Test
    fun `cliente inexistente nao e autorizado`() {
        val resultado = useCase(ClienteRepositoryFake.vazio()).executar(CPF_VALIDO)

        assertEquals(ResultadoAutenticacao.NaoAutorizado, resultado)
        assertTrue(emissor.emissoes.isEmpty())
    }

    @Test
    fun `cliente inativo nao e autorizado mesmo existindo no banco`() {
        val inativo = cliente(status = StatusCliente.INATIVO)

        val resultado = useCase(ClienteRepositoryFake.com(CPF_VALIDO, inativo)).executar(CPF_VALIDO)

        assertEquals(ResultadoAutenticacao.NaoAutorizado, resultado)
        assertTrue(emissor.emissoes.isEmpty(), "cliente inativo nao pode gerar token")
    }

    @Test
    fun `CPF e normalizado antes da consulta ao repositorio`() {
        val clientes = ClienteRepositoryFake.com(CPF_VALIDO, cliente())

        useCase(clientes).executar("  529 982 247-25  ")

        assertEquals(listOf(CPF_VALIDO_DIGITOS), clientes.consultas.map { it.digitos })
    }

    @Test
    fun `CPF valido de outro cliente nao autentica`() {
        val clientes = ClienteRepositoryFake.com(CPF_VALIDO, cliente())

        val resultado = useCase(clientes).executar(OUTRO_CPF_VALIDO)

        assertEquals(ResultadoAutenticacao.NaoAutorizado, resultado)
    }
}
