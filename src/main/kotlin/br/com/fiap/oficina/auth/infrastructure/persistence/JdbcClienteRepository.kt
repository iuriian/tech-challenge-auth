package br.com.fiap.oficina.auth.infrastructure.persistence

import br.com.fiap.oficina.auth.domain.CPF
import br.com.fiap.oficina.auth.domain.Cliente
import br.com.fiap.oficina.auth.domain.ClienteRepository
import br.com.fiap.oficina.auth.domain.StatusCliente
import br.com.fiap.oficina.auth.domain.UsuarioId
import java.util.UUID
import javax.sql.DataSource

class JdbcClienteRepository(
    private val ds: DataSource,
) : ClienteRepository {
    override fun buscarPorCpf(cpf: CPF): Cliente? =
        ds.primeiraLinha(SQL, listOf(cpf.digitos, cpf.formatado)) { rs ->
            Cliente(
                id = UsuarioId(UUID.fromString(rs.getString("id"))),
                nome = rs.getString("nome"),
                status = StatusCliente.de(rs.getString("status")),
            )
        }

    private companion object {
        const val SQL = "SELECT id, nome, status FROM cliente WHERE cpf IN (?, ?) LIMIT 1"
    }
}
