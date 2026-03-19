package com.androidlord.seekerwallet.wallet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class SkrOfficialService {
    suspend fun buildStakeTx(
        naturalTokenAmount: String,
        payer: String,
        user: String,
    ): OfficialTxEnvelope {
        return withContext(Dispatchers.IO) {
            ensureTx(callServerFn(HASH_CREATE_STAKE_TX, mapOf("naturalTokenAmount" to naturalTokenAmount, "payer" to payer, "user" to user)))
        }
    }

    suspend fun buildUnstakeTx(
        naturalTokenAmount: String,
        user: String,
    ): OfficialTxEnvelope {
        return withContext(Dispatchers.IO) {
            ensureTx(callServerFn(HASH_CREATE_UNSTAKE_TX, mapOf("naturalTokenAmount" to naturalTokenAmount, "user" to user)))
        }
    }

    suspend fun buildWithdrawTx(
        payer: String,
        user: String,
    ): OfficialTxEnvelope {
        return withContext(Dispatchers.IO) {
            ensureTx(callServerFn(HASH_CREATE_WITHDRAW_TX, mapOf("payer" to payer, "user" to user)))
        }
    }

    suspend fun fetchUserStake(
        walletAddress: String,
    ): OfficialUserStakeState {
        return withContext(Dispatchers.IO) {
            val payload = decodeSerovalNode(
                post(
                    hash = HASH_GET_USER_STAKE,
                    body = encodeServerFnBody(mapOf("walletAddress" to walletAddress)),
                )
            ) as? Map<*, *> ?: throw IllegalStateException("Official SKR API returned invalid payload.")

            val result = payload["result"] as? Map<*, *> ?: throw IllegalStateException("Official SKR API result missing.")
            OfficialUserStakeState(
                ok = result["ok"] as? Boolean ?: false,
                error = result["error"]?.toString(),
                cluster = result["cluster"]?.toString(),
                shares = result["shares"]?.toString(),
                unstakingAmount = result["unstakingAmount"]?.toString(),
                unstakeTimestamp = result["unstakeTimestamp"]?.toString(),
                unstakableAmount = result["unstakableAmount"]?.toString(),
                stakedAmountForDisplay = result["stakedAmountForDisplay"]?.toString(),
                withdrawableAmountForDisplay = result["withdrawableAmountForDisplay"]?.toString(),
                availableBalance = result["availableBalance"]?.toString(),
            )
        }
    }

    suspend fun fetchCurrentApy(): Double? {
        return withContext(Dispatchers.IO) {
            val payload = decodeSerovalNode(
                post(
                    hash = HASH_GET_CURRENT_APY,
                    body = encodeServerFnBody(emptyMap()),
                )
            ) as? Map<*, *> ?: return@withContext null
            val result = payload["result"] as? Map<*, *> ?: return@withContext null
            if (result["ok"] != true) return@withContext null
            val apy = result["apy"]
            when (apy) {
                is Number -> apy.toDouble()
                is Map<*, *> -> apy["s"]?.toString()?.toDoubleOrNull()
                else -> null
            }
        }
    }

    private fun ensureTx(payload: Map<String, Any?>): OfficialTxEnvelope {
        val result = payload["result"] as? Map<*, *> ?: throw IllegalStateException("Official SKR API result missing.")
        val ok = result["ok"] as? Boolean ?: false
        if (!ok) {
            throw IllegalStateException(result["error"]?.toString() ?: "Official SKR API returned not-ok response.")
        }
        val tx = result["transaction"]?.toString()
            ?: throw IllegalStateException("Official SKR API did not return a transaction payload.")
        return OfficialTxEnvelope(
            transaction = tx,
            fee = result["fee"]?.toString(),
            cluster = result["cluster"]?.toString(),
        )
    }

    private suspend fun callServerFn(
        hash: String,
        data: Map<String, Any>,
    ): Map<String, Any?> {
        val decoded = decodeSerovalNode(post(hash, encodeServerFnBody(data))) as? Map<*, *>
            ?: throw IllegalStateException("Official SKR API returned invalid envelope.")
        @Suppress("UNCHECKED_CAST")
        return decoded as Map<String, Any?>
    }

    private fun post(
        hash: String,
        body: String,
    ): JSONObject {
        val connection = (URL("$SERVER_FN_BASE$hash").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("x-tsr-serverFn", "true")
            setRequestProperty("accept", "application/json")
            setRequestProperty("content-type", "application/json")
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 15_000
        }

        OutputStreamWriter(connection.outputStream).use { writer ->
            writer.write(body)
        }

        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val raw = BufferedReader(stream.reader()).use { it.readText() }
        if (code !in 200..299) {
            throw IllegalStateException("Official SKR API request failed ($code): $raw")
        }
        return JSONObject(raw)
    }

    private fun encodeServerFnBody(data: Map<String, Any>): String {
        return JSONObject()
            .put(
                "t",
                JSONObject()
                    .put("t", 10)
                    .put("i", 0)
                    .put(
                        "p",
                        JSONObject()
                            .put("k", JSONArray().put("data"))
                            .put("v", JSONArray().put(encodeObjectNode(data, 1)))
                            .put("s", 1)
                    )
                    .put("o", 0)
            )
            .put("f", 31)
            .put("m", JSONArray())
            .toString()
    }

    private fun encodeObjectNode(
        map: Map<String, Any>,
        index: Int,
    ): JSONObject {
        val keys = JSONArray()
        val values = JSONArray()
        map.forEach { (key, value) ->
            keys.put(key)
            values.put(encodeValueNode(value))
        }
        return JSONObject()
            .put("t", 10)
            .put("i", index)
            .put("p", JSONObject().put("k", keys).put("v", values).put("s", map.size))
            .put("o", 0)
    }

    private fun encodeValueNode(value: Any?): Any {
        return when (value) {
            null -> JSONObject().put("t", 6)
            is String -> JSONObject().put("t", 1).put("s", value)
            is Boolean -> JSONObject().put("t", 2).put("s", if (value) 2 else 1)
            is Number -> JSONObject().put("t", 3).put("s", value)
            is Map<*, *> -> {
                val typed = linkedMapOf<String, Any>()
                value.forEach { (k, v) ->
                    if (k != null && v != null) typed[k.toString()] = v
                }
                encodeObjectNode(typed, 1)
            }
            is List<*> -> {
                val array = JSONArray()
                value.forEach { array.put(encodeValueNode(it)) }
                JSONObject().put("t", 9).put("a", array)
            }
            else -> JSONObject().put("t", 1).put("s", value.toString())
        }
    }

    private fun decodeSerovalNode(node: Any?): Any? {
        if (node == null) return null
        if (node is JSONArray) {
            return buildList {
                for (i in 0 until node.length()) {
                    add(decodeSerovalNode(node.get(i)))
                }
            }
        }
        if (node !is JSONObject || !node.has("t")) return node

        return when (node.getInt("t")) {
            1 -> node.optString("s")
            2 -> when (node.optInt("s")) {
                2 -> true
                1 -> false
                else -> null
            }
            3 -> node.opt("s")
            6 -> null
            9 -> {
                val array = node.optJSONArray("a") ?: JSONArray()
                buildList {
                    for (i in 0 until array.length()) {
                        add(decodeSerovalNode(array.get(i)))
                    }
                }
            }
            10, 11 -> {
                val payload = node.optJSONObject("p") ?: JSONObject()
                val keys = payload.optJSONArray("k") ?: JSONArray()
                val values = payload.optJSONArray("v") ?: JSONArray()
                linkedMapOf<String, Any?>().apply {
                    for (i in 0 until keys.length()) {
                        val key = if (node.getInt("t") == 11) {
                            decodeSerovalNode(keys.get(i)).toString()
                        } else {
                            keys.get(i).toString()
                        }
                        put(key, decodeSerovalNode(values.get(i)))
                    }
                }
            }
            else -> node
        }
    }

    companion object {
        private const val SERVER_FN_BASE = "https://stake.solanamobile.com/_serverFn/"
        private const val HASH_CREATE_STAKE_TX = "dc813ba819d17751b8eed9bf1bec60ead4815ba05542ea87351f533b88062f28"
        private const val HASH_CREATE_UNSTAKE_TX = "3245bb4ea443c37eadd38a73c78bf55c0bf612f654ee0e7a25a460ab7ff00164"
        private const val HASH_CREATE_WITHDRAW_TX = "bd247fc68624cb504883b3e55c2e3e9dfbf344a46d2193f695c042e085c28fa4"
        private const val HASH_GET_USER_STAKE = "78cdbf4c268706c43b41b4e84323eb790ad6d4c8fc6ef07fa5e8f418774a7e67"
        private const val HASH_GET_CURRENT_APY = "48292189fcfbf90252fc613ffef21b1fde10b2b042b6d374f2f036a1044769af"
    }
}

data class OfficialTxEnvelope(
    val transaction: String,
    val fee: String? = null,
    val cluster: String? = null,
)

data class OfficialUserStakeState(
    val ok: Boolean = false,
    val error: String? = null,
    val cluster: String? = null,
    val shares: String? = null,
    val unstakingAmount: String? = null,
    val unstakeTimestamp: String? = null,
    val unstakableAmount: String? = null,
    val stakedAmountForDisplay: String? = null,
    val withdrawableAmountForDisplay: String? = null,
    val availableBalance: String? = null,
)
