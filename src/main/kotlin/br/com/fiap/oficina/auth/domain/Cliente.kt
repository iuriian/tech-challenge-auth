package br.com.fiap.oficina.auth.domain

data class Cliente(
    override val id: UsuarioId,
    override val nome: String,
    val status: StatusCliente,
) : Usuario {
    init {
        require(nome.isNotBlank()) { "Nome do cliente não pode ser vazio" }
    }

    override val papel: Papel = Papel.CLIENTE

    override fun podeAutenticar(): Boolean = status == StatusCliente.ATIVO
}
