package com.androidlord.seekerkeyboard.ime

import android.content.Context

data class WalletSessionSnapshot(
    val walletAddress: String? = null,
    val authTokenPresent: Boolean = false,
    val clusterName: String = "DEVNET",
)

class WalletSessionSnapshotStore(context: Context) {
    private val prefs = context.getSharedPreferences("seeker_wallet_session", Context.MODE_PRIVATE)

    fun load(): WalletSessionSnapshot {
        return WalletSessionSnapshot(
            walletAddress = prefs.getString("wallet_address", null),
            authTokenPresent = !prefs.getString("auth_token", null).isNullOrBlank(),
            clusterName = prefs.getString("cluster", "DEVNET") ?: "DEVNET",
        )
    }
}
