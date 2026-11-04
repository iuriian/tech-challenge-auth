package br.com.fiap.oficina.auth.presentation

import br.com.fiap.oficina.auth.infrastructure.di.authModules
import br.com.fiap.oficina.auth.presentation.http.AuthRoutes
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.cio.CIO
import io.ktor.server.engine.addShutdownHook
import io.ktor.server.engine.embeddedServer
import org.koin.dsl.koinApplication

/**
 * Ponto de entrada da imagem: o Main-Class do fat JAR aponta para a classe gerada a partir deste
 * arquivo (ver `mainClass` no build.gradle.kts).
 *
 * O shutdown hook existe porque o Cloud Run manda SIGTERM antes de recolher a instância: sem ele a
 * JVM morre sem disparar `ApplicationStopped`, e o pool do Hikari fica pendurado no banco até o
 * servidor expirar as conexões.
 */
fun main() {
    val koin = koinApplication { modules(authModules) }.koin

    val servidor =
        embeddedServer(CIO, port = portaDeEscuta(), host = TODAS_AS_INTERFACES) {
            monitor.subscribe(ApplicationStopped) { koin.close() }
            koin.get<AuthRoutes>().configurar(this)
        }

    servidor.addShutdownHook { servidor.stop(CARENCIA_MS, LIMITE_MS) }
    servidor.start(wait = true)
}

/** O Cloud Run injeta PORT; 8080 é o default do contrato e vale também no local. */
internal fun portaDeEscuta(env: (String) -> String? = System::getenv): Int = env("PORT")?.toIntOrNull() ?: PORTA_PADRAO

internal const val PORTA_PADRAO = 8080

private const val TODAS_AS_INTERFACES = "0.0.0.0"
private const val CARENCIA_MS = 1_000L
private const val LIMITE_MS = 5_000L
