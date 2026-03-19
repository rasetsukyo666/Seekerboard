package com.androidlord.seekerkeyboard.ime

import android.content.Context

class WalletAccessGuardStore(context: Context) {
    private val prefs = context.getSharedPreferences("seeker_wallet_session", Context.MODE_PRIVATE)

    fun isUnlocked(): Boolean {
        return prefs.getBoolean(KEY_UNLOCKED_ONCE, false)
    }

    fun unlock() {
        prefs.edit().putBoolean(KEY_UNLOCKED_ONCE, true).apply()
    }

    fun lock() {
        prefs.edit()
            .putBoolean(KEY_UNLOCKED_ONCE, false)
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
        private const val KEY_UNLOCKED_ONCE = "wallet_unlocked_once"
        private const val KEY_PENDING_OPEN_WALLET = "wallet_pending_open"
    }
}
