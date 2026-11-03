
package com.example.smartdashboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class DashboardApi {

    suspend fun getState(
        baseUrl: String
    ): Result<DashboardState> = withContext(Dispatchers.IO) {
        executeRequest(baseUrl, "/api/state")
    }

    suspend fun postEvent(
        baseUrl: String,
        payload: JSONObject
    ): Result<DashboardState> = withContext(Dispatchers.IO) {
        executeRequest(baseUrl, "/api/event", payload)
    }

    private fun executeRequest(
        baseUrl: String,
        endpoint: String,
        payload: JSONObject? = null
    ): Result<DashboardState> {
        var connection: HttpURLConnection? = null

        return try {
            val normalizedUrl = DashboardSettings
                .normalizeUrl(baseUrl)
                .trimEnd('/')

            val url = URL("$normalizedUrl$endpoint")

            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = if (payload == null) "GET" else "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                useCaches = false
                setRequestProperty("Accept", "application/json")

                if (payload != null) {
                    doOutput = true
                    setRequestProperty(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                    )
                }
            }

            if (payload != null) {
                OutputStreamWriter(
                    connection.outputStream,
                    Charsets.UTF_8
                ).use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }
            }

            val responseCode = connection.responseCode

            val responseStream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseText = responseStream?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
                    .use { reader -> reader.readText() }
            }.orEmpty()

            if (responseCode !in 200..299) {
                val details = responseText.ifBlank {
                    "No error details returned by server"
                }

                throw Exception("HTTP $responseCode: $details")
            }

            if (responseText.isBlank()) {
                throw Exception("Server returned an empty response")
            }

            val json = JSONObject(responseText)

            // POST /api/event may return {"dashboard": {...}}
            val dashboardJson = json.optJSONObject("dashboard") ?: json

            Result.success(DashboardState.fromJson(dashboardJson))

        } catch (e: Exception) {
            val message = when (e) {
                is java.net.SocketTimeoutException ->
                    "Connection timed out. Check Wi-Fi and server."

                is java.net.ConnectException ->
                    "Cannot connect. Check server IP and port 5000."

                is java.net.UnknownHostException ->
                    "Server address could not be resolved."

                else ->
                    e.localizedMessage ?: e.javaClass.simpleName
            }

            Result.failure(Exception(message, e))
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        private const val TIMEOUT_MS = 10_000
    }
}
