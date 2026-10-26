package br.com.fiap.oficina.auth.application.usecase

import br.com.fiap.oficina.auth.application.port.EmissorDeToken
import br.com.fiap.oficina.auth.domain.CPF
import br.com.fiap.oficina.auth.domain.Cliente
import br.com.fiap.oficina.auth.domain.ClienteRepository

internal class AutenticarClienteUseCaseImpl(
    private val clientes: ClienteRepository,
    private val emissor: EmissorDeToken,
) : AutenticarClienteUseCase {

    override fun executar(cpfInformado: String?): ResultadoAutenticacao {
        val cpf = cpfInformado?.let(CPF::parse)
            ?: return ResultadoAutenticacao.CpfInvalido

        val cliente = clientes.buscarPorCpf(cpf)
            ?.takeIf(Cliente::podeAutenticar)
            ?: return ResultadoAutenticacao.NaoAutorizado

        return ResultadoAutenticacao.Autenticado(emissor.emitir(cliente))
    }
}