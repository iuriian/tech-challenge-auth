package br.com.fiap.oficina.auth.support

import com.google.cloud.functions.HttpRequest
import com.google.cloud.functions.HttpResponse
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.StringReader
import java.io.StringWriter
import java.util.Optional

internal class FakeHttpRequest(
    private val metodo: String,
    private val caminho: String,
    private val corpo: String = "",
) : HttpRequest by naoImplementado() {

    override fun getMethod(): String = metodo

    override fun getPath(): String = caminho

    override fun getReader(): BufferedReader = BufferedReader(StringReader(corpo))

    override fun getContentType(): Optional<String> = Optional.of("application/json")
}

internal class FakeHttpResponse : HttpResponse by naoImplementado() {

    private val buffer = StringWriter()
    private val escritor = BufferedWriter(buffer)
    private val cabecalhos = mutableMapOf<String, MutableList<String>>()

    var status: Int? = null
        private set

    var tipoDeConteudo: String? = null
        private set

    val corpo: String
        get() {
            escritor.flush()
            return buffer.toString()
        }

    fun cabecalho(nome: String): String? = cabecalhos[nome]?.firstOrNull()

    override fun setStatusCode(code: Int) {
        status = code
    }

    override fun setContentType(contentType: String) {
        tipoDeConteudo = contentType
    }

    override fun appendHeader(header: String, value: String) {
        cabecalhos.getOrPut(header) { mutableListOf() }.add(value)
    }

    override fun getHeaders(): MutableMap<String, MutableList<String>> = cabecalhos

    override fun getWriter(): BufferedWriter = escritor
}
