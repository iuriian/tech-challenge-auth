package br.com.fiap.oficina.auth.application.usecase

import br.com.fiap.oficina.auth.application.port.TokenEmitido

sealed interface ResultadoAutenticacao {
    data class Autenticado(
        val token: TokenEmitido,
    ) : ResultadoAutenticacao

    data object CpfInvalido : ResultadoAutenticacao

    data object NaoAutorizado : ResultadoAutenticacao
}
