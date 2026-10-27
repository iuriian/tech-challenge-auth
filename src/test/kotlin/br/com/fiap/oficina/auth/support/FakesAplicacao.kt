package br.com.fiap.oficina.auth.support

import br.com.fiap.oficina.auth.application.port.EmissorDeToken
import br.com.fiap.oficina.auth.application.port.TokenEmitido
import br.com.fiap.oficina.auth.application.usecase.AutenticarClienteUseCase
import br.com.fiap.oficina.auth.application.usecase.ResultadoAutenticacao
import br.com.fiap.oficina.auth.domain.CPF
import br.com.fiap.oficina.auth.domain.Cliente
import br.com.fiap.oficina.auth.domain.ClienteRepository
import br.com.fiap.oficina.auth.domain.Funcionario
import br.com.fiap.oficina.auth.domain.FuncionarioRepository
import br.com.fiap.oficina.auth.domain.Papel
import br.com.fiap.oficina.auth.domain.StatusCliente
import br.com.fiap.oficina.auth.domain.Usuario
import br.com.fiap.oficina.auth.domain.UsuarioId
import java.util.UUID

internal const val CPF_VALIDO = "529.982.247-25"
internal const val CPF_VALIDO_DIGITOS = "52998224725"
internal const val OUTRO_CPF_VALIDO = "111.444.777-35"

internal fun cliente(
    id: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111"),
    nome: String = "Maria Oliveira",
    status: StatusCliente = StatusCliente.ATIVO,
) = Cliente(UsuarioId(id), nome, status)

internal fun funcionario(
    id: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222"),
    nome: String = "Carlos Pereira",
    papel: Papel = Papel.ATENDENTE,
) = Funcionario(UsuarioId(id), nome, papel)

/**
 * Fake comum às duas tabelas: o que muda entre cliente e funcionário é só o tipo devolvido, então
 * duplicar o fake duplicaria também o registro de consultas que os testes de precedência usam.
 */
internal open class RepositorioFake<T : Usuario>(
    private val porCpf: Map<String, T>,
) {
    val consultas = mutableListOf<CPF>()

    protected fun buscar(cpf: CPF): T? {
        consultas += cpf
        return porCpf[cpf.digitos]
    }
}

internal class ClienteRepositoryFake(
    porCpf: Map<String, Cliente> = emptyMap(),
) : RepositorioFake<Cliente>(porCpf),
    ClienteRepository {
    override fun buscarPorCpf(cpf: CPF): Cliente? = buscar(cpf)

    companion object {
        fun com(
            cpf: String,
            cliente: Cliente,
        ) = ClienteRepositoryFake(mapOf(cpf.filter(Char::isDigit) to cliente))

        fun vazio() = ClienteRepositoryFake()
    }
}

internal class FuncionarioRepositoryFake(
    porCpf: Map<String, Funcionario> = emptyMap(),
) : RepositorioFake<Funcionario>(porCpf),
    FuncionarioRepository {
    override fun buscarPorCpf(cpf: CPF): Funcionario? = buscar(cpf)

    companion object {
        fun com(
            cpf: String,
            funcionario: Funcionario,
        ) = FuncionarioRepositoryFake(mapOf(cpf.filter(Char::isDigit) to funcionario))

        fun vazio() = FuncionarioRepositoryFake()
    }
}

internal class EmissorDeTokenFake(
    private val token: TokenEmitido = TokenEmitido("jwt-fake", 3600),
) : EmissorDeToken {
    val emissoes = mutableListOf<Usuario>()

    override fun emitir(usuario: Usuario): TokenEmitido {
        emissoes += usuario
        return token
    }
}

internal class AutenticarClienteUseCaseFake(
    private val resposta: (String?) -> ResultadoAutenticacao,
) : AutenticarClienteUseCase {
    val cpfsRecebidos = mutableListOf<String?>()

    override fun executar(cpfInformado: String?): ResultadoAutenticacao {
        cpfsRecebidos += cpfInformado
        return resposta(cpfInformado)
    }
}
