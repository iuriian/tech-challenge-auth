package br.com.fiap.oficina.auth.domain

data class Funcionario(
    override val id: UsuarioId,
    override val nome: String,
    override val papel: Papel,
) : Usuario {
    init {
        require(nome.isNotBlank()) { "Nome do funcionário não pode ser vazio" }
        require(papel != Papel.CLIENTE) { "Funcionário não pode autenticar como cliente" }
    }

    /**
     * A tabela funcionario não tem coluna de status: estar cadastrada com um cargo reconhecido é a
     * própria autorização. Cargo que o domínio não conhece não chega a virar [Funcionario] — ver
     * [Papel.cargoDe].
     */
    override fun podeAutenticar(): Boolean = true
}
