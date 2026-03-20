package helium314.keyboard.seeker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

class SeekerSnsResolver {
    suspend fun resolveRecipient(input: String): String = withContext(Dispatchers.IO) {
        val trimmed = input.trim()
        if (!trimmed.endsWith(".sol", ignoreCase = true)) {
            return@withContext trimmed
        }

        val encoded = URLEncoder.encode(trimmed, Charsets.UTF_8.name())
        val connection = (URL("$SDK_PROXY_BASE/resolve/$encoded").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
        }

        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val body = stream.bufferedReader().use { it.readText() }
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException(body.ifBlank { "SNS resolution failed" })
        }

        val parsed = JSONObject(body)
        parsed.optString("result")
            .ifBlank { parsed.optString("address") }
            .ifBlank { parsed.optString("owner") }
            .ifBlank { throw IllegalStateException("SNS returned no resolved address") }
    }

    private companion object {
        const val SDK_PROXY_BASE = "https://sns-sdk-proxy.bonfida.workers.dev"
    }
}
