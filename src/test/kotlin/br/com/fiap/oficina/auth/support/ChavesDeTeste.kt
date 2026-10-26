package br.com.fiap.oficina.auth.support

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.RSAPrivateKeySpec
import java.util.Base64

/**
 * Chaves RSA geradas em tempo de execução: evita material criptográfico versionado no repositório
 * e garante que os testes não dependem de uma chave específica. A geração é cara, por isso cada
 * tamanho é criado uma única vez por execução.
 */
internal object ChavesDeTeste {

    val par2048: KeyPair by lazy { gerar(2048) }
    val par1024: KeyPair by lazy { gerar(1024) }
    val par2048Alternativo: KeyPair by lazy { gerar(2048) }

    /** PEM PKCS#8 válido, do jeito que a aplicação espera receber em `JWT_PRIVATE_KEY`. */
    val pem2048: String by lazy { paraPem(par2048) }
    val pem1024: String by lazy { paraPem(par1024) }
    val pem2048Alternativo: String by lazy { paraPem(par2048Alternativo) }

    /** Mesmo PEM, mas com `\n` literal — formato comum quando a chave vem de variável de ambiente. */
    val pem2048ComEscapes: String by lazy { pem2048.replace("\n", "\\n") }

    /** Chave RSA sem os parâmetros CRT (apenas módulo e expoente privado). */
    val pemSemParametrosCrt: String by lazy {
        val privada = par2048.private as RSAPrivateCrtKey
        val semCrt = KeyFactory.getInstance("RSA")
            .generatePrivate(RSAPrivateKeySpec(privada.modulus, privada.privateExponent))
        envelopar(semCrt.encoded)
    }

    private fun gerar(bits: Int): KeyPair =
        KeyPairGenerator.getInstance("RSA").apply { initialize(bits) }.generateKeyPair()

    private fun paraPem(par: KeyPair): String = envelopar(par.private.encoded)

    private fun envelopar(der: ByteArray): String = buildString {
        appendLine("-----BEGIN PRIVATE KEY-----")
        appendLine(Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(der))
        append("-----END PRIVATE KEY-----")
    }
}
