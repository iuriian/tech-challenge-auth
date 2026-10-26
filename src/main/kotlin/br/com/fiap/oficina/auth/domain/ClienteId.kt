package br.com.fiap.oficina.auth.domain
import java.util.UUID

data class ClienteId(val valor: UUID) {
    override fun toString(): String = valor.toString()
}