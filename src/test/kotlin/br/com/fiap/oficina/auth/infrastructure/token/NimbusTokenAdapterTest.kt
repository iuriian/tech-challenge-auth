package br.com.fiap.oficina.auth.infrastructure.token

import br.com.fiap.oficina.auth.domain.Papel
import br.com.fiap.oficina.auth.domain.StatusCliente
import br.com.fiap.oficina.auth.infrastructure.config.TokenSettings
import br.com.fiap.oficina.auth.support.ChavesDeTeste
import br.com.fiap.oficina.auth.support.cliente
import br.com.fiap.oficina.auth.support.funcionario
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NimbusTokenAdapterTest {
    private val agora = Instant.parse("2026-03-10T12:00:00Z")
    private val clock = Clock.fixed(agora, ZoneOffset.UTC)

    private val settings =
        TokenSettings(
            issuer = "https://auth.oficina.dev",
            audience = "oficina",
            ttl = Duration.ofMinutes(15),
            privateKeyPem = ChavesDeTeste.pem2048,
        )

    private val adapter = NimbusTokenAdapter(settings, clock)

    private val id = UUID.fromString("7a1f0a2c-0000-4000-8000-000000000001")
    private val joana = cliente(id = id, nome = "Joana Souza", status = StatusCliente.ATIVO)

    @Test
    fun `token emitido tem assinatura RS256 verificavel pela chave publica do JWKS`() {
        val jwt = SignedJWT.parse(adapter.emitir(joana).accessToken)
        val publica =
            JWKSet
                .parse(adapter.jwks())
                .keys
                .single()
                .toRSAKey()
                .toRSAPublicKey()

        assertTrue(jwt.verify(RSASSAVerifier(publica)), "assinatura invalida")
    }

    @Test
    fun `header traz alg, typ e o kid da chave usada`() {
        val jwt = SignedJWT.parse(adapter.emitir(joana).accessToken)

        assertEquals(JWSAlgorithm.RS256, jwt.header.algorithm)
        assertEquals(JOSEObjectType.JWT, jwt.header.type)
        assertEquals(
            JWKSet
                .parse(adapter.jwks())
                .keys
                .single()
                .keyID,
            jwt.header.keyID,
            "kid do token precisa casar com o kid publicado no JWKS",
        )
    }

    @Test
    fun `claims identificam o usuario e o emissor`() {
        val claims = SignedJWT.parse(adapter.emitir(joana).accessToken).jwtClaimsSet

        assertEquals("https://auth.oficina.dev", claims.issuer)
        assertEquals(id.toString(), claims.subject)
        assertEquals(listOf("oficina"), claims.audience)
        assertEquals("Joana Souza", claims.getStringClaim("name"))
        assertEquals(listOf("CLIENTE"), claims.getStringListClaim("roles"))
    }

    @ParameterizedTest
    @EnumSource(Papel::class, names = ["CLIENTE"], mode = EnumSource.Mode.EXCLUDE)
    fun `roles do funcionario traz exatamente o cargo cadastrado`(papel: Papel) {
        val token = adapter.emitir(funcionario(id = id, nome = "Carlos Pereira", papel = papel))
        val claims = SignedJWT.parse(token.accessToken).jwtClaimsSet

        assertEquals(listOf(papel.name), claims.getStringListClaim("roles"))
        assertEquals(id.toString(), claims.subject)
        assertEquals("Carlos Pereira", claims.getStringClaim("name"))
    }

    @Test
    fun `o claim roles nunca traz mais de um papel`() {
        val emitidos =
            listOf(joana, funcionario(papel = Papel.ATENDENTE), funcionario(papel = Papel.ADMIN))
                .map { SignedJWT.parse(adapter.emitir(it).accessToken).jwtClaimsSet }

        assertTrue(emitidos.all { it.getStringListClaim("roles").size == 1 })
    }

    @Test
    fun `janela de validade usa o clock injetado e o ttl configurado`() {
        val claims = SignedJWT.parse(adapter.emitir(joana).accessToken).jwtClaimsSet

        assertEquals(Date.from(agora), claims.issueTime)
        assertEquals(Date.from(agora), claims.notBeforeTime)
        assertEquals(Date.from(agora.plus(Duration.ofMinutes(15))), claims.expirationTime)
    }

    @Test
    fun `expiraEmSegundos reflete o ttl em segundos`() {
        assertEquals(900, adapter.emitir(joana).expiraEmSegundos)
    }

    @Test
    fun `cada emissao recebe um jti unico`() {
        val primeiro = SignedJWT.parse(adapter.emitir(joana).accessToken).jwtClaimsSet.jwtid
        val segundo = SignedJWT.parse(adapter.emitir(joana).accessToken).jwtClaimsSet.jwtid

        assertTrue(primeiro.isNotBlank())
        assertTrue(primeiro != segundo, "jti repetido impede deteccao de replay")
    }

    @Test
    fun `jwks expoe apenas a chave publica`() {
        val json = adapter.jwks()
        val chave = JWKSet.parse(json).keys.single()

        assertTrue(!chave.isPrivate, "JWKS nao pode conter material privado")
        listOf("\"d\"", "\"p\"", "\"q\"", "\"dp\"", "\"dq\"", "\"qi\"").forEach {
            assertTrue(it !in json, "JWKS vazou o campo $it")
        }
        assertEquals("RS256", chave.algorithm.name)
        assertEquals("sig", chave.keyUse.identifier())
    }

    @Test
    fun `jwks e estavel entre chamadas`() {
        assertEquals(adapter.jwks(), adapter.jwks())
    }

    @Test
    fun `chave invalida falha na construcao do adapter`() {
        val invalido = settings.copy(privateKeyPem = "chave-qualquer")

        assertFailsWith<IllegalArgumentException> { NimbusTokenAdapter(invalido, clock) }
    }

    @Test
    fun `tokens de usuarios diferentes tem subjects diferentes`() {
        val outro = cliente(id = UUID.randomUUID(), nome = "Carlos")

        val a = SignedJWT.parse(adapter.emitir(joana).accessToken).jwtClaimsSet.subject
        val b = SignedJWT.parse(adapter.emitir(outro).accessToken).jwtClaimsSet.subject

        assertTrue(a != b)
    }
}
