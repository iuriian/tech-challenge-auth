package br.com.fiap.oficina.auth.infrastructure.config

data class DatabaseSettings(
    val url: String,
    val user: String,
    val password: String,
) {
    override fun toString() = "DatabaseSettings(url=$url, user=$user)"

    companion object {
        fun fromEnv(env: (String) -> String? = System::getenv) =
            DatabaseSettings(
                url = env.obrigatoria("DB_URL"),
                user = env.obrigatoria("DB_USER"),
                password = env.obrigatoria("DB_PASSWORD"),
            )
    }
}
