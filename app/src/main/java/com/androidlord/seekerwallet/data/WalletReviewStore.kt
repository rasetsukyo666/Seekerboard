package com.androidlord.seekerwallet.data

import android.content.Context

data class PendingWalletReview(
    val action: String = "",
    val title: String = "",
    val summary: String = "",
    val detail: String = "",
)

class WalletReviewStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): PendingWalletReview {
        return PendingWalletReview(
            action = prefs.getString(KEY_ACTION, "").orEmpty(),
            title = prefs.getString(KEY_TITLE, "").orEmpty(),
            summary = prefs.getString(KEY_SUMMARY, "").orEmpty(),
            detail = prefs.getString(KEY_DETAIL, "").orEmpty(),
        )
    }

    fun save(review: PendingWalletReview) {
        prefs.edit()
            .putString(KEY_ACTION, review.action)
            .putString(KEY_TITLE, review.title)
            .putString(KEY_SUMMARY, review.summary)
            .putString(KEY_DETAIL, review.detail)
            .apply()
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_ACTION)
            .remove(KEY_TITLE)
            .remove(KEY_SUMMARY)
            .remove(KEY_DETAIL)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "seeker_wallet_review"
        const val KEY_ACTION = "action"
        const val KEY_TITLE = "title"
        const val KEY_SUMMARY = "summary"
        const val KEY_DETAIL = "detail"
    }
}
