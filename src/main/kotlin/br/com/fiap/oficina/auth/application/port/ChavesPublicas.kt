package br.com.fiap.oficina.auth.application.port

interface ChavesPublicas {
    fun jwks(): String
}