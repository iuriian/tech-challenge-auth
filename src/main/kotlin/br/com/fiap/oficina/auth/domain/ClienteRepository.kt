package br.com.fiap.oficina.auth.domain

interface ClienteRepository {
    fun buscarPorCpf(cpf: CPF): Cliente?
}
