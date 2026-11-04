package br.com.fiap.oficina.auth.domain

enum class StatusCliente {
    ATIVO,
    INATIVO,
    ;

    companion object {
        fun de(valor: String?): StatusCliente = entries.firstOrNull { it.name == valor?.trim()?.uppercase() } ?: INATIVO
    }
}
