package br.com.fiap.oficina.auth.infrastructure.token

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

internal object RsaKeyLoader {
    fun carregar(pem: String): RSAKey {
        val normalizado = pem.replace("\\n", "\n").trim()
        require(normalizado.startsWith("-----BEGIN PRIVATE KEY-----")) {
            "Chave deve estar em PKCS#8 (BEGIN PRIVATE KEY). Gere com: openssl genpkey -algorithm RSA"
        }

        val der =
            Base64.getMimeDecoder().decode(
                normalizado.lineSequence().filterNot { it.startsWith("-----") }.joinToString(""),
            )

        val kf = KeyFactory.getInstance("RSA")
        val privada =
            kf.generatePrivate(PKCS8EncodedKeySpec(der)) as? RSAPrivateCrtKey
                ?: error("Chave privada RSA sem parâmetros CRT")
        val publica =
            kf.generatePublic(
                RSAPublicKeySpec(privada.modulus, privada.publicExponent),
            ) as RSAPublicKey

        require(publica.modulus.bitLength() >= 2048) { "Chave RSA deve ter ao menos 2048 bits" }

        val base = RSAKey.Builder(publica).privateKey(privada).build()
        return RSAKey
            .Builder(base)
            .keyID(base.computeThumbprint().toString())
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .build()
    }
}
