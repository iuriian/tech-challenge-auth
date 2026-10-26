package br.com.fiap.oficina.auth.domain

data class Cliente(
    val id: ClienteId,
    val nome: String,
    val status: StatusCliente,
) {
    init {
        require(nome.isNotBlank()) { "Nome do cliente não pode ser vazio" }
    }

    fun podeAutenticar(): Boolean = status == StatusCliente.ATIVO
}