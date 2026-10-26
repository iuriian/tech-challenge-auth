package br.com.fiap.oficina.auth.infrastructure.token

import br.com.fiap.oficina.auth.support.ChavesDeTeste
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.KeyUse
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.security.interfaces.RSAPublicKey
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RsaKeyLoaderTest {

    @Test
    fun `carrega chave PKCS8 valida com metadados JWK preenchidos`() {
        val chave = RsaKeyLoader.carregar(ChavesDeTeste.pem2048)

        assertEquals(KeyUse.SIGNATURE, chave.keyUse)
        assertEquals(JWSAlgorithm.RS256, chave.algorithm)
        assertNotNull(chave.keyID)
        assertTrue(chave.isPrivate, "a chave precisa conter a parte privada para assinar")
    }

    @Test
    fun `keyID e o thumbprint da chave - estavel entre carregamentos`() {
        val primeira = RsaKeyLoader.carregar(ChavesDeTeste.pem2048)
        val segunda = RsaKeyLoader.carregar(ChavesDeTeste.pem2048)

        assertEquals(primeira.keyID, segunda.keyID)
        assertEquals(primeira.computeThumbprint().toString(), primeira.keyID)
    }

    @Test
    fun `chaves distintas produzem keyIDs distintos`() {
        val a = RsaKeyLoader.carregar(ChavesDeTeste.pem2048)
        val b = RsaKeyLoader.carregar(ChavesDeTeste.pem2048Alternativo)

        assertTrue(a.keyID != b.keyID)
    }

    @Test
    fun `aceita PEM com escapes literais vindos de variavel de ambiente`() {
        val comEscapes = RsaKeyLoader.carregar(ChavesDeTeste.pem2048ComEscapes)

        assertEquals(RsaKeyLoader.carregar(ChavesDeTeste.pem2048).keyID, comEscapes.keyID)
    }

    @Test
    fun `aceita PEM com espacos em volta`() {
        val chave = RsaKeyLoader.carregar("\n\n  ${ChavesDeTeste.pem2048}  \n\n")

        assertNotNull(chave.keyID)
    }

    @Test
    fun `derivada a chave publica a partir da privada`() {
        val chave = RsaKeyLoader.carregar(ChavesDeTeste.pem2048)
        val esperada = ChavesDeTeste.par2048.public as RSAPublicKey

        assertEquals(esperada.modulus, chave.toRSAPublicKey().modulus)
        assertEquals(esperada.publicExponent, chave.toRSAPublicKey().publicExponent)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "",
            "   ",
            "nao-e-um-pem",
            "-----BEGIN RSA PRIVATE KEY-----\nMIIE\n-----END RSA PRIVATE KEY-----",
            "-----BEGIN PUBLIC KEY-----\nMIIB\n-----END PUBLIC KEY-----",
        ]
    )
    fun `rejeita formatos diferentes de PKCS8`(pem: String) {
        val erro = assertFailsWith<IllegalArgumentException> { RsaKeyLoader.carregar(pem) }

        assertTrue(erro.message!!.startsWith("Chave deve estar em PKCS#8"))
    }

    @Test
    fun `rejeita chave menor que 2048 bits`() {
        val erro = assertFailsWith<IllegalArgumentException> {
            RsaKeyLoader.carregar(ChavesDeTeste.pem1024)
        }

        assertEquals("Chave RSA deve ter ao menos 2048 bits", erro.message)
    }

    @Test
    fun `rejeita chave RSA sem parametros CRT - nao serve para assinar com Nimbus`() {
        val erro = assertFailsWith<IllegalStateException> {
            RsaKeyLoader.carregar(ChavesDeTeste.pemSemParametrosCrt)
        }

        assertEquals("Chave privada RSA sem parâmetros CRT", erro.message)
    }

    @Test
    fun `rejeita conteudo base64 que nao e uma chave RSA`() {
        val lixo = "-----BEGIN PRIVATE KEY-----\nQUJDREVGR0g=\n-----END PRIVATE KEY-----"

        assertFailsWith<Exception> { RsaKeyLoader.carregar(lixo) }
    }
}
