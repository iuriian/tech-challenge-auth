package br.com.fiap.oficina.auth.domain

/**
 * Papel que vai no claim `roles` do token. É todo o vocabulário de autorização que os serviços
 * consumidores conhecem — eles não falam com as tabelas de cadastro, então o que não está aqui
 * não existe para efeito de permissão.
 */
enum class Papel {
    CLIENTE,
    ATENDENTE,
    MECANICO,
    ADMIN,
    ;

    companion object {
        /**
         * Traduz o `cargo` gravado na tabela funcionario, devolvendo `null` para o que o domínio
         * não reconhece.
         *
         * [CLIENTE] é recusado de propósito: ele não é cargo, e uma linha de funcionário com esse
         * valor emitiria um token de cliente — confusão de privilégio vinda de um dado que este
         * serviço apenas lê e não governa.
         */
        fun cargoDe(valor: String?): Papel? {
            val bruto = valor?.trim()?.uppercase() ?: return null
            return entries.firstOrNull { it != CLIENTE && it.name == bruto }
        }
    }
}
