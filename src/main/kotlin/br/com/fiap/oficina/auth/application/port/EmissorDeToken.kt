package br.com.fiap.oficina.auth.application.port

import br.com.fiap.oficina.auth.domain.Cliente

interface EmissorDeToken {
    fun emitir(cliente: Cliente): TokenEmitido
}