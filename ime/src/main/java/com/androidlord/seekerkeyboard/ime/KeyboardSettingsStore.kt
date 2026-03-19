package com.androidlord.seekerkeyboard.ime

import android.content.Context

enum class KeyboardTheme(val label: String) {
    SAND("Sand"),
    TEAL("Teal"),
    GRAPHITE("Graphite"),
}

enum class KeyboardLayoutMode(val label: String) {
    COMPACT("Compact"),
    COMFORT("Comfort"),
    THUMB("Thumb"),
}

enum class KeyboardLanguage(val label: String) {
    ENGLISH("English"),
    SPANISH("Spanish"),
    PORTUGUESE("Portuguese"),
}

data class KeyboardSettings(
    val theme: KeyboardTheme = KeyboardTheme.SAND,
    val layoutMode: KeyboardLayoutMode = KeyboardLayoutMode.COMFORT,
    val language: KeyboardLanguage = KeyboardLanguage.ENGLISH,
    val showNumberRow: Boolean = true,
    val showWalletKey: Boolean = true,
    val keyHeightDp: Int = 52,
    val hapticsEnabled: Boolean = false,
    val consolidationSourceCountPreview: Int = 1,
    val wallpaperUri: String = "",
    val backgroundHex: String = "",
    val keyHex: String = "",
    val auxiliaryKeyHex: String = "",
    val accentHex: String = "",
    val utilityHex: String = "",
    val panelHex: String = "",
    val textHex: String = "",
)

class KeyboardSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): KeyboardSettings {
        val theme = KeyboardTheme.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, KeyboardTheme.SAND.name) }
            ?: KeyboardTheme.SAND
        val layoutMode = KeyboardLayoutMode.entries.firstOrNull { it.name == prefs.getString(KEY_LAYOUT_MODE, KeyboardLayoutMode.COMFORT.name) }
            ?: KeyboardLayoutMode.COMFORT
        val language = KeyboardLanguage.entries.firstOrNull { it.name == prefs.getString(KEY_LANGUAGE, KeyboardLanguage.ENGLISH.name) }
            ?: KeyboardLanguage.ENGLISH
        return KeyboardSettings(
            theme = theme,
            layoutMode = layoutMode,
            language = language,
            showNumberRow = prefs.getBoolean(KEY_NUMBER_ROW, true),
            showWalletKey = prefs.getBoolean(KEY_WALLET_KEY, true),
            keyHeightDp = prefs.getInt(KEY_KEY_HEIGHT, 52).coerceIn(40, 76),
            hapticsEnabled = prefs.getBoolean(KEY_HAPTICS, false),
            consolidationSourceCountPreview = prefs.getInt(KEY_CONSOLIDATION_SOURCES, 1).coerceIn(1, 99),
            wallpaperUri = prefs.getString(KEY_WALLPAPER_URI, "").orEmpty(),
            backgroundHex = prefs.getString(KEY_BACKGROUND_HEX, "").orEmpty(),
            keyHex = prefs.getString(KEY_KEY_HEX, "").orEmpty(),
            auxiliaryKeyHex = prefs.getString(KEY_AUXILIARY_KEY_HEX, "").orEmpty(),
            accentHex = prefs.getString(KEY_ACCENT_HEX, "").orEmpty(),
            utilityHex = prefs.getString(KEY_UTILITY_HEX, "").orEmpty(),
            panelHex = prefs.getString(KEY_PANEL_HEX, "").orEmpty(),
            textHex = prefs.getString(KEY_TEXT_HEX, "").orEmpty(),
        )
    }

    fun saveTheme(theme: KeyboardTheme) {
        prefs.edit().putString(KEY_THEME, theme.name).apply()
    }

    fun saveLayoutMode(layoutMode: KeyboardLayoutMode) {
        prefs.edit().putString(KEY_LAYOUT_MODE, layoutMode.name).apply()
    }

    fun saveLanguage(language: KeyboardLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.name).apply()
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

    fun saveConsolidationSourceCountPreview(value: Int) {
        prefs.edit().putInt(KEY_CONSOLIDATION_SOURCES, value.coerceIn(1, 99)).apply()
    }

    fun saveWallpaperUri(value: String) {
        prefs.edit().putString(KEY_WALLPAPER_URI, value).apply()
    }

    fun saveBackgroundHex(value: String) {
        prefs.edit().putString(KEY_BACKGROUND_HEX, value).apply()
    }

    fun saveKeyHex(value: String) {
        prefs.edit().putString(KEY_KEY_HEX, value).apply()
    }

    fun saveAuxiliaryKeyHex(value: String) {
        prefs.edit().putString(KEY_AUXILIARY_KEY_HEX, value).apply()
    }

    fun saveAccentHex(value: String) {
        prefs.edit().putString(KEY_ACCENT_HEX, value).apply()
    }

    fun saveUtilityHex(value: String) {
        prefs.edit().putString(KEY_UTILITY_HEX, value).apply()
    }

    fun savePanelHex(value: String) {
        prefs.edit().putString(KEY_PANEL_HEX, value).apply()
    }

    fun saveTextHex(value: String) {
        prefs.edit().putString(KEY_TEXT_HEX, value).apply()
    }

    companion object {
        private const val PREFS_NAME = "seeker_keyboard_settings"
        private const val KEY_THEME = "theme"
        private const val KEY_LAYOUT_MODE = "layout_mode"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_NUMBER_ROW = "number_row"
        private const val KEY_WALLET_KEY = "wallet_key"
        private const val KEY_KEY_HEIGHT = "key_height"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_CONSOLIDATION_SOURCES = "consolidation_sources"
        private const val KEY_WALLPAPER_URI = "wallpaper_uri"
        private const val KEY_BACKGROUND_HEX = "background_hex"
        private const val KEY_KEY_HEX = "key_hex"
        private const val KEY_AUXILIARY_KEY_HEX = "auxiliary_key_hex"
        private const val KEY_ACCENT_HEX = "accent_hex"
        private const val KEY_UTILITY_HEX = "utility_hex"
        private const val KEY_PANEL_HEX = "panel_hex"
        private const val KEY_TEXT_HEX = "text_hex"
    }
}
