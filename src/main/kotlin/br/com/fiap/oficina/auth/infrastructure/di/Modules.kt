package br.com.fiap.oficina.auth.infrastructure.di

import br.com.fiap.oficina.auth.application.port.ChavesPublicas
import br.com.fiap.oficina.auth.application.port.EmissorDeToken
import br.com.fiap.oficina.auth.application.usecase.AutenticarClienteUseCase
import br.com.fiap.oficina.auth.application.usecase.AutenticarClienteUseCaseImpl
import br.com.fiap.oficina.auth.domain.ClienteRepository
import br.com.fiap.oficina.auth.infrastructure.config.DatabaseSettings
import br.com.fiap.oficina.auth.infrastructure.config.TokenSettings
import br.com.fiap.oficina.auth.infrastructure.persistence.DataSourceFactory
import br.com.fiap.oficina.auth.infrastructure.persistence.JdbcClienteRepository
import br.com.fiap.oficina.auth.infrastructure.token.NimbusTokenAdapter
import br.com.fiap.oficina.auth.presentation.http.AuthRoutes
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.binds
import org.koin.dsl.module
import org.koin.dsl.onClose
import java.time.Clock
import javax.sql.DataSource

val configModule =
    module {
        single { TokenSettings.fromEnv() }
        single { DatabaseSettings.fromEnv() }
        single<Clock> { Clock.systemUTC() }
        single<ObjectMapper> {
            jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }

val infrastructureModule =
    module {
        single<DataSource> { DataSourceFactory.criar(get()) } onClose { (it as? AutoCloseable)?.close() }

        singleOf(::JdbcClienteRepository) { bind<ClienteRepository>() }

        single { NimbusTokenAdapter(get(), get()) } binds
            arrayOf(
                EmissorDeToken::class,
                ChavesPublicas::class,
            )
    }

val applicationModule =
    module {
        singleOf(::AutenticarClienteUseCaseImpl) { bind<AutenticarClienteUseCase>() }
    }

val entrypointModule =
    module {
        singleOf(::AuthRoutes)
    }

val authModules = listOf(configModule, infrastructureModule, applicationModule, entrypointModule)
