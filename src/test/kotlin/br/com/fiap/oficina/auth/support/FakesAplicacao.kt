package br.com.fiap.oficina.auth.support

import br.com.fiap.oficina.auth.application.port.EmissorDeToken
import br.com.fiap.oficina.auth.application.port.TokenEmitido
import br.com.fiap.oficina.auth.application.usecase.AutenticarClienteUseCase
import br.com.fiap.oficina.auth.application.usecase.ResultadoAutenticacao
import br.com.fiap.oficina.auth.domain.CPF
import br.com.fiap.oficina.auth.domain.Cliente
import br.com.fiap.oficina.auth.domain.ClienteId
import br.com.fiap.oficina.auth.domain.ClienteRepository
import br.com.fiap.oficina.auth.domain.StatusCliente
import java.util.UUID

internal const val CPF_VALIDO = "529.982.247-25"
internal const val CPF_VALIDO_DIGITOS = "52998224725"
internal const val OUTRO_CPF_VALIDO = "111.444.777-35"

internal fun cliente(
    id: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111"),
    nome: String = "Maria Oliveira",
    status: StatusCliente = StatusCliente.ATIVO,
) = Cliente(ClienteId(id), nome, status)

internal class ClienteRepositoryFake(
    private val porCpf: Map<String, Cliente> = emptyMap(),
) : ClienteRepository {
    val consultas = mutableListOf<CPF>()

    override fun buscarPorCpf(cpf: CPF): Cliente? {
        consultas += cpf
        return porCpf[cpf.digitos]
    }

    companion object {
        fun com(
            cpf: String,
            cliente: Cliente,
        ) = ClienteRepositoryFake(mapOf(cpf.filter(Char::isDigit) to cliente))

        fun vazio() = ClienteRepositoryFake()
    }
}

internal class EmissorDeTokenFake(
    private val token: TokenEmitido = TokenEmitido("jwt-fake", 3600),
) : EmissorDeToken {
    val emissoes = mutableListOf<Cliente>()

    override fun emitir(cliente: Cliente): TokenEmitido {
        emissoes += cliente
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
