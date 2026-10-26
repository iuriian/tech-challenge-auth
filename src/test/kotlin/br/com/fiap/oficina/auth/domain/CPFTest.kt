package br.com.fiap.oficina.auth.domain

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CPFTest {
    @Nested
    inner class Parse {
        @ParameterizedTest
        @ValueSource(
            strings = [
                "52998224725",
                "529.982.247-25",
                "529 982 247 25",
                "  529.982.247-25  ",
                "11144477735",
                "12345678909",
                "39053344705",
                "00000000191",
                "714.287.938-60",
            ],
        )
        fun `aceita CPF com digitos verificadores corretos, com ou sem mascara`(entrada: String) {
            assertNotNull(CPF.parse(entrada), "esperava CPF valido para '$entrada'")
        }

        @Test
        fun `ignora qualquer caractere nao numerico`() {
            assertEquals(
                CPF.parse("52998224725"),
                CPF.parse("CPF: 529.982.247-25 (titular)"),
            )
        }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "52998224726",
                "52998224715",
                "487.955.230-20",
                "11111111111",
                "00000000000",
                "99999999999",
                "5299822472",
                "529982247250",
                "",
                "abc",
                "529.982.247-2X",
            ],
        )
        fun `rejeita CPF com digito verificador, tamanho ou conteudo invalido`(entrada: String) {
            assertNull(CPF.parse(entrada), "esperava null para '$entrada'")
        }

        @Test
        fun `rejeita sequencias de digitos repetidos mesmo quando passariam no calculo`() {
            // 00000000000 satisfaz o calculo dos verificadores, mas nao e um CPF real.
            assertNull(CPF.parse("00000000000"))
        }

        @Test
        fun `preserva apenas os digitos`() {
            assertEquals("52998224725", CPF.parse("529.982.247-25")?.digitos)
        }
    }

    @Nested
    inner class Representacoes {
        private val cpf = requireNotNull(CPF.parse("52998224725"))

        @Test
        fun `formatado aplica a mascara completa`() {
            assertEquals("529.982.247-25", cpf.formatado)
        }

        @Test
        fun `mascarado expoe apenas os digitos do meio`() {
            assertEquals("***.982.***-**", cpf.mascarado)
        }

        @Test
        fun `toString nunca revela o CPF completo`() {
            assertEquals(cpf.mascarado, cpf.toString())
            assert(!cpf.toString().contains("52998224725"))
            assert(!cpf.toString().contains("529"))
        }
    }

    @Nested
    inner class Igualdade {
        @Test
        fun `CPFs com os mesmos digitos sao iguais independente da formatacao`() {
            val a = CPF.parse("529.982.247-25")
            val b = CPF.parse("52998224725")

            assertEquals(a, b)
            assertEquals(a.hashCode(), b.hashCode())
        }

        @Test
        fun `CPFs diferentes nao sao iguais`() {
            assert(CPF.parse("52998224725") != CPF.parse("11144477735"))
        }
    }
}
