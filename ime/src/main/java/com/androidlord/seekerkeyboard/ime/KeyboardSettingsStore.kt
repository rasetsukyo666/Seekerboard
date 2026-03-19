package com.androidlord.seekerkeyboard.ime

import android.content.Context

enum class KeyboardTheme(val label: String) {
    SAND("Sand"),
    TEAL("Teal"),
    GRAPHITE("Graphite"),
}

data class KeyboardSettings(
    val theme: KeyboardTheme = KeyboardTheme.SAND,
    val showNumberRow: Boolean = true,
    val showWalletKey: Boolean = true,
    val keyHeightDp: Int = 52,
    val hapticsEnabled: Boolean = false,
)

class KeyboardSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): KeyboardSettings {
        val theme = KeyboardTheme.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, KeyboardTheme.SAND.name) }
            ?: KeyboardTheme.SAND
        return KeyboardSettings(
            theme = theme,
            showNumberRow = prefs.getBoolean(KEY_NUMBER_ROW, true),
            showWalletKey = prefs.getBoolean(KEY_WALLET_KEY, true),
            keyHeightDp = prefs.getInt(KEY_KEY_HEIGHT, 52).coerceIn(40, 76),
            hapticsEnabled = prefs.getBoolean(KEY_HAPTICS, false),
        )
    }

    fun saveTheme(theme: KeyboardTheme) {
        prefs.edit().putString(KEY_THEME, theme.name).apply()
    }

    fun saveNumberRow(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NUMBER_ROW, enabled).apply()
    }

    fun saveWalletKey(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WALLET_KEY, enabled).apply()
    }

    fun saveKeyHeightDp(value: Int) {
        prefs.edit().putInt(KEY_KEY_HEIGHT, value.coerceIn(40, 76)).apply()
    }

    fun saveHapticsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTICS, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "seeker_keyboard_settings"
        private const val KEY_THEME = "theme"
        private const val KEY_NUMBER_ROW = "number_row"
        private const val KEY_WALLET_KEY = "wallet_key"
        private const val KEY_KEY_HEIGHT = "key_height"
        private const val KEY_HAPTICS = "haptics"
    }
}
