package com.androidlord.seekerkeyboard.ime

import android.content.Context
import org.json.JSONArray

data class WalletSessionSnapshot(
    val walletAddress: String? = null,
    val authTokenPresent: Boolean = false,
    val clusterName: String = "DEVNET",
    val statusMessage: String = "Keyboard wallet ready.",
    val nativeStakeAccountCount: Int = 0,
    val totalBalanceUsd: String = "$0.00",
    val skrApyLabel: String = "APY unavailable",
    val skrStakedAmount: String = "0",
    val skrWithdrawableAmount: String = "0",
    val eligibleConsolidationSources: Int = 0,
    val stakeAccountsPreview: List<InlineStakePreview> = emptyList(),
)

data class InlineStakePreview(
    val pubkey: String,
    val lamports: Long,
    val stakeState: String,
)

class WalletSessionSnapshotStore(context: Context) {
    private val prefs = context.getSharedPreferences("seeker_wallet_session", Context.MODE_PRIVATE)

    fun load(): WalletSessionSnapshot {
        return WalletSessionSnapshot(
            walletAddress = prefs.getString("wallet_address", null),
            authTokenPresent = !prefs.getString("auth_token", null).isNullOrBlank(),
            clusterName = prefs.getString("cluster", "DEVNET") ?: "DEVNET",
            statusMessage = prefs.getString("keyboard_status", "Keyboard wallet ready.") ?: "Keyboard wallet ready.",
            nativeStakeAccountCount = prefs.getInt("native_stake_account_count", 0),
            totalBalanceUsd = prefs.getString("total_balance_usd", "$0.00") ?: "$0.00",
            skrApyLabel = prefs.getString("skr_apy", "APY unavailable") ?: "APY unavailable",
            skrStakedAmount = prefs.getString("skr_staked", "0") ?: "0",
            skrWithdrawableAmount = prefs.getString("skr_withdrawable", "0") ?: "0",
            eligibleConsolidationSources = prefs.getInt("eligible_consolidation_sources", 0),
            stakeAccountsPreview = parseStakeAccounts(prefs.getString("stake_accounts_json", "[]").orEmpty()),
        )
    }

    private fun parseStakeAccounts(raw: String): List<InlineStakePreview> {
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        InlineStakePreview(
                            pubkey = item.optString("pubkey"),
                            lamports = item.optLong("lamports"),
                            stakeState = item.optString("stakeState"),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
