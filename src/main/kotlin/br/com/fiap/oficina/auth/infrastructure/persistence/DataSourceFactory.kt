package br.com.fiap.oficina.auth.infrastructure.persistence

import br.com.fiap.oficina.auth.infrastructure.config.DatabaseSettings
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

object DataSourceFactory {
    fun criar(cfg: DatabaseSettings): HikariDataSource =
        HikariDataSource(
            HikariConfig().apply {
                poolName = "auth-pool"
                jdbcUrl = cfg.url
                username = cfg.user
                password = cfg.password
                maximumPoolSize = 2
                minimumIdle = 0
                connectionTimeout = 5_000
                idleTimeout = 60_000
                maxLifetime = 300_000
                initializationFailTimeout = -1
                isReadOnly = true
            },
        )
}
