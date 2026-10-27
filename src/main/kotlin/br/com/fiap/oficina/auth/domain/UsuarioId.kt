package br.com.fiap.oficina.auth.domain
import java.util.UUID

/**
 * Identificador de quem autentica, seja cliente ou funcionário. As duas tabelas usam `uuid` como
 * chave primária e o valor vai cru no claim `sub`.
 */
data class UsuarioId(
    val valor: UUID,
) {
    override fun toString(): String = valor.toString()
}
