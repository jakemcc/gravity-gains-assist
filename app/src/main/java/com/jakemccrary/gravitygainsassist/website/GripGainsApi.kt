package com.jakemccrary.gravitygainsassist.website

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class GripGainsApiResponse(
    val statusCode: Int,
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

                GripGainsApiResponse(statusCode = connection.responseCode)
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
