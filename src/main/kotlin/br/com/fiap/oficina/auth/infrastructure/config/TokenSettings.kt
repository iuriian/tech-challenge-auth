package br.com.fiap.oficina.auth.infrastructure.config

import java.time.Duration

data class TokenSettings(
    val issuer: String,
    val audience: String,
    val ttl: Duration,
    val privateKeyPem: String,
) {
    init {
        require(issuer.startsWith("http")) { "ISSUER deve ser uma URL" }
        require(!ttl.isNegative && !ttl.isZero) { "TOKEN_TTL_SECONDS deve ser positivo" }
    }

    override fun toString() = "TokenSettings(issuer=$issuer, audience=$audience, ttl=$ttl)"

    companion object {
        fun fromEnv(env: (String) -> String? = System::getenv) = TokenSettings(
            issuer = env.obrigatoria("ISSUER").trimEnd('/'),
            audience = env("AUDIENCE") ?: "oficina",
            ttl = Duration.ofSeconds(env("TOKEN_TTL_SECONDS")?.toLong() ?: 3600),
            privateKeyPem = env.obrigatoria("JWT_PRIVATE_KEY"),
        )
    }
}

internal fun ((String) -> String?).obrigatoria(nome: String): String =
    this(nome)?.takeIf { it.isNotBlank() } ?: error("Variável de ambiente $nome ausente")