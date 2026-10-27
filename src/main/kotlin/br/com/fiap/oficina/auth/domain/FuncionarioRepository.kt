package br.com.fiap.oficina.auth.domain

interface FuncionarioRepository {
    fun buscarPorCpf(cpf: CPF): Funcionario?
}
