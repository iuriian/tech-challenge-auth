package br.com.fiap.oficina.auth.support

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import javax.sql.DataSource

internal class FakeResultSet(private val linhas: List<Map<String, String?>>) :
    ResultSet by naoImplementado() {

    private var indice = -1
    var fechado = false
        private set

    override fun next(): Boolean = ++indice < linhas.size

    override fun getString(columnLabel: String): String? {
        val linha = linhas[indice]
        require(columnLabel in linha) { "Coluna $columnLabel nao existe no resultado" }
        return linha[columnLabel]
    }

    override fun close() {
        fechado = true
    }
}

internal class FakePreparedStatement(
    val sql: String,
    private val resultado: FakeResultSet,
    private val falha: SQLException?,
) : PreparedStatement by naoImplementado() {

    val parametros = mutableMapOf<Int, String?>()
    var fechado = false
        private set

    override fun setString(parameterIndex: Int, x: String?) {
        parametros[parameterIndex] = x
    }

    override fun executeQuery(): ResultSet = falha?.let { throw it } ?: resultado

    override fun close() {
        fechado = true
    }
}

internal class FakeConnection(
    private val resultado: FakeResultSet,
    private val falha: SQLException?,
) : Connection by naoImplementado() {

    var statement: FakePreparedStatement? = null
        private set
    var fechada = false
        private set

    override fun prepareStatement(sql: String): PreparedStatement =
        FakePreparedStatement(sql, resultado, falha).also { statement = it }

    override fun close() {
        fechada = true
    }
}

internal class FakeDataSource(
    linhas: List<Map<String, String?>> = emptyList(),
    falha: SQLException? = null,
) : DataSource by naoImplementado() {

    val resultado = FakeResultSet(linhas)
    val conexao = FakeConnection(resultado, falha)
    var conexoesSolicitadas = 0
        private set

    override fun getConnection(): Connection {
        conexoesSolicitadas++
        return conexao
    }
}
