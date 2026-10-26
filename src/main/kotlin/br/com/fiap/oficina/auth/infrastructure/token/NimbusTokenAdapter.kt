package br.com.fiap.oficina.auth.infrastructure.token

import br.com.fiap.oficina.auth.application.port.ChavesPublicas
import br.com.fiap.oficina.auth.application.port.EmissorDeToken
import br.com.fiap.oficina.auth.application.port.TokenEmitido
import br.com.fiap.oficina.auth.domain.Cliente
import br.com.fiap.oficina.auth.infrastructure.config.TokenSettings
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.time.Clock
import java.util.Date
import java.util.UUID

class NimbusTokenAdapter(
    private val settings: TokenSettings,
    private val clock: Clock,
) : EmissorDeToken,
    ChavesPublicas {
    private val rsaKey: RSAKey = RsaKeyLoader.carregar(settings.privateKeyPem)
    private val signer = RSASSASigner(rsaKey)
    private val jwksJson: String = JWKSet(rsaKey.toPublicJWK()).toString()

    override fun jwks(): String = jwksJson

    override fun emitir(cliente: Cliente): TokenEmitido {
        val agora = clock.instant()

        val claims =
            JWTClaimsSet
                .Builder()
                .issuer(settings.issuer)
                .subject(cliente.id.toString())
                .audience(settings.audience)
                .issueTime(Date.from(agora))
                .notBeforeTime(Date.from(agora))
                .expirationTime(Date.from(agora.plus(settings.ttl)))
                .jwtID(UUID.randomUUID().toString())
                .claim("name", cliente.nome)
                .claim("roles", listOf(ROLE_CLIENTE))
                .build()

        val header =
            JWSHeader
                .Builder(JWSAlgorithm.RS256)
                .keyID(rsaKey.keyID)
                .type(JOSEObjectType.JWT)
                .build()

        val jwt = SignedJWT(header, claims).apply { sign(signer) }.serialize()
        return TokenEmitido(jwt, settings.ttl.seconds)
    }

    private companion object {
        const val ROLE_CLIENTE = "CLIENTE"
    }
}
