package br.com.fiap.oficina.auth.application.usecase

interface AutenticarUsuarioUseCase {
    fun executar(cpfInformado: String?): ResultadoAutenticacao
}
