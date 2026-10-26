package br.com.fiap.oficina.auth.presentation

import br.com.fiap.oficina.auth.infrastructure.di.authModules
import br.com.fiap.oficina.auth.presentation.http.AuthHttpHandler
import com.google.cloud.functions.HttpFunction
import com.google.cloud.functions.HttpRequest
import com.google.cloud.functions.HttpResponse
import org.koin.core.Koin
import org.koin.dsl.koinApplication

class AuthFunction : HttpFunction {
    private val handler: AuthHttpHandler by lazy { container.get() }

    override fun service(
        request: HttpRequest,
        response: HttpResponse,
    ) = handler.handle(request, response)

    private companion object {
        val container: Koin by lazy {
            koinApplication { modules(authModules) }.koin
        }
    }
}
