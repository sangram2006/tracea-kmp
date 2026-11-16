package com.mobile.ktracea.model

import kotlinx.serialization.Serializable

@Serializable
data class HttpHeader(
    val name: String,
    val value: String
)

@Serializable
data class HttpTransaction(
    val id: String,
    val url: String,
    val method: String,
    val requestHeaders: List<HttpHeader> = emptyList(),
    val requestBody: String? = null,
    val requestContentLength: Long = 0L,
    val responseCode: Int? = null,
    val responseMessage: String? = null,
    val responseHeaders: List<HttpHeader> = emptyList(),
    val responseBody: String? = null,
    val responseContentLength: Long = 0L,
    val durationMs: Long? = null,
    val timestamp: Long = 0L,
    val error: String? = null,
    val isMocked: Boolean = false
) {
    val isSuccessful: Boolean
        get() = responseCode != null && responseCode in 200..299

    val statusGroup: String
        get() = when (responseCode) {
            in 200..299 -> "2xx"
            in 300..399 -> "3xx"
            in 400..499 -> "4xx"
            in 500..599 -> "5xx"
            else -> if (error != null) "Error" else "Pending"
        }

    val path: String
        get() = try {
            val withoutProtocol = url.substringAfter("://")
            val pathPart = withoutProtocol.substringAfter("/", "")
            if (pathPart.isEmpty()) "/" else "/$pathPart"
        } catch (e: Exception) {
            url
        }

    val host: String
        get() = try {
            val withoutProtocol = url.substringAfter("://")
            withoutProtocol.substringBefore("/").substringBefore("?")
        } catch (e: Exception) {
            ""
        }
}

@Serializable
data class MockRule(
    val id: String,
    val urlPattern: String,
    val method: String = "*", // "*" means all methods
    val responseCode: Int = 200,
    val responseBody: String = "{}",
    val responseHeaders: List<HttpHeader> = listOf(
        HttpHeader("Content-Type", "application/json")
    ),
    val delayMs: Long = 0L,
    val isEnabled: Boolean = true,
    val description: String = ""
) {
    fun matches(targetUrl: String, targetMethod: String): Boolean {
        if (!isEnabled) return false
        val methodMatch = method == "*" || method.equals(targetMethod, ignoreCase = true)
        if (!methodMatch) return false

        val urlMatch = try {
            targetUrl.contains(urlPattern) || Regex(urlPattern).containsMatchIn(targetUrl)
        } catch (e: Exception) {
            targetUrl.contains(urlPattern)
        }
        return urlMatch
    }
}

@Serializable
data class KTraceaConfig(
    val maxTransactions: Int = 500,
    val webServerEnabled: Boolean = true,
    val webServerPort: Int = 8080,
    val mockingEnabled: Boolean = true,
    val showNotification: Boolean = true,
    val sanitizeHeaders: List<String> = listOf("Authorization", "Cookie", "Set-Cookie")
)
