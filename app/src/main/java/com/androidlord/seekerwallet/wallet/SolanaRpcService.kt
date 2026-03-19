package com.androidlord.seekerwallet.wallet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.pow

class SolanaRpcService {
    suspend fun loadPortfolio(
        rpcUrl: String,
        ownerAddress: String,
    ): PortfolioSnapshot = withContext(Dispatchers.IO) {
        val balanceResponse = post(
            rpcUrl = rpcUrl,
            method = "getBalance",
            params = JSONArray().put(ownerAddress).put(JSONObject().put("commitment", "confirmed")),
        )
        val lamports = balanceResponse
            .getJSONObject("result")
            .getLong("value")

        val tokensResponse = post(
            rpcUrl = rpcUrl,
            method = "getTokenAccountsByOwner",
            params = JSONArray()
                .put(ownerAddress)
                .put(JSONObject().put("programId", TOKEN_PROGRAM_ID))
                .put(JSONObject().put("encoding", "jsonParsed")),
        )

        val tokenAccounts = tokensResponse
            .getJSONObject("result")
            .getJSONArray("value")

        val assets = mutableListOf<TokenHolding>()
        for (index in 0 until tokenAccounts.length()) {
            val parsedInfo = tokenAccounts
                .getJSONObject(index)
                .getJSONObject("account")
                .getJSONObject("data")
                .getJSONObject("parsed")
                .getJSONObject("info")
            val mint = parsedInfo.getString("mint")
            val amountObject = parsedInfo.getJSONObject("tokenAmount")
            val decimals = amountObject.optInt("decimals", 0)
            val rawAmount = amountObject.optString("amount").ifBlank { "0" }.toDoubleOrNull() ?: 0.0
            val uiAmount = amountObject.optString("uiAmountString")
                .takeIf { it.isNotBlank() }
                ?: "%.6f".format(rawAmount / 10.0.pow(decimals.toDouble()))
            if (uiAmount == "0" || uiAmount == "0.0" || uiAmount == "0.000000") continue
            assets += TokenHolding(
                mint = mint,
                symbol = knownSymbol(mint),
                amount = uiAmount,
            )
        }

        PortfolioSnapshot(
            solBalance = lamports / LAMPORTS_PER_SOL.toDouble(),
            tokenHoldings = assets,
        )
    }

    suspend fun requestAirdrop(
        rpcUrl: String,
        ownerAddress: String,
        lamports: Long,
    ): String = withContext(Dispatchers.IO) {
        val response = post(
            rpcUrl = rpcUrl,
            method = "requestAirdrop",
            params = JSONArray().put(ownerAddress).put(lamports),
        )
        response.getString("result")
    }

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

        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = BufferedReader(stream.reader()).use { it.readText() }
        val json = JSONObject(body)

        if (json.has("error")) {
            val message = json.getJSONObject("error").optString("message", "RPC error")
            throw IllegalStateException(message)
        }
        return json
    }

    private fun knownSymbol(mint: String): String {
        return when (mint) {
            "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v" -> "USDC"
            else -> mint.take(4) + "…" + mint.takeLast(4)
        }
    }

    private companion object {
        const val TOKEN_PROGRAM_ID = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
        const val LAMPORTS_PER_SOL = 1_000_000_000L
    }
}

data class PortfolioSnapshot(
    val solBalance: Double,
    val tokenHoldings: List<TokenHolding>,
)

data class TokenHolding(
    val mint: String,
    val symbol: String,
    val amount: String,
)
