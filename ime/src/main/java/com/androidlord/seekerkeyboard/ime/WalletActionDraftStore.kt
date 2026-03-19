package com.androidlord.seekerkeyboard.ime

import android.content.Context
import kotlin.math.max

data class WalletActionDrafts(
    val sendAmountSol: String = "0.01",
    val skrStakeAmount: String = "1",
    val skrUnstakeAmount: String = "1",
)

class WalletActionDraftStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): WalletActionDrafts {
        return WalletActionDrafts(
            sendAmountSol = prefs.getString(KEY_SEND_AMOUNT_SOL, "0.01") ?: "0.01",
            skrStakeAmount = prefs.getString(KEY_SKR_STAKE_AMOUNT, "1") ?: "1",
            skrUnstakeAmount = prefs.getString(KEY_SKR_UNSTAKE_AMOUNT, "1") ?: "1",
        )
    }

    fun stepSendAmount(delta: Double) {
        val current = load().sendAmountSol.toDoubleOrNull() ?: 0.01
        saveSendAmount(((current + delta).coerceAtLeast(0.01)).formatDecimal(2))
    }

    fun stepSkrStakeAmount(delta: Int) {
        val current = load().skrStakeAmount.toIntOrNull() ?: 1
        saveSkrStakeAmount(max(1, current + delta).toString())
    }

    fun stepSkrUnstakeAmount(delta: Int) {
        val current = load().skrUnstakeAmount.toIntOrNull() ?: 1
        saveSkrUnstakeAmount(max(1, current + delta).toString())
    }

    fun saveSendAmount(value: String) {
        prefs.edit().putString(KEY_SEND_AMOUNT_SOL, value).apply()
    }

    fun saveSkrStakeAmount(value: String) {
        prefs.edit().putString(KEY_SKR_STAKE_AMOUNT, value).apply()
    }

    fun saveSkrUnstakeAmount(value: String) {
        prefs.edit().putString(KEY_SKR_UNSTAKE_AMOUNT, value).apply()
    }

    private fun Double.formatDecimal(decimals: Int): String {
        return "%.${decimals}f".format(java.util.Locale.US, this)
            .trimEnd('0')
            .trimEnd('.')
            .ifBlank { "0" }
    }

    private companion object {
        const val PREFS_NAME = "seeker_wallet_drafts"
        const val KEY_SEND_AMOUNT_SOL = "send_amount_sol"
        const val KEY_SKR_STAKE_AMOUNT = "skr_stake_amount"
        const val KEY_SKR_UNSTAKE_AMOUNT = "skr_unstake_amount"
    }
}
