package br.com.fiap.oficina.auth.support

import java.lang.reflect.Proxy

/**
 * Cria uma implementação dinâmica de [T] que lança [UnsupportedOperationException] em qualquer
 * chamada. Serve como alvo de delegação (`by`) nos fakes: só o que o teste precisa é sobrescrito,
 * e qualquer uso inesperado da API falha de forma explícita em vez de silenciosa.
 */
internal inline fun <reified T : Any> naoImplementado(): T =
    Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
    ) { proxy, metodo, args ->
        when (metodo.name) {
            "toString" -> "FakeDe${T::class.java.simpleName}"
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            else -> throw UnsupportedOperationException(
                "${T::class.java.simpleName}.${metodo.name} nao deveria ser chamado no teste",
            )
        }
    } as T
