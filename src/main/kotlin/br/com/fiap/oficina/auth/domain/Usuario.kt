package br.com.fiap.oficina.auth.domain

/**
 * Identidade autenticável, qualquer que seja a tabela de origem.
 *
 * É exatamente o que o emissor precisa saber para montar o token — `sub`, `name` e `roles` — e o
 * que permite ao caso de uso tratar cliente e funcionário pelo mesmo caminho. Selada porque o
 * conjunto de origens é fechado: quem autentica está numa das duas tabelas, não numa terceira.
 */
sealed interface Usuario {
    val id: UsuarioId
    val nome: String
    val papel: Papel

    fun podeAutenticar(): Boolean
}
