package com.androidlord.seekerwallet.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import com.androidlord.seekerwallet.wallet.SolanaCluster
import com.androidlord.seekerwallet.wallet.NativeStakeAccount
import com.androidlord.seekerwallet.wallet.SkrPosition
import com.androidlord.seekerkeyboard.ime.UnifiedAccountPreview

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
            .remove(KEY_NATIVE_STAKE_ACCOUNT_COUNT)
            .remove(KEY_KEYBOARD_STATUS)
            .remove(KEY_REVIEW_REQUIRED)
            .remove(KEY_LAST_REVIEWED_ACTION)
            .apply()
    }

    fun saveNativeStakeAccountCount(count: Int) {
        prefs.edit().putInt(KEY_NATIVE_STAKE_ACCOUNT_COUNT, count.coerceAtLeast(0)).apply()
    }

    fun saveWalletPanelSnapshot(
        totalBalanceUsd: String,
        skrPosition: SkrPosition,
        stakeAccounts: List<NativeStakeAccount>,
        eligibleConsolidationSources: Int,
        unifiedAccounts: List<UnifiedAccountPreview>,
    ) {
        val stakeJson = JSONArray()
        stakeAccounts.take(8).forEach { account ->
            stakeJson.put(
                JSONObject()
                    .put("pubkey", account.pubkey)
                    .put("lamports", account.lamports)
                    .put("stakeState", account.stakeState)
                    .put("delegationVote", account.delegationVote)
                    .put("canStake", account.canStake)
                    .put("canWithdraw", account.canWithdraw)
            )
        }
        val unifiedJson = JSONArray()
        unifiedAccounts.take(6).forEach { account ->
            unifiedJson.put(
                JSONObject()
                    .put("title", account.title)
                    .put("balanceLabel", account.balanceLabel)
                    .put("detailLabel", account.detailLabel)
                    .put("emphasis", account.emphasis)
            )
        }
        prefs.edit()
            .putString(KEY_TOTAL_BALANCE_USD, totalBalanceUsd)
            .putString(KEY_SKR_STAKED, skrPosition.stakedAmount)
            .putString(KEY_SKR_WITHDRAWABLE, skrPosition.withdrawableAmount)
            .putString(KEY_SKR_APY, skrPosition.apyLabel)
            .putString(KEY_STAKE_ACCOUNTS_JSON, stakeJson.toString())
            .putString(KEY_UNIFIED_ACCOUNTS_JSON, unifiedJson.toString())
            .putInt(KEY_ELIGIBLE_CONSOLIDATION_SOURCES, eligibleConsolidationSources.coerceAtLeast(0))
            .apply()
    }

    fun saveKeyboardStatus(status: String) {
        prefs.edit().putString(KEY_KEYBOARD_STATUS, status).apply()
    }

    fun saveReviewState(reviewRequired: Boolean, lastReviewedAction: String) {
        prefs.edit()
            .putBoolean(KEY_REVIEW_REQUIRED, reviewRequired)
            .putString(KEY_LAST_REVIEWED_ACTION, lastReviewedAction)
            .apply()
    }

    private companion object {
        const val KEY_AUTH_TOKEN = "auth_token"
        const val KEY_WALLET_ADDRESS = "wallet_address"
        const val KEY_CLUSTER = "cluster"
        const val KEY_NATIVE_STAKE_ACCOUNT_COUNT = "native_stake_account_count"
        const val KEY_TOTAL_BALANCE_USD = "total_balance_usd"
        const val KEY_SKR_STAKED = "skr_staked"
        const val KEY_SKR_WITHDRAWABLE = "skr_withdrawable"
        const val KEY_SKR_APY = "skr_apy"
        const val KEY_STAKE_ACCOUNTS_JSON = "stake_accounts_json"
        const val KEY_UNIFIED_ACCOUNTS_JSON = "unified_accounts_json"
        const val KEY_ELIGIBLE_CONSOLIDATION_SOURCES = "eligible_consolidation_sources"
        const val KEY_KEYBOARD_STATUS = "keyboard_status"
        const val KEY_REVIEW_REQUIRED = "review_required"
        const val KEY_LAST_REVIEWED_ACTION = "last_reviewed_action"
    }
}
