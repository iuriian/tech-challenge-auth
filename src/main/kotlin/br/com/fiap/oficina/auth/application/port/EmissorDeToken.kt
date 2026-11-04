package br.com.fiap.oficina.auth.application.port

import br.com.fiap.oficina.auth.domain.Usuario

interface EmissorDeToken {
    fun emitir(usuario: Usuario): TokenEmitido
}
