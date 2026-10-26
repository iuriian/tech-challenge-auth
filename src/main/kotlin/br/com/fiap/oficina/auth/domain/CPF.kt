package br.com.fiap.oficina.auth.domain

@ConsistentCopyVisibility
data class CPF private constructor(val digitos: String) {

    val formatado: String
        get() = "${digitos.substring(0, 3)}.${digitos.substring(3, 6)}." +
                "${digitos.substring(6, 9)}-${digitos.substring(9)}"

    val mascarado: String
        get() = "***.${digitos.substring(3, 6)}.***-**"

    override fun toString(): String = mascarado

    companion object {
        fun parse(raw: String): CPF? {
            val d = raw.filter(Char::isDigit)
            if (d.length != 11 || d.all { it == d[0] }) return null

            fun digitoVerificador(n: Int): Int {
                val soma = (0 until n).sumOf { (d[it] - '0') * (n + 1 - it) }
                val resto = (soma * 10) % 11
                return if (resto == 10) 0 else resto
            }

            val valido = digitoVerificador(9) == d[9] - '0' &&
                    digitoVerificador(10) == d[10] - '0'
            return if (valido) CPF(d) else null
        }
    }
}