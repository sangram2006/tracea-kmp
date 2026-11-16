package com.mobile.ktracea.android

import android.content.Context
import com.mobile.ktracea.KTracea
import com.mobile.ktracea.mock.MockEngine
import com.mobile.ktracea.model.HttpHeader
import com.mobile.ktracea.model.HttpTransaction
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class KTraceaOkHttpInterceptor(
    private val context: Context? = null
) : Interceptor {

    private val utf8: Charset = StandardCharsets.UTF_8

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()
        val method = request.method
        val config = KTracea.config

        // Check Mock Engine first
        if (config.mockingEnabled) {
            val matchingRule = MockEngine.findMatchingRule(url, method)
            if (matchingRule != null) {
                if (matchingRule.delayMs > 0) {
                    try {
                        Thread.sleep(matchingRule.delayMs)
                    } catch (ignored: InterruptedException) {}
                }

                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val responseBody = matchingRule.responseBody.toResponseBody(mediaType)

                val responseBuilder = Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(matchingRule.responseCode)
                    .message("Mocked by KTracea")
                    .body(responseBody)

                matchingRule.responseHeaders.forEach { header ->
                    responseBuilder.addHeader(header.name, header.value)
                }

                val mockResponse = responseBuilder.build()

                val transaction = HttpTransaction(
                    id = "${System.currentTimeMillis()}_${(0..9999).random()}",
                    url = url,
                    method = method,
                    requestHeaders = request.headers.map { HttpHeader(it.first, it.second) },
                    requestBody = extractRequestBody(request),
                    responseCode = matchingRule.responseCode,
                    responseMessage = "Mocked by KTracea",
                    responseHeaders = matchingRule.responseHeaders,
                    responseBody = matchingRule.responseBody,
                    durationMs = matchingRule.delayMs,
                    timestamp = System.currentTimeMillis(),
                    isMocked = true
                )
                KTracea.recordTransaction(transaction)

                return mockResponse
            }
        }

        val startTime = System.currentTimeMillis()
        val reqHeaders = request.headers.map { HttpHeader(it.first, it.second) }
        val reqBody = extractRequestBody(request)

        val txId = "${startTime}_${(0..9999).random()}"

        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            val failedTransaction = HttpTransaction(
                id = txId,
                url = url,
                method = method,
                requestHeaders = reqHeaders,
                requestBody = reqBody,
                durationMs = duration,
                timestamp = startTime,
                error = e.message ?: e.toString()
            )
            KTracea.recordTransaction(failedTransaction)
            throw e
        }

        val duration = System.currentTimeMillis() - startTime
        val resHeaders = response.headers.map { HttpHeader(it.first, it.second) }

        val resBodyString = try {
            val responseBody = response.body
            if (responseBody != null) {
                val source = responseBody.source()
                source.request(Long.MAX_VALUE) // Buffer the entire body
                val buffer = source.buffer
                buffer.clone().readString(utf8)
            } else null
        } catch (e: Exception) {
            null
        }

        val transaction = HttpTransaction(
            id = txId,
            url = url,
            method = method,
            requestHeaders = reqHeaders,
            requestBody = reqBody,
            responseCode = response.code,
            responseMessage = response.message,
            responseHeaders = resHeaders,
            responseBody = resBodyString,
            durationMs = duration,
            timestamp = startTime,
            isMocked = false
        )
        KTracea.recordTransaction(transaction)

        return response
    }

    private fun extractRequestBody(request: okhttp3.Request): String? {
        val body = request.body ?: return null
        return try {
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readString(utf8)
        } catch (e: Exception) {
            null
        }
    }
}
