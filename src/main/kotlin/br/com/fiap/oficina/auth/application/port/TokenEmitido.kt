package br.com.fiap.oficina.auth.application.port

data class TokenEmitido(
    val accessToken: String,
    val expiraEmSegundos: Long,
)
