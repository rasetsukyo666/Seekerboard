package helium314.keyboard.seeker

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

class SeekerJupiterSwapService(
    private val apiKey: String,
) {
    suspend fun getQuote(
        inputMint: String,
        outputMint: String,
        amount: Long,
        slippageBps: Int = 50,
    ): JupiterQuote = withContext(Dispatchers.IO) {
        val query = listOf(
            "inputMint" to inputMint,
            "outputMint" to outputMint,
            "amount" to amount.toString(),
            "slippageBps" to slippageBps.toString(),
            "swapMode" to "ExactIn",
        ).joinToString("&") { (key, value) ->
            key + "=" + URLEncoder.encode(value, Charsets.UTF_8.name())
        }

        val response = get("$SWAP_BASE/quote?$query")
        if (response.has("error")) {
            throw IllegalStateException(response.optString("error", "Quote failed"))
        }
        JupiterQuote(
            raw = response,
            inputMint = response.getString("inputMint"),
            outputMint = response.getString("outputMint"),
            inAmount = response.getString("inAmount"),
            outAmount = response.getString("outAmount"),
            priceImpactPct = response.optString("priceImpactPct", "0"),
        )
    }

    suspend fun buildSwapTransaction(
        quote: JupiterQuote,
        userPublicKey: String,
    ): ByteArray = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("quoteResponse", quote.raw)
            .put("userPublicKey", userPublicKey)
            .put("wrapAndUnwrapSol", true)

        val response = post("$SWAP_BASE/swap", payload)
        if (response.has("error")) {
            throw IllegalStateException(response.optString("error", "Swap build failed"))
        }
        val transaction = response.optString("swapTransaction")
        if (transaction.isBlank()) {
            throw IllegalStateException("Swap transaction missing")
        }
        Base64.decode(transaction, Base64.DEFAULT)
    }

    private fun get(url: String): JSONObject {
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
        val json = JSONObject(body)
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException(
                json.optString("error", json.optString("message", "HTTP ${connection.responseCode}"))
            )
        }
        return json
    }

    private fun post(url: String, payload: JSONObject): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("x-api-key", apiKey)
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        OutputStreamWriter(connection.outputStream).use { writer ->
            writer.write(payload.toString())
        }
        val body = BufferedReader(
            (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream).reader()
        ).use { it.readText() }
        val json = JSONObject(body)
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException(
                json.optString("error", json.optString("message", "HTTP ${connection.responseCode}"))
            )
        }
        return json
    }

    data class JupiterQuote(
        val raw: JSONObject,
        val inputMint: String,
        val outputMint: String,
        val inAmount: String,
        val outAmount: String,
        val priceImpactPct: String,
    )

    companion object {
        private const val SWAP_BASE = "https://api.jup.ag/swap/v1"
    }
}
