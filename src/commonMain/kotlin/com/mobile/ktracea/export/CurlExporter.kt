package com.mobile.ktracea.export

import com.mobile.ktracea.model.HttpTransaction

object CurlExporter {

    fun exportToCurl(transaction: HttpTransaction): String {
        val builder = StringBuilder("curl")
        builder.append(" -X ${transaction.method}")

        transaction.requestHeaders.forEach { header ->
            val escapedValue = header.value.replace("\"", "\\\"")
            builder.append(" -H \"${header.name}: $escapedValue\"")
        }

        transaction.requestBody?.let { body ->
            if (body.isNotEmpty()) {
                val escapedBody = body.replace("'", "'\\''")
                builder.append(" --data '$escapedBody'")
            }
        }

        builder.append(" \"${transaction.url}\"")
        return builder.toString()
    }
}
