package br.com.fiap.oficina.auth.infrastructure.persistence

import br.com.fiap.oficina.auth.domain.CPF
import br.com.fiap.oficina.auth.domain.Cliente
import br.com.fiap.oficina.auth.domain.ClienteId
import br.com.fiap.oficina.auth.domain.ClienteRepository
import br.com.fiap.oficina.auth.domain.StatusCliente
import java.util.UUID
import javax.sql.DataSource

class JdbcClienteRepository(private val ds: DataSource) : ClienteRepository {
    override fun buscarPorCpf(cpf: CPF): Cliente? =
        ds.connection.use { conn ->
            conn.prepareStatement(SQL).use { ps ->
                ps.setString(1, cpf.digitos)
                ps.setString(2, cpf.formatado)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) null
                    else Cliente(
                        id = ClienteId(UUID.fromString(rs.getString("id"))),
                        nome = rs.getString("nome"),
                        status = StatusCliente.de(rs.getString("status")),
                    )
                }
            }
        }

    private companion object {
        const val SQL = "SELECT id, nome, status FROM cliente WHERE cpf IN (?, ?) LIMIT 1"
    }
}