package br.com.fiap.oficina.auth.infrastructure.persistence

import br.com.fiap.oficina.auth.domain.CPF
import br.com.fiap.oficina.auth.domain.Funcionario
import br.com.fiap.oficina.auth.domain.FuncionarioRepository
import br.com.fiap.oficina.auth.domain.Papel
import br.com.fiap.oficina.auth.domain.UsuarioId
import java.util.UUID
import javax.sql.DataSource

class JdbcFuncionarioRepository(
    private val ds: DataSource,
) : FuncionarioRepository {
    /**
     * Cargo que o domínio não reconhece resulta em `null`, não em erro: `cargo` é coluna de uma
     * tabela que este serviço apenas lê, e um cargo novo cadastrado lá precisa negar aquele login
     * sem derrubar a autenticação de todo mundo.
     */
    override fun buscarPorCpf(cpf: CPF): Funcionario? =
        ds.primeiraLinha(SQL, listOf(cpf.digitos, cpf.formatado)) { rs ->
            Papel.cargoDe(rs.getString("cargo"))?.let { papel ->
                Funcionario(
                    id = UsuarioId(UUID.fromString(rs.getString("id"))),
                    nome = rs.getString("nome"),
                    papel = papel,
                )
            }
        }

    private companion object {
        const val SQL = "SELECT id, nome, cargo FROM funcionario WHERE cpf IN (?, ?) LIMIT 1"
    }
}
