package br.com.fiap.oficina.auth.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals

class StatusClienteTest {
    @ParameterizedTest
    @CsvSource(
        "ATIVO, ATIVO",
        "ativo, ATIVO",
        "AtIvO, ATIVO",
        "'  ATIVO  ', ATIVO",
        "INATIVO, INATIVO",
        "inativo, INATIVO",
    )
    fun `converte o valor textual do banco ignorando caixa e espacos`(
        bruto: String,
        esperado: StatusCliente,
    ) {
        assertEquals(esperado, StatusCliente.de(bruto))
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["", " ", "BLOQUEADO", "ACTIVE", "ativo ativo"])
    fun `valor desconhecido ou ausente cai para INATIVO - nega acesso por padrao`(bruto: String?) {
        assertEquals(StatusCliente.INATIVO, StatusCliente.de(bruto))
    }

    @Test
    fun `apenas ATIVO e INATIVO existem - mudanca aqui exige revisar a regra de autenticacao`() {
        assertEquals(listOf("ATIVO", "INATIVO"), StatusCliente.entries.map { it.name })
    }
}
