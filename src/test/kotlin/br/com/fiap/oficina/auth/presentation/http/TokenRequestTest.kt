package br.com.fiap.oficina.auth.presentation.http

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TokenRequestTest {
    private val mapper =
        jacksonObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    @Test
    fun `desserializa o cpf do corpo`() {
        assertEquals(
            TokenRequest("529.982.247-25"),
            mapper.readValue<TokenRequest>("""{"cpf":"529.982.247-25"}"""),
        )
    }

    @Test
    fun `cpf ausente fica nulo`() {
        assertNull(mapper.readValue<TokenRequest>("{}").cpf)
    }

    @Test
    fun `cpf nulo explicito fica nulo`() {
        assertNull(mapper.readValue<TokenRequest>("""{"cpf":null}""").cpf)
    }

    @Test
    fun `campos desconhecidos nao quebram a desserializacao`() {
        val request = mapper.readValue<TokenRequest>("""{"cpf":"123","grant_type":"password"}""")

        assertEquals("123", request.cpf)
    }
}
