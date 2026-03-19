package com.androidlord.seekerkeyboard.ime

import android.content.ClipDescription
import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

enum class UtilityPanel {
    NONE,
    WALLET,
    CLIPBOARD,
    THEME,
    SETTINGS,
}

data class KeyboardPanelState(
    val activePanel: UtilityPanel = UtilityPanel.NONE,
    val walletSnapshot: WalletSessionSnapshot = WalletSessionSnapshot(),
    val clipboardPreview: String = "Clipboard empty",
    val consolidationFeeQuote: ConsolidationFeeQuote = ConsolidationFeeModel.quote(1),
)

class SeekerKeyboardView(
    context: Context,
) : LinearLayout(context) {
    private val rowSpecs = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("shift", "z", "x", "c", "v", "b", "n", "m", "⌫"),
    )

    private var uppercase = false

    init {
        orientation = VERTICAL
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }

    fun render(
        settings: KeyboardSettings,
        panelState: KeyboardPanelState,
        onKeyPress: (String) -> Unit,
        onUtilityPress: (String) -> Unit,
    ) {
        removeAllViews()
        setBackgroundColor(backgroundColor(settings.theme))

        addView(buildUtilityStrip(settings, panelState.activePanel, onUtilityPress))
        if (panelState.activePanel != UtilityPanel.NONE) {
            addView(buildPanel(settings, panelState, onUtilityPress))
        }

        if (settings.showNumberRow) {
            addView(buildRow(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"), settings, onKeyPress))
        }

        rowSpecs.forEach { row ->
            addView(buildRow(row, settings, onKeyPress))
        }

        val bottomRow = mutableListOf("123", ",")
        if (settings.showWalletKey) {
            bottomRow += "wallet"
        }
        bottomRow += listOf("space", ".", "enter")
        addView(buildRow(bottomRow, settings, onKeyPress))
    }

    private fun buildUtilityStrip(
        settings: KeyboardSettings,
        activePanel: UtilityPanel,
        onUtilityPress: (String) -> Unit,
    ): View {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
            }
            addView(buildUtilityButton("wallet", activePanel == UtilityPanel.WALLET, settings, onUtilityPress))
            addView(buildUtilityButton("clipboard", activePanel == UtilityPanel.CLIPBOARD, settings, onUtilityPress))
            addView(buildUtilityButton("theme", activePanel == UtilityPanel.THEME, settings, onUtilityPress))
            addView(buildUtilityButton("settings", activePanel == UtilityPanel.SETTINGS, settings, onUtilityPress))
        }
    }

    private fun buildUtilityButton(
        label: String,
        active: Boolean,
        settings: KeyboardSettings,
        onUtilityPress: (String) -> Unit,
    ): View {
        return Button(context).apply {
            text = label
            isAllCaps = false
            setTextColor(foregroundColor(settings.theme))
            setBackgroundColor(
                Color.parseColor(
                    if (active) accentColor(settings.theme) else mutedUtilityColor(settings.theme)
                )
            )
            layoutParams = LayoutParams(0, dp(36), 1f).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            }
            setOnClickListener { onUtilityPress("panel:$label") }
        }
    }

    private fun buildPanel(
        settings: KeyboardSettings,
        panelState: KeyboardPanelState,
        onUtilityPress: (String) -> Unit,
    ): View {
        val card = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.parseColor(panelColor(settings.theme)))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        }
        when (panelState.activePanel) {
            UtilityPanel.WALLET -> {
                card.addView(panelTitle("Wallet"))
                card.addView(panelText("Session: ${panelState.walletSnapshot.walletAddress?.let(::shortAddress) ?: "disconnected"}"))
                card.addView(panelText("Cluster: ${panelState.walletSnapshot.clusterName.lowercase()}"))
                card.addView(panelText(if (panelState.walletSnapshot.authTokenPresent) "Auth token cached" else "No cached session token"))
                card.addView(panelText("Native stake accounts: ${panelState.walletSnapshot.nativeStakeAccountCount}"))
                card.addView(panelText("Consolidation preview: ${panelState.consolidationFeeQuote.sourceCount} sources"))
                card.addView(panelText("Fee carry: ${panelState.consolidationFeeQuote.perSourceFeeInSkr} SKR/source, cap ${panelState.consolidationFeeQuote.capInSkr} SKR"))
                card.addView(panelText("Current consolidation fee: ${panelState.consolidationFeeQuote.feeInSkr} SKR"))
                card.addView(panelActions(settings, listOf("connect", "stake", "accounts", "send"), onUtilityPress))
                card.addView(panelActions(settings, listOf("sources_down", "sources_up", "consolidate"), onUtilityPress))
            }
            UtilityPanel.CLIPBOARD -> {
                card.addView(panelTitle("Clipboard"))
                card.addView(panelText(panelState.clipboardPreview))
                card.addView(panelActions(settings, listOf("paste", "clear"), onUtilityPress))
            }
            UtilityPanel.THEME -> {
                card.addView(panelTitle("Theme"))
                card.addView(panelText("Theme: ${settings.theme.label}"))
                card.addView(panelText("Height: ${settings.keyHeightDp}dp"))
                card.addView(panelActions(settings, listOf("cycle_theme", "height_up", "height_down", "toggle_number"), onUtilityPress))
            }
            UtilityPanel.SETTINGS -> {
                card.addView(panelTitle("Settings"))
                card.addView(panelText("Open the companion settings app for IME enablement and advanced options."))
                card.addView(panelActions(settings, listOf("open_settings", "switch_ime"), onUtilityPress))
            }
            UtilityPanel.NONE -> Unit
        }
        return card
    }

    private fun panelTitle(text: String): View {
        return TextView(context).apply {
            this.text = text
            textSize = 16f
            setTextColor(Color.WHITE)
        }
    }

    private fun panelText(text: String): View {
        return TextView(context).apply {
            this.text = text
            textSize = 13f
            setTextColor(Color.parseColor("#F5F5F5"))
            setPadding(0, dp(4), 0, 0)
        }
    }

    private fun panelActions(
        settings: KeyboardSettings,
        actions: List<String>,
        onUtilityPress: (String) -> Unit,
    ): View {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.START
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
            actions.forEach { action ->
                addView(
                    Button(context).apply {
                        text = action.replace('_', ' ')
                        isAllCaps = false
                        setTextColor(foregroundColor(settings.theme))
                        setBackgroundColor(Color.parseColor(mutedUtilityColor(settings.theme)))
                        textSize = 12f
                        layoutParams = LayoutParams(0, dp(34), 1f).apply {
                            marginStart = dp(3)
                            marginEnd = dp(3)
                        }
                        setOnClickListener { onUtilityPress("action:$action") }
                    }
                )
            }
        }
    }

    private fun buildRow(
        labels: List<String>,
        settings: KeyboardSettings,
        onKeyPress: (String) -> Unit,
    ): View {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
        }

        labels.forEach { label ->
            row.addView(
                Button(context).apply {
                    text = displayLabel(label)
                    isAllCaps = false
                    setTextColor(foregroundColor(settings.theme))
                    setBackgroundColor(keyColor(settings.theme, label))
                    textSize = 16f
                    minHeight = 0
                    minimumHeight = 0
                    layoutParams = LayoutParams(0, dp(settings.keyHeightDp), keyWeight(label)).apply {
                        marginStart = dp(3)
                        marginEnd = dp(3)
                    }
                    setOnClickListener {
                        if (label == "shift") {
                            uppercase = !uppercase
                            onUtilityPress("action:redraw")
                        } else {
                            onKeyPress(resolveKeyValue(label))
                        }
                    }
                }
            )
        }
        return row
    }

    fun clipboardPreviewFrom(label: CharSequence?, description: ClipDescription?): String {
        if (label.isNullOrBlank()) return "Clipboard empty"
        val type = when {
            description?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true -> "text"
            description?.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML) == true -> "html"
            else -> "content"
        }
        return "$type: ${label.take(48)}"
    }

    private fun displayLabel(label: String): String {
        return when (label) {
            "space" -> "space"
            "wallet" -> "wallet"
            "enter" -> "enter"
            "shift" -> if (uppercase) "SHIFT" else "shift"
            else -> resolveKeyValue(label)
        }
    }

    private fun resolveKeyValue(label: String): String {
        return when (label) {
            "⌫", "shift", "space", "wallet", "enter", "123" -> label
            else -> if (uppercase) label.uppercase() else label
        }
    }

    private fun keyWeight(label: String): Float {
        return when (label) {
            "space" -> 4.2f
            "wallet" -> 1.6f
            "enter", "shift", "123", "⌫" -> 1.4f
            else -> 1f
        }
    }

    private fun backgroundColor(theme: KeyboardTheme): Int {
        return when (theme) {
            KeyboardTheme.SAND -> Color.parseColor("#F1E3D3")
            KeyboardTheme.TEAL -> Color.parseColor("#D8F4EE")
            KeyboardTheme.GRAPHITE -> Color.parseColor("#20272B")
        }
    }

    private fun keyColor(theme: KeyboardTheme, label: String): Int {
        return Color.parseColor(if (label == "wallet") accentColor(theme) else neutralKeyColor(theme))
    }

    private fun foregroundColor(theme: KeyboardTheme): Int {
        return if (theme == KeyboardTheme.GRAPHITE) Color.WHITE else Color.BLACK
    }

    private fun accentColor(theme: KeyboardTheme): String {
        return when (theme) {
            KeyboardTheme.SAND -> "#C16A39"
            KeyboardTheme.TEAL -> "#12786B"
            KeyboardTheme.GRAPHITE -> "#5C6F52"
        }
    }

    private fun neutralKeyColor(theme: KeyboardTheme): String {
        return when (theme) {
            KeyboardTheme.SAND -> "#FFF8F2"
            KeyboardTheme.TEAL -> "#F3FFFC"
            KeyboardTheme.GRAPHITE -> "#344047"
        }
    }

    private fun mutedUtilityColor(theme: KeyboardTheme): String {
        return when (theme) {
            KeyboardTheme.SAND -> "#E8C8B0"
            KeyboardTheme.TEAL -> "#BDE8DF"
            KeyboardTheme.GRAPHITE -> "#415158"
        }
    }

    private fun panelColor(theme: KeyboardTheme): String {
        return when (theme) {
            KeyboardTheme.SAND -> "#8A4A26"
            KeyboardTheme.TEAL -> "#0F5C53"
            KeyboardTheme.GRAPHITE -> "#263137"
        }
    }

    private fun shortAddress(value: String): String {
        return if (value.length <= 12) value else "${value.take(6)}...${value.takeLast(6)}"
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics,
        ).toInt()
    }
}
