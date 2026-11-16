package com.mobile.ktracea.ktor

import com.mobile.ktracea.KTracea
import com.mobile.ktracea.mock.MockEngine
import com.mobile.ktracea.model.HttpHeader
import com.mobile.ktracea.model.HttpTransaction
import io.ktor.client.HttpClient
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.content.OutgoingContent
import io.ktor.util.date.getTimeMillis
import kotlinx.coroutines.delay

val KTraceaKtorPlugin = createClientPlugin("KTraceaKtorPlugin") {
    onResponse { response ->
        val request = response.request
        val url = request.url.toString()
        val method = request.method.value

        val reqHeaders = request.headers.entries().flatMap { entry ->
            entry.value.map { HttpHeader(entry.key, it) }
        }

        val resHeaders = response.headers.entries().flatMap { entry ->
            entry.value.map { HttpHeader(entry.key, it) }
        }

        val responseBodyText = try {
            response.bodyAsText()
        } catch (e: Exception) {
            null
        }

        val transaction = HttpTransaction(
            id = "${getTimeMillis()}_${(0..99999).random()}",
            url = url,
            method = method,
            requestHeaders = reqHeaders,
            requestBody = null,
            responseCode = response.status.value,
            responseMessage = response.status.description,
            responseHeaders = resHeaders,
            responseBody = responseBodyText,
            durationMs = 0L,
            timestamp = getTimeMillis(),
            isMocked = false
        )
        KTracea.recordTransaction(transaction)
    }
}
