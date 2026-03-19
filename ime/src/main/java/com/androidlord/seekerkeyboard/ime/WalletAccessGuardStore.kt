package com.androidlord.seekerkeyboard.ime

import android.content.Context

class WalletAccessGuardStore(context: Context) {
    private val prefs = context.getSharedPreferences("seeker_wallet_session", Context.MODE_PRIVATE)

    fun isUnlocked(nowMs: Long = System.currentTimeMillis()): Boolean {
        return prefs.getLong(KEY_UNLOCKED_UNTIL, 0L) > nowMs
    }

    fun unlock(durationMs: Long = DEFAULT_UNLOCK_MS) {
        prefs.edit().putLong(KEY_UNLOCKED_UNTIL, System.currentTimeMillis() + durationMs).apply()
    }

    fun lock() {
        prefs.edit()
            .putLong(KEY_UNLOCKED_UNTIL, 0L)
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
        private const val KEY_UNLOCKED_UNTIL = "wallet_unlocked_until"
        private const val KEY_PENDING_OPEN_WALLET = "wallet_pending_open"
        private const val DEFAULT_UNLOCK_MS = 120_000L
    }
}
