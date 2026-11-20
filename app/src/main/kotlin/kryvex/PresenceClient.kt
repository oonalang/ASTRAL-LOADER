package kryvex.loader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class PresenceHeartbeat(val active: Boolean?, val reason: String?)

class PresenceClient(private val panelBaseUrl: String) {
    suspend fun onlineCount(game: String): Int? = withContext(Dispatchers.IO) {
        runCatching {
            val query = URLEncoder.encode(game, "UTF-8")
            val connection = (URL("${panelBaseUrl.trimEnd('/')}/api/presence/count?game=$query")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                useCaches = false
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (connection.responseCode !in 200..299) return@runCatching null
                val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = JSONObject(response)
                if (!json.optBoolean("status") || !json.has("online_count")) return@runCatching null
                json.optInt("online_count").takeIf { it >= 0 }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    suspend fun heartbeat(sessionToken: String): Boolean? = heartbeatDetails(sessionToken).active

    suspend fun heartbeatDetails(sessionToken: String): PresenceHeartbeat = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL("${panelBaseUrl.trimEnd('/')}/api/presence/heartbeat")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                useCaches = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            try {
                val form = "session_token=" + URLEncoder.encode(sessionToken, "UTF-8")
                connection.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val json = runCatching { JSONObject(body) }.getOrNull()
                if (code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    PresenceHeartbeat(false, json?.optString("reason").orEmpty().ifBlank { "Session expired or unauthorized" })
                } else if (code in 200..299) {
                    PresenceHeartbeat(json?.optBoolean("status"), json?.optString("reason"))
                } else PresenceHeartbeat(null, null)
            } finally {
                connection.disconnect()
            }
        }.getOrElse { PresenceHeartbeat(null, null) }
    }

    suspend fun offline(sessionToken: String): Boolean? = postStatus("offline", sessionToken)

    private suspend fun postStatus(action: String, token: String): Boolean? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL("${panelBaseUrl.trimEnd('/')}/api/presence/$action")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                useCaches = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            try {
                val form = "session_token=" + URLEncoder.encode(token, "UTF-8")
                connection.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_UNAUTHORIZED) return@runCatching false
                if (code !in 200..299) return@runCatching null
                val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                JSONObject(response).optBoolean("status")
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    private companion object {
        const val TIMEOUT_MS = 8_000
    }
}
