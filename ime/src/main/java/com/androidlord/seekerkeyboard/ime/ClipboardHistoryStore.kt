package com.androidlord.seekerkeyboard.ime

import android.content.Context
import org.json.JSONArray

class ClipboardHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(limit: Int = 5): List<String> {
        val raw = prefs.getString(KEY_HISTORY, "[]").orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val value = array.optString(i)
                    if (value.isNotBlank()) add(value)
                    if (size >= limit) break
                }
            }
        }.getOrDefault(emptyList())
    }

    fun record(value: CharSequence?) {
        val clean = value?.toString()?.trim().orEmpty()
        if (clean.isBlank()) return
        val next = linkedSetOf(clean)
        load(limit = 12).forEach { next.add(it) }
        val json = JSONArray()
        next.take(12).forEach { json.put(it) }
        prefs.edit().putString(KEY_HISTORY, json.toString()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private companion object {
        const val PREFS_NAME = "seeker_clipboard_history"
        const val KEY_HISTORY = "history"
    }
}
