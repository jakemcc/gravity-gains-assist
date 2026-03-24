package com.jakemccrary.gravitygainsassist.website

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class GripGainsApiResponse(
    val statusCode: Int,
    val responseBody: String? = null,
)

interface GripGainsApi {
    @Throws(IOException::class)
    suspend fun execute(request: GripGainsRequest): GripGainsApiResponse
}

class HttpUrlConnectionGripGainsApi(
    private val connectionFactory: GripGainsConnectionFactory = UrlGripGainsConnectionFactory(),
) : GripGainsApi {
    override suspend fun execute(request: GripGainsRequest): GripGainsApiResponse {
        return withContext(Dispatchers.IO) {
            val connection = connectionFactory.open(request.url)
            try {
                connection.requestMethod = request.method
                connection.doOutput = true
                request.headers.forEach { (name, value) ->
                    connection.setRequestProperty(name, value)
                }
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(request.body)
                }

                val statusCode = connection.responseCode
                GripGainsApiResponse(
                    statusCode = statusCode,
                    responseBody = connection.readResponseBody(statusCode),
                )
            } finally {
                connection.disconnect()
            }
        }
    }
}

interface GripGainsConnectionFactory {
    fun open(url: String): HttpURLConnection
}

class UrlGripGainsConnectionFactory : GripGainsConnectionFactory {
    override fun open(url: String): HttpURLConnection {
        return URL(url).openConnection() as HttpURLConnection
    }
}

private fun HttpURLConnection.readResponseBody(statusCode: Int): String? {
    val stream = if (statusCode >= 400) {
        errorStream
    } else {
        inputStream
    } ?: return null

    return try {
        stream.bufferedReader(Charsets.UTF_8).use(BufferedReader::readText)
            .trim()
            .ifBlank { null }
    } catch (_: IOException) {
        null
    }
}
