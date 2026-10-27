package br.com.fiap.oficina.auth.application.usecase

import br.com.fiap.oficina.auth.application.port.TokenEmitido
import br.com.fiap.oficina.auth.domain.Papel
import br.com.fiap.oficina.auth.domain.StatusCliente
import br.com.fiap.oficina.auth.support.CPF_VALIDO
import br.com.fiap.oficina.auth.support.CPF_VALIDO_DIGITOS
import br.com.fiap.oficina.auth.support.ClienteRepositoryFake
import br.com.fiap.oficina.auth.support.EmissorDeTokenFake
import br.com.fiap.oficina.auth.support.FuncionarioRepositoryFake
import br.com.fiap.oficina.auth.support.OUTRO_CPF_VALIDO
import br.com.fiap.oficina.auth.support.cliente
import br.com.fiap.oficina.auth.support.funcionario
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AutenticarUsuarioUseCaseImplTest {
    private val emissor = EmissorDeTokenFake(TokenEmitido("token-assinado", 900))

    private fun useCase(
        clientes: ClienteRepositoryFake = ClienteRepositoryFake.vazio(),
        funcionarios: FuncionarioRepositoryFake = FuncionarioRepositoryFake.vazio(),
    ) = AutenticarUsuarioUseCaseImpl(clientes, funcionarios, emissor)

    @Test
    fun `cliente ativo recebe token emitido`() {
        val ativo = cliente(nome = "Joana", status = StatusCliente.ATIVO)

        val resultado = useCase(clientes = ClienteRepositoryFake.com(CPF_VALIDO, ativo)).executar(CPF_VALIDO)

        val autenticado = assertIs<ResultadoAutenticacao.Autenticado>(resultado)
        assertEquals(TokenEmitido("token-assinado", 900), autenticado.token)
        assertEquals(ativo, emissor.emissoes.single())
    }

    @ParameterizedTest
    @EnumSource(Papel::class, names = ["CLIENTE"], mode = EnumSource.Mode.EXCLUDE)
    fun `funcionario de qualquer cargo recebe token com o seu papel`(papel: Papel) {
        val cadastrado = funcionario(papel = papel)

        val resultado =
            useCase(funcionarios = FuncionarioRepositoryFake.com(CPF_VALIDO, cadastrado))
                .executar(CPF_VALIDO)

        val autenticado = assertIs<ResultadoAutenticacao.Autenticado>(resultado)
        assertEquals(TokenEmitido("token-assinado", 900), autenticado.token)
        assertEquals(cadastrado, emissor.emissoes.single())
        assertEquals(papel, emissor.emissoes.single().papel, "o papel do token vem do cargo cadastrado")
    }

    @Test
    fun `tabela de funcionario so e consultada quando o CPF nao e de cliente apto`() {
        val clientes = ClienteRepositoryFake.com(CPF_VALIDO, cliente())
        val funcionarios = FuncionarioRepositoryFake.com(CPF_VALIDO, funcionario())

        useCase(clientes, funcionarios).executar(CPF_VALIDO)

        assertEquals(1, clientes.consultas.size)
        assertTrue(funcionarios.consultas.isEmpty(), "cliente apto encerra a busca na primeira tabela")
    }

    @Test
    fun `CPF nas duas tabelas autentica como cliente - o papel de menor privilegio vence`() {
        val resultado =
            useCase(
                clientes = ClienteRepositoryFake.com(CPF_VALIDO, cliente(nome = "Maria")),
                funcionarios = FuncionarioRepositoryFake.com(CPF_VALIDO, funcionario(papel = Papel.ADMIN)),
            ).executar(CPF_VALIDO)

        val autenticado = assertIs<ResultadoAutenticacao.Autenticado>(resultado)
        assertEquals(TokenEmitido("token-assinado", 900), autenticado.token)
        assertEquals(Papel.CLIENTE, emissor.emissoes.single().papel)
    }

    @Test
    fun `cadastro de cliente inativo nao esconde o cadastro de funcionario do mesmo CPF`() {
        val atendente = funcionario(papel = Papel.ATENDENTE)

        val resultado =
            useCase(
                clientes = ClienteRepositoryFake.com(CPF_VALIDO, cliente(status = StatusCliente.INATIVO)),
                funcionarios = FuncionarioRepositoryFake.com(CPF_VALIDO, atendente),
            ).executar(CPF_VALIDO)

        val autenticado = assertIs<ResultadoAutenticacao.Autenticado>(resultado)
        assertEquals(TokenEmitido("token-assinado", 900), autenticado.token)
        assertEquals(atendente, emissor.emissoes.single())
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["", "123", "00000000000", "529.982.247-26", "abcdefghijk"])
    fun `CPF ausente ou invalido nao chega a nenhum repositorio`(cpf: String?) {
        val clientes = ClienteRepositoryFake.vazio()
        val funcionarios = FuncionarioRepositoryFake.vazio()

        val resultado = useCase(clientes, funcionarios).executar(cpf)

        assertEquals(ResultadoAutenticacao.CpfInvalido, resultado)
        assertTrue(clientes.consultas.isEmpty(), "repositorio de clientes nao deve ser consultado")
        assertTrue(funcionarios.consultas.isEmpty(), "repositorio de funcionarios nao deve ser consultado")
        assertTrue(emissor.emissoes.isEmpty(), "token nao deve ser emitido")
    }

    @Test
    fun `CPF sem cadastro em nenhuma das tabelas nao e autorizado`() {
        val clientes = ClienteRepositoryFake.vazio()
        val funcionarios = FuncionarioRepositoryFake.vazio()

        val resultado = useCase(clientes, funcionarios).executar(CPF_VALIDO)

        assertEquals(ResultadoAutenticacao.NaoAutorizado, resultado)
        assertEquals(1, clientes.consultas.size, "as duas tabelas sao consultadas antes de negar")
        assertEquals(1, funcionarios.consultas.size)
        assertTrue(emissor.emissoes.isEmpty())
    }

    @Test
    fun `cliente inativo nao e autorizado mesmo existindo no banco`() {
        val inativo = cliente(status = StatusCliente.INATIVO)

        val resultado = useCase(clientes = ClienteRepositoryFake.com(CPF_VALIDO, inativo)).executar(CPF_VALIDO)

        assertEquals(ResultadoAutenticacao.NaoAutorizado, resultado)
        assertTrue(emissor.emissoes.isEmpty(), "cliente inativo nao pode gerar token")
    }

    @Test
    fun `CPF e normalizado antes da consulta aos repositorios`() {
        val clientes = ClienteRepositoryFake.com(CPF_VALIDO, cliente())

        useCase(clientes).executar("  529 982 247-25  ")

        assertEquals(listOf(CPF_VALIDO_DIGITOS), clientes.consultas.map { it.digitos })
    }

    @Test
    fun `CPF valido de outro usuario nao autentica`() {
        val resultado =
            useCase(
                clientes = ClienteRepositoryFake.com(CPF_VALIDO, cliente()),
                funcionarios = FuncionarioRepositoryFake.com(CPF_VALIDO, funcionario()),
            ).executar(OUTRO_CPF_VALIDO)

        assertEquals(ResultadoAutenticacao.NaoAutorizado, resultado)
    }
}
