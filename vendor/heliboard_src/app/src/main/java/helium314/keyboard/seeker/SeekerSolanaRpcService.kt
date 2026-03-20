package helium314.keyboard.seeker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class SeekerSolanaRpcService {
    suspend fun getLatestBlockhash(rpcUrl: String): String = withContext(Dispatchers.IO) {
        val response = post(
            rpcUrl = rpcUrl,
            method = "getLatestBlockhash",
            params = JSONArray().put(JSONObject().put("commitment", "confirmed")),
        )
        response.getJSONObject("result")
            .getJSONObject("value")
            .getString("blockhash")
    }

    suspend fun getBalance(rpcUrl: String, address: String): Long = withContext(Dispatchers.IO) {
        val response = post(
            rpcUrl = rpcUrl,
            method = "getBalance",
            params = JSONArray()
                .put(address)
                .put(JSONObject().put("commitment", "confirmed")),
        )
        response.getJSONObject("result")
            .getLong("value")
    }

    private fun post(
        rpcUrl: String,
        method: String,
        params: JSONArray,
    ): JSONObject {
        val payload = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", method)
            .put("method", method)
            .put("params", params)

        val connection = (URL(rpcUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 15_000
        }

        OutputStreamWriter(connection.outputStream).use { writer ->
            writer.write(payload.toString())
        }

        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val body = BufferedReader(stream.reader()).use { it.readText() }
        val json = JSONObject(body)
        if (json.has("error")) {
            val message = json.getJSONObject("error").optString("message", "RPC error")
            throw IllegalStateException(message)
        }
        return json
    }
}
