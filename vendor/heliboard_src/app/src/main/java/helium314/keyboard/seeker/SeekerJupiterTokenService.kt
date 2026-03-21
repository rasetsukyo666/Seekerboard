package helium314.keyboard.seeker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

class SeekerJupiterTokenService(
    private val apiKey: String,
) {
    suspend fun searchTokens(query: String): List<JupiterToken> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        val response = get("https://api.jup.ag/tokens/v2/search?query=$encoded")
        buildList {
            for (i in 0 until response.length()) {
                val token = response.getJSONObject(i)
                add(
                    JupiterToken(
                        mint = token.getString("id"),
                        symbol = token.optString("symbol", "TOKEN"),
                        name = token.optString("name", token.optString("symbol", "Token")),
                        decimals = token.optInt("decimals", 0),
                        verified = token.optBoolean("isVerified", false),
                    )
                )
            }
        }
    }

    private fun get(url: String): JSONArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("x-api-key", apiKey)
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        val body = BufferedReader(
            (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream).reader()
        ).use { it.readText() }
        if (connection.responseCode !in 200..299) {
            val json = runCatching { JSONObject(body) }.getOrNull()
            throw IllegalStateException(json?.optString("error", json.optString("message", "Token search failed")) ?: "Token search failed")
        }
        return JSONArray(body)
    }

    data class JupiterToken(
        val mint: String,
        val symbol: String,
        val name: String,
        val decimals: Int,
        val verified: Boolean,
    )
}
