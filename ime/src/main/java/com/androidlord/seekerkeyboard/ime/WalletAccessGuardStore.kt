package com.androidlord.seekerkeyboard.ime

import android.content.Context

class WalletAccessGuardStore(context: Context) {
    private val prefs = context.getSharedPreferences("seeker_wallet_session", Context.MODE_PRIVATE)

    fun isUnlocked(mode: WalletUnlockMode, nowMs: Long = System.currentTimeMillis()): Boolean {
        return when (mode) {
            WalletUnlockMode.ALWAYS_PROMPT -> false
            WalletUnlockMode.UNTIL_CLOSE -> prefs.getBoolean(KEY_UNLOCKED_UNTIL_CLOSE, false)
            WalletUnlockMode.TIMER_30S, WalletUnlockMode.TIMER_2M -> prefs.getLong(KEY_UNLOCKED_UNTIL_MS, 0L) > nowMs
        }
    }

    fun unlock(mode: WalletUnlockMode) {
        prefs.edit().apply {
            when (mode) {
                WalletUnlockMode.ALWAYS_PROMPT -> {
                    putBoolean(KEY_UNLOCKED_UNTIL_CLOSE, false)
                    putLong(KEY_UNLOCKED_UNTIL_MS, 0L)
                }
                WalletUnlockMode.UNTIL_CLOSE -> {
                    putBoolean(KEY_UNLOCKED_UNTIL_CLOSE, true)
                    putLong(KEY_UNLOCKED_UNTIL_MS, 0L)
                }
                WalletUnlockMode.TIMER_30S -> {
                    putBoolean(KEY_UNLOCKED_UNTIL_CLOSE, false)
                    putLong(KEY_UNLOCKED_UNTIL_MS, System.currentTimeMillis() + 30_000L)
                }
                WalletUnlockMode.TIMER_2M -> {
                    putBoolean(KEY_UNLOCKED_UNTIL_CLOSE, false)
                    putLong(KEY_UNLOCKED_UNTIL_MS, System.currentTimeMillis() + 120_000L)
                }
            }
        }.apply()
    }

    fun lock() {
        prefs.edit()
            .putBoolean(KEY_UNLOCKED_UNTIL_CLOSE, false)
            .putLong(KEY_UNLOCKED_UNTIL_MS, 0L)
            .putBoolean(KEY_PENDING_OPEN_WALLET, false)
            .apply()
    }

    fun markPendingOpenWallet() {
        prefs.edit().putBoolean(KEY_PENDING_OPEN_WALLET, true).apply()
    }

    fun consumePendingOpenWallet(): Boolean {
        val pending = prefs.getBoolean(KEY_PENDING_OPEN_WALLET, false)
        if (pending) {
            prefs.edit().putBoolean(KEY_PENDING_OPEN_WALLET, false).apply()
        }
        return pending
    }

    companion object {
        private const val KEY_UNLOCKED_UNTIL_CLOSE = "wallet_unlocked_until_close"
        private const val KEY_UNLOCKED_UNTIL_MS = "wallet_unlocked_until_ms"
        private const val KEY_PENDING_OPEN_WALLET = "wallet_pending_open"
    }
}
