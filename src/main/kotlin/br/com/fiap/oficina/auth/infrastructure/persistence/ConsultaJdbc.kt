package br.com.fiap.oficina.auth.infrastructure.persistence

import java.sql.ResultSet
import javax.sql.DataSource

/**
 * Executa uma consulta parametrizada e mapeia no máximo uma linha, devolvendo `null` quando não há
 * resultado ou quando o mapeamento recusa a linha.
 *
 * O aninhamento dos três `use` — conexão, statement e result set — é o contrato de fechamento de
 * recursos do JDBC, não complexidade acidental. Fica concentrado aqui para que cada repositório
 * declare só o seu SQL e o seu mapeamento, e para que a garantia de devolver a conexão ao pool
 * exista em um lugar em vez de um por tabela.
 */
@Suppress("NestedBlockDepth")
internal fun <T> DataSource.primeiraLinha(
    sql: String,
    parametros: List<String>,
    mapear: (ResultSet) -> T?,
): T? =
    connection.use { conn ->
        conn.prepareStatement(sql).use { ps ->
            parametros.forEachIndexed { indice, valor -> ps.setString(indice + 1, valor) }
            ps.executeQuery().use { rs -> if (rs.next()) mapear(rs) else null }
        }
    }
