package helium314.keyboard.seeker

import android.content.Context

class SeekerWalletSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("seeker_wallet_session_v1", Context.MODE_PRIVATE)

    fun loadAuthToken(): String? = prefs.getString(KEY_AUTH_TOKEN, null)

    fun saveAuthToken(authToken: String?) {
        prefs.edit().putString(KEY_AUTH_TOKEN, authToken).apply()
    }

    fun loadWalletAddress(): String? = prefs.getString(KEY_WALLET_ADDRESS, null)

    fun saveWalletAddress(address: String?) {
        prefs.edit().putString(KEY_WALLET_ADDRESS, address).apply()
    }

    fun loadCluster(): SolanaCluster {
        val raw = prefs.getString(KEY_CLUSTER, SolanaCluster.MAINNET.name).orEmpty()
        return SolanaCluster.entries.firstOrNull { it.name == raw } ?: SolanaCluster.MAINNET
    }

    fun saveCluster(cluster: SolanaCluster) {
        prefs.edit().putString(KEY_CLUSTER, cluster.name).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_AUTH_TOKEN = "auth_token"
        const val KEY_WALLET_ADDRESS = "wallet_address"
        const val KEY_CLUSTER = "cluster"
    }
}
