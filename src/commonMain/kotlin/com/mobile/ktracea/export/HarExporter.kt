package com.mobile.ktracea.export

import com.mobile.ktracea.model.HttpTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object HarExporter {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun exportToHar(transactions: List<HttpTransaction>): String {
        val root = buildJsonObject {
            put("log", buildJsonObject {
                put("version", "1.2")
                put("creator", buildJsonObject {
                    put("name", "KTracea")
                    put("version", "1.0.0")
                    put("comment", "Kotlin Multiplatform Network Inspector by Sangram Mohanty")
                })
                put("entries", buildJsonArray {
                    transactions.forEach { tx ->
                        add(buildEntry(tx))
                    }
                })
            })
        }
        return json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), root)
    }

    private fun buildEntry(tx: HttpTransaction) = buildJsonObject {
        put("startedDateTime", tx.timestamp.toString())
        put("time", tx.durationMs ?: 0L)
        put("request", buildJsonObject {
            put("method", tx.method)
            put("url", tx.url)
            put("httpVersion", "HTTP/1.1")
            put("headers", buildJsonArray {
                tx.requestHeaders.forEach { header ->
                    add(buildJsonObject {
                        put("name", header.name)
                        put("value", header.value)
                    })
                }
            })
            put("queryString", buildJsonArray {})
            put("headersSize", -1)
            put("bodySize", tx.requestContentLength)
            tx.requestBody?.let { body ->
                put("postData", buildJsonObject {
                    put("mimeType", tx.requestHeaders.find { it.name.equals("content-type", true) }?.value ?: "application/json")
                    put("text", body)
                })
            }
        })
        put("response", buildJsonObject {
            put("status", tx.responseCode ?: 0)
            put("statusText", tx.responseMessage ?: (if (tx.isSuccessful) "OK" else "Error"))
            put("httpVersion", "HTTP/1.1")
            put("headers", buildJsonArray {
                tx.responseHeaders.forEach { header ->
                    add(buildJsonObject {
                        put("name", header.name)
                        put("value", header.value)
                    })
                }
            })
            put("content", buildJsonObject {
                put("size", tx.responseContentLength)
                put("mimeType", tx.responseHeaders.find { it.name.equals("content-type", true) }?.value ?: "application/json")
                put("text", tx.responseBody ?: "")
            })
            put("redirectURL", "")
            put("headersSize", -1)
            put("bodySize", tx.responseContentLength)
        })
        put("cache", buildJsonObject {})
        put("timings", buildJsonObject {
            put("send", 0)
            put("wait", tx.durationMs ?: 0L)
            put("receive", 0)
        })
        if (tx.isMocked) {
            put("comment", "Mocked by KTracea")
        }
    }
}
