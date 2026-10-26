package br.com.fiap.oficina.auth.application.usecase

interface AutenticarClienteUseCase {
    fun executar(cpfInformado: String?): ResultadoAutenticacao
}