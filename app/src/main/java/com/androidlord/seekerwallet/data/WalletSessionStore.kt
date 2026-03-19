package com.androidlord.seekerwallet.data

import android.content.Context
import com.androidlord.seekerwallet.wallet.SolanaCluster

class WalletSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("seeker_wallet_session", Context.MODE_PRIVATE)

    fun loadAuthToken(): String? {
        return prefs.getString(KEY_AUTH_TOKEN, null)
    }

    fun saveAuthToken(authToken: String?) {
        prefs.edit().putString(KEY_AUTH_TOKEN, authToken).apply()
    }

    fun loadWalletAddress(): String? {
        return prefs.getString(KEY_WALLET_ADDRESS, null)
    }

    fun saveWalletAddress(address: String?) {
        prefs.edit().putString(KEY_WALLET_ADDRESS, address).apply()
    }

    fun loadCluster(): SolanaCluster {
        val raw = prefs.getString(KEY_CLUSTER, SolanaCluster.DEVNET.name).orEmpty()
        return SolanaCluster.entries.firstOrNull { it.name == raw } ?: SolanaCluster.DEVNET
    }

    fun saveCluster(cluster: SolanaCluster) {
        prefs.edit().putString(KEY_CLUSTER, cluster.name).apply()
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_WALLET_ADDRESS)
            .apply()
    }

    private companion object {
        const val KEY_AUTH_TOKEN = "auth_token"
        const val KEY_WALLET_ADDRESS = "wallet_address"
        const val KEY_CLUSTER = "cluster"
    }
}
