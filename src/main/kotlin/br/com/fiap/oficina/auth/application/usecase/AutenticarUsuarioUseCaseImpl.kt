package br.com.fiap.oficina.auth.application.usecase

import br.com.fiap.oficina.auth.application.port.EmissorDeToken
import br.com.fiap.oficina.auth.domain.CPF
import br.com.fiap.oficina.auth.domain.ClienteRepository
import br.com.fiap.oficina.auth.domain.FuncionarioRepository
import br.com.fiap.oficina.auth.domain.Usuario

internal class AutenticarUsuarioUseCaseImpl(
    private val clientes: ClienteRepository,
    private val funcionarios: FuncionarioRepository,
    private val emissor: EmissorDeToken,
) : AutenticarUsuarioUseCase {
    /**
     * Os dois retornos antecipados são guard clauses: CPF inválido e usuário não autorizado saem
     * na hora. Aninhar para ter um único retorno deixaria o fluxo principal menos legível, não mais.
     */
    @Suppress("ReturnCount")
    override fun executar(cpfInformado: String?): ResultadoAutenticacao {
        val cpf =
            cpfInformado?.let(CPF::parse)
                ?: return ResultadoAutenticacao.CpfInvalido

        val usuario =
            autorizado(cpf)
                ?: return ResultadoAutenticacao.NaoAutorizado

        return ResultadoAutenticacao.Autenticado(emissor.emitir(usuario))
    }

    /**
     * A tabela cliente é consultada primeiro por duas razões que apontam para o mesmo lado: é o
     * caso comum, e um CPF presente nas duas tabelas acaba recebendo o papel de menor privilégio.
     *
     * O `takeIf` é aplicado por origem, não no fim: um cadastro de cliente inativo não deve
     * esconder o cadastro de funcionário do mesmo CPF. A tabela funcionario só é lida quando a
     * primeira consulta não produz ninguém apto.
     */
    private fun autorizado(cpf: CPF): Usuario? =
        clientes.buscarPorCpf(cpf)?.takeIf(Usuario::podeAutenticar)
            ?: funcionarios.buscarPorCpf(cpf)?.takeIf(Usuario::podeAutenticar)
}
