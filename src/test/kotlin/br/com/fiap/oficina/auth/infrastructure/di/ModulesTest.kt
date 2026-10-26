package br.com.fiap.oficina.auth.infrastructure.di

import br.com.fiap.oficina.auth.application.port.ChavesPublicas
import br.com.fiap.oficina.auth.application.port.EmissorDeToken
import br.com.fiap.oficina.auth.application.usecase.AutenticarClienteUseCase
import br.com.fiap.oficina.auth.application.usecase.AutenticarClienteUseCaseImpl
import br.com.fiap.oficina.auth.domain.ClienteRepository
import br.com.fiap.oficina.auth.infrastructure.config.DatabaseSettings
import br.com.fiap.oficina.auth.infrastructure.config.TokenSettings
import br.com.fiap.oficina.auth.infrastructure.persistence.JdbcClienteRepository
import br.com.fiap.oficina.auth.infrastructure.token.NimbusTokenAdapter
import br.com.fiap.oficina.auth.presentation.http.AuthHttpHandler
import br.com.fiap.oficina.auth.support.ChavesDeTeste
import br.com.fiap.oficina.auth.support.FakeDataSource
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import br.com.fiap.oficina.auth.presentation.http.TokenRequest
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.time.Clock
import java.time.Duration
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Valida o grafo de dependências real, substituindo apenas o que exigiria banco de dados e
 * variáveis de ambiente: um erro de wiring aqui só apareceria em produção na primeira requisição.
 */
class ModulesTest {

    private val fonteDeDados = FakeDataSource()

    private val substituicoes = module {
        single {
            TokenSettings(
                issuer = "https://auth.oficina.dev",
                audience = "oficina",
                ttl = Duration.ofHours(1),
                privateKeyPem = ChavesDeTeste.pem2048,
            )
        }
        single<DataSource> { fonteDeDados }
    }

    private var app: Koin? = null

    private fun koin(): Koin = app ?: koinApplication {
        allowOverride(true)
        modules(configModule, infrastructureModule, applicationModule, entrypointModule)
        modules(substituicoes)
    }.koin.also { app = it }

    @AfterEach
    fun fechar() {
        app?.close()
        app = null
    }

    @Test
    fun `o handler HTTP e resolvido com todas as dependencias`() {
        koin().get<AuthHttpHandler>()
    }

    @Test
    fun `o caso de uso usa a implementacao concreta`() {
        assertIs<AutenticarClienteUseCaseImpl>(koin().get<AutenticarClienteUseCase>())
    }

    @Test
    fun `o repositorio resolvido e o JDBC`() {
        assertIs<JdbcClienteRepository>(koin().get<ClienteRepository>())
    }

    @Test
    fun `emissor e chaves publicas compartilham a mesma instancia do adapter Nimbus`() {
        val emissor = koin().get<EmissorDeToken>()
        val chaves = koin().get<ChavesPublicas>()

        assertIs<NimbusTokenAdapter>(emissor)
        assertSame(emissor, chaves, "um unico adapter evita carregar a chave RSA duas vezes")
    }

    @Test
    fun `componentes sao singletons - a function reaproveita o container entre invocacoes`() {
        val koin = koin()

        assertSame(koin.get<AuthHttpHandler>(), koin.get<AuthHttpHandler>())
        assertSame(koin.get<AutenticarClienteUseCase>(), koin.get<AutenticarClienteUseCase>())
        assertSame(koin.get<ClienteRepository>(), koin.get<ClienteRepository>())
    }

    @Test
    fun `o clock registrado e UTC`() {
        assertEquals(Clock.systemUTC(), koin().get<Clock>())
    }

    @Test
    fun `o ObjectMapper registrado ignora campos desconhecidos`() {
        val mapper = koin().get<ObjectMapper>()

        assertEquals(
            "123",
            mapper.readValue<TokenRequest>("""{"cpf":"123","grant_type":"password"}""").cpf,
        )
    }

    @Test
    fun `authModules agrega os quatro modulos da aplicacao`() {
        assertEquals(
            listOf(configModule, infrastructureModule, applicationModule, entrypointModule),
            authModules,
        )
    }

    @Test
    fun `fechar o container libera o pool de conexoes`() {
        val koin = koinApplication {
            allowOverride(true)
            modules(configModule, infrastructureModule, applicationModule, entrypointModule)
            modules(
                module {
                    single {
                        TokenSettings(
                            issuer = "https://auth.oficina.dev",
                            audience = "oficina",
                            ttl = Duration.ofHours(1),
                            privateKeyPem = ChavesDeTeste.pem2048,
                        )
                    }
                    single {
                        DatabaseSettings(
                            url = "jdbc:postgresql://localhost:5432/oficina",
                            user = "auth_ro",
                            password = "segredo",
                        )
                    }
                }
            )
        }.koin

        val pool = koin.get<DataSource>() as HikariDataSource
        assertFalse(pool.isClosed)

        koin.close()

        assertTrue(pool.isClosed, "onClose do modulo precisa encerrar o Hikari")
    }
}
