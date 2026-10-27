package br.com.fiap.oficina.auth.infrastructure.persistence

import br.com.fiap.oficina.auth.infrastructure.config.DatabaseSettings
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataSourceFactoryTest {
    private val settings =
        DatabaseSettings(
            url = "jdbc:postgresql://10.0.0.5:5432/oficina",
            user = "auth_ro",
            password = "segredo",
        )

    /** initializationFailTimeout negativo permite subir o servico mesmo com o banco fora. */
    @Test
    fun `pool e criado sem exigir banco disponivel na inicializacao`() {
        DataSourceFactory.criar(settings).use { pool ->
            assertEquals(-1, pool.initializationFailTimeout)
            assertTrue(!pool.isClosed)
        }
    }

    @Test
    fun `credenciais e url vem das configuracoes`() {
        DataSourceFactory.criar(settings).use { pool ->
            assertEquals(settings.url, pool.jdbcUrl)
            assertEquals(settings.user, pool.username)
        }
    }

    @Test
    fun `pool e somente leitura - o servico nunca escreve no banco`() {
        DataSourceFactory.criar(settings).use { pool ->
            assertTrue(pool.isReadOnly)
        }
    }

    @Test
    fun `pool e dimensionado para ambiente serverless`() {
        DataSourceFactory.criar(settings).use { pool ->
            assertEquals("auth-pool", pool.poolName)
            assertEquals(2, pool.maximumPoolSize)
            assertEquals(0, pool.minimumIdle)
            assertEquals(5_000, pool.connectionTimeout)
            assertEquals(60_000, pool.idleTimeout)
            assertEquals(300_000, pool.maxLifetime)
        }
    }

    @Test
    fun `close libera o pool`() {
        val pool = DataSourceFactory.criar(settings)

        pool.close()

        assertTrue(pool.isClosed)
    }
}
