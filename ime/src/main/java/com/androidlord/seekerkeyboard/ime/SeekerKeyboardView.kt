package com.androidlord.seekerkeyboard.ime

import android.content.ClipDescription
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

enum class UtilityPanel {
    NONE,
    WALLET,
    CLIPBOARD,
    THEME,
    SETTINGS,
}

enum class WalletDrawerTab {
    OVERVIEW,
    STAKE,
    ACCOUNTS,
}

enum class KeyboardLayer {
    ALPHA,
    SYMBOLS,
    MORE_SYMBOLS,
}

enum class ShiftState {
    OFF,
    ONCE,
    CAPS,
}

data class KeyboardPanelState(
    val activePanel: UtilityPanel = UtilityPanel.NONE,
    val walletTab: WalletDrawerTab = WalletDrawerTab.OVERVIEW,
    val walletSnapshot: WalletSessionSnapshot = WalletSessionSnapshot(),
    val clipboardPreview: String = "Clipboard empty",
    val clipboardRaw: String = "",
    val clipboardHistory: List<String> = emptyList(),
    val drafts: WalletActionDrafts = WalletActionDrafts(),
    val selectedStakeIndex: Int = 0,
    val consolidationFeeQuote: ConsolidationFeeQuote = ConsolidationFeeModel.quote(1),
    val keyboardLayer: KeyboardLayer = KeyboardLayer.ALPHA,
    val shiftState: ShiftState = ShiftState.OFF,
)

class SeekerKeyboardView(
    context: Context,
) : LinearLayout(context) {
    private val alphaRowSpecs = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("shift", "z", "x", "c", "v", "b", "n", "m", "⌫"),
    )
    private val symbolRowSpecs = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("@", "#", "$", "&", "-", "+", "(", ")", "/", "\""),
        listOf("shift", "*", "'", ":", ";", "!", "?", "%", "⌫"),
    )
    private val moreSymbolRowSpecs = listOf(
        listOf("~", "`", "|", "•", "√", "π", "÷", "×", "{", "}"),
        listOf("£", "€", "¥", "^", "_", "=", "[", "]", "<", ">"),
        listOf("shift", "\\", "©", "®", "°", "…", "¿", "¡", "⌫"),
    )
    private val longPressMap = mapOf(
        "a" to "á",
        "c" to "ç",
        "e" to "é",
        "i" to "í",
        "o" to "ó",
        "u" to "ú",
        "n" to "ñ",
        "y" to "ý",
        "z" to "ž",
        "l" to "ł",
        "s" to "$",
        "!" to "¡",
        "?" to "¿",
        "\"" to "'",
        "." to ",",
        "," to ";",
    )

    private var cachedWallpaperUri: String? = null
    private var cachedWallpaper: BitmapDrawable? = null

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
        applyBackground(settings)

        addView(buildUtilityStrip(settings, panelState.activePanel, onUtilityPress))
        if (panelState.activePanel != UtilityPanel.NONE) {
            addView(buildPanel(settings, panelState, onUtilityPress))
        }

        val rowSpecs = when (panelState.keyboardLayer) {
            KeyboardLayer.ALPHA -> alphaRowSpecs
            KeyboardLayer.SYMBOLS -> symbolRowSpecs
            KeyboardLayer.MORE_SYMBOLS -> moreSymbolRowSpecs
        }
        if (settings.showNumberRow && panelState.keyboardLayer == KeyboardLayer.ALPHA) {
            addView(buildRow(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"), settings, panelState, onKeyPress, onUtilityPress))
        }

        rowSpecs.forEach { row ->
            addView(buildRow(row, settings, panelState, onKeyPress, onUtilityPress))
        }

        val bottomRow = mutableListOf("123", ",")
        if (settings.showWalletKey) bottomRow += "wallet"
        bottomRow += listOf("space", ".", "enter")
        addView(buildRow(bottomRow, settings, panelState, onKeyPress, onUtilityPress))
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
            setTextColor(foregroundColor(settings))
            setBackgroundColor(
                parseColorOrFallback(
                    if (active) settings.accentHex else settings.utilityHex,
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
            setPadding(dp(12), dp(10), dp(12), dp(12))
            setBackgroundColor(parseColorOrFallback(settings.panelHex, panelColor(settings.theme)))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        }
        when (panelState.activePanel) {
            UtilityPanel.WALLET -> renderWalletPanel(card, settings, panelState, onUtilityPress)
            UtilityPanel.CLIPBOARD -> renderClipboardPanel(card, settings, panelState, onUtilityPress)
            UtilityPanel.THEME -> {
                card.addView(panelTitle("Theme", settings))
                card.addView(statusChip("theme ${settings.theme.label.lowercase()} · ${settings.keyHeightDp}dp", settings))
                card.addView(panelActions(settings, listOf("cycle_theme", "height_up", "height_down", "toggle_number"), onUtilityPress))
            }
            UtilityPanel.SETTINGS -> {
                card.addView(panelTitle("Settings", settings))
                card.addView(statusChip("onboarding + advanced config", settings))
                card.addView(panelActions(settings, listOf("open_settings", "switch_ime"), onUtilityPress))
            }
            UtilityPanel.NONE -> Unit
        }
        return card
    }

    private fun renderWalletPanel(
        card: LinearLayout,
        settings: KeyboardSettings,
        panelState: KeyboardPanelState,
        onUtilityPress: (String) -> Unit,
    ) {
        card.addView(panelTitle("Wallet", settings))
        card.addView(panelActions(settings, listOf("wallet_overview", "wallet_stake", "wallet_accounts"), onUtilityPress))
        card.addView(statusChip(compactStatus(panelState.walletSnapshot.statusMessage), settings))
        card.addView(panelMeta("session", panelState.walletSnapshot.walletAddress?.let(::shortAddress) ?: "disconnected", settings))
        card.addView(panelMeta("cluster", panelState.walletSnapshot.clusterName.lowercase(), settings))

        when (panelState.walletTab) {
            WalletDrawerTab.OVERVIEW -> {
                card.addView(panelMeta("balance", panelState.walletSnapshot.totalBalanceUsd, settings))
                card.addView(panelMeta("skr", "${panelState.walletSnapshot.skrStakedAmount} staked · ${panelState.walletSnapshot.skrWithdrawableAmount} ready", settings))
                card.addView(panelMeta("send", "${panelState.drafts.sendAmountSol} SOL -> ${panelState.clipboardRaw.ifBlank { "clipboard target" }.let { if (it.length > 18) shortAddress(it) else it }}", settings))
                card.addView(panelActions(settings, listOf("connect", "refresh", "disconnect"), onUtilityPress))
                card.addView(panelActions(settings, listOf("send_down", "send_up", "send"), onUtilityPress))
            }
            WalletDrawerTab.STAKE -> {
                val selectedStake = panelState.walletSnapshot.stakeAccountsPreview.getOrNull(panelState.selectedStakeIndex)
                card.addView(panelMeta("validator", "Solana Mobile", settings))
                card.addView(panelMeta("selected", selectedStake?.let { "${shortAddress(it.pubkey)} · ${formatLamports(it.lamports)}" } ?: "none", settings))
                card.addView(panelMeta("skr", "${panelState.drafts.skrStakeAmount} stake · ${panelState.drafts.skrUnstakeAmount} unstake", settings))
                card.addView(panelActions(settings, listOf("skr_stake_down", "skr_stake_up", "skr_stake"), onUtilityPress))
                card.addView(panelActions(settings, listOf("skr_unstake_down", "skr_unstake_up", "skr_unstake"), onUtilityPress))
                card.addView(panelActions(settings, listOf("skr_withdraw", "stake_prev", "stake_next"), onUtilityPress))
                card.addView(panelActions(settings, listOf("native_delegate", "native_deactivate", "native_withdraw"), onUtilityPress))
            }
            WalletDrawerTab.ACCOUNTS -> {
                val selectedStake = panelState.walletSnapshot.stakeAccountsPreview.getOrNull(panelState.selectedStakeIndex)
                card.addView(panelMeta("selected", selectedStake?.let { "${shortAddress(it.pubkey)} · ${it.stakeState}" } ?: "none", settings))
                card.addView(panelMeta("merge", "${panelState.consolidationFeeQuote.sourceCount} src · ${panelState.consolidationFeeQuote.feeInSkr} SKR carry", settings))
                card.addView(panelMeta("compat", consolidationHint(panelState), settings))
                panelState.walletSnapshot.stakeAccountsPreview.take(4).forEachIndexed { index, stake ->
                    val prefix = if (index == panelState.selectedStakeIndex) ">" else "•"
                    card.addView(panelText("$prefix ${shortAddress(stake.pubkey)} · ${formatLamports(stake.lamports)}", settings))
                }
                card.addView(panelActions(settings, listOf("stake_prev", "stake_next", "refresh"), onUtilityPress))
                card.addView(panelActions(settings, listOf("sources_down", "sources_up", "consolidate"), onUtilityPress))
            }
        }
    }

    private fun renderClipboardPanel(
        card: LinearLayout,
        settings: KeyboardSettings,
        panelState: KeyboardPanelState,
        onUtilityPress: (String) -> Unit,
    ) {
        card.addView(panelTitle("Clipboard", settings))
        card.addView(statusChip(compactStatus(panelState.clipboardPreview), settings))
        if (panelState.clipboardHistory.isEmpty()) {
            card.addView(panelText("No history yet", settings))
        } else {
            panelState.clipboardHistory.take(4).forEachIndexed { index, item ->
                card.addView(panelText("${index + 1}. ${item.take(36)}", settings))
            }
        }
        card.addView(panelActions(settings, listOf("paste", "hist_1", "hist_2"), onUtilityPress))
        card.addView(panelActions(settings, listOf("hist_3", "hist_4", "clear"), onUtilityPress))
    }

    private fun panelTitle(text: String, settings: KeyboardSettings): View {
        return TextView(context).apply {
            this.text = text
            textSize = 16f
            setTextColor(foregroundColor(settings))
        }
    }

    private fun statusChip(text: String, settings: KeyboardSettings): View {
        return TextView(context).apply {
            this.text = text
            textSize = 12f
            setTextColor(foregroundColor(settings))
            setBackgroundColor(parseColorOrFallback(settings.utilityHex, mutedUtilityColor(settings.theme)))
            setPadding(dp(8), dp(5), dp(8), dp(5))
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
        }
    }

    private fun panelMeta(label: String, value: String, settings: KeyboardSettings): View {
        return panelText("$label: $value", settings)
    }

    private fun panelText(text: String, settings: KeyboardSettings): View {
        return TextView(context).apply {
            this.text = text
            textSize = 13f
            setTextColor(foregroundColor(settings))
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
                        setTextColor(foregroundColor(settings))
                        setBackgroundColor(parseColorOrFallback(settings.utilityHex, mutedUtilityColor(settings.theme)))
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
        panelState: KeyboardPanelState,
        onKeyPress: (String) -> Unit,
        onUtilityPress: (String) -> Unit,
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
                    text = displayLabel(label, panelState)
                    isAllCaps = false
                    setTextColor(foregroundColor(settings))
                    setBackgroundColor(keyColor(settings, label))
                    textSize = 16f
                    minHeight = 0
                    minimumHeight = 0
                    layoutParams = LayoutParams(0, dp(settings.keyHeightDp), keyWeight(label)).apply {
                        marginStart = dp(3)
                        marginEnd = dp(3)
                    }
                    setOnClickListener {
                        when (label) {
                            "shift" -> onUtilityPress("action:cycle_shift")
                            "123" -> onUtilityPress("action:toggle_symbols")
                            else -> onKeyPress(resolveKeyValue(label, panelState))
                        }
                    }
                    if (longPressMap.containsKey(label)) {
                        setOnLongClickListener {
                            onKeyPress(longPressMap.getValue(label))
                            true
                        }
                    }
                }
            )
        }
        return row
    }

    private fun displayLabel(label: String, panelState: KeyboardPanelState): String {
        return when (label) {
            "space" -> "space"
            "wallet" -> "wallet"
            "enter" -> "enter"
            "123" -> when (panelState.keyboardLayer) {
                KeyboardLayer.ALPHA -> "123"
                KeyboardLayer.SYMBOLS -> "#+="
                KeyboardLayer.MORE_SYMBOLS -> "ABC"
            }
            "shift" -> when (panelState.shiftState) {
                ShiftState.OFF -> "shift"
                ShiftState.ONCE -> "Shift"
                ShiftState.CAPS -> "CAPS"
            }
            else -> resolveKeyValue(label, panelState)
        }
    }

    private fun resolveKeyValue(label: String, panelState: KeyboardPanelState): String {
        return when (label) {
            "⌫", "shift", "space", "wallet", "enter", "123" -> label
            else -> {
                val shouldUppercase = panelState.keyboardLayer == KeyboardLayer.ALPHA &&
                    panelState.shiftState != ShiftState.OFF &&
                    label.length == 1 &&
                    label[0].isLetter()
                if (shouldUppercase) label.uppercase() else label
            }
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

    private fun compactStatus(status: String): String {
        return status.substringBefore(".").take(56).ifBlank { "ready" }
    }

    private fun consolidationHint(panelState: KeyboardPanelState): String {
        val destination = panelState.walletSnapshot.stakeAccountsPreview.getOrNull(panelState.selectedStakeIndex)
            ?: return "pick destination"
        val nonDestination = panelState.walletSnapshot.stakeAccountsPreview.filter { it.pubkey != destination.pubkey }
        if (nonDestination.isEmpty()) return "need at least one source"

        val likely = nonDestination.count {
            it.delegationVote != null &&
                it.delegationVote == destination.delegationVote &&
                !isInactiveLike(it.stakeState) &&
                !isInactiveLike(destination.stakeState)
        }
        val risky = nonDestination.size - likely
        return when {
            likely > 0 && risky == 0 -> "$likely likely mergeable"
            likely > 0 -> "$likely likely · $risky risky"
            else -> "chain will decide · preview risky"
        }
    }

    private fun isInactiveLike(state: String): Boolean {
        return state == "inactive" || state == "undelegated" || state == "initialized"
    }

    private fun applyBackground(settings: KeyboardSettings) {
        val fallback = parseColorOrFallback(settings.backgroundHex, backgroundColor(settings.theme))
        setBackgroundColor(fallback)
        background = null
        if (settings.wallpaperUri.isBlank()) return
        if (cachedWallpaperUri != settings.wallpaperUri) {
            cachedWallpaperUri = settings.wallpaperUri
            cachedWallpaper = runCatching {
                context.contentResolver.openInputStream(Uri.parse(settings.wallpaperUri))?.use { stream ->
                    BitmapDrawable(resources, android.graphics.BitmapFactory.decodeStream(stream))
                }
            }.getOrNull()
        }
        cachedWallpaper?.let {
            it.alpha = 92
            background = it
        }
    }

    private fun backgroundColor(theme: KeyboardTheme): String {
        return when (theme) {
            KeyboardTheme.SAND -> "#F1E3D3"
            KeyboardTheme.TEAL -> "#D8F4EE"
            KeyboardTheme.GRAPHITE -> "#20272B"
        }
    }

    private fun keyColor(settings: KeyboardSettings, label: String): Int {
        val fallback = when (label) {
            "wallet" -> accentColor(settings.theme)
            "enter", "shift", "123", "⌫" -> auxiliaryKeyColor(settings.theme)
            else -> neutralKeyColor(settings.theme)
        }
        val configured = when (label) {
            "wallet" -> settings.accentHex
            "enter", "shift", "123", "⌫" -> settings.auxiliaryKeyHex
            else -> settings.keyHex
        }
        return parseColorOrFallback(configured, fallback)
    }

    private fun foregroundColor(settings: KeyboardSettings): Int {
        return parseColorOrFallback(
            settings.textHex,
            if (settings.theme == KeyboardTheme.GRAPHITE) "#FFFFFF" else "#111111"
        )
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

    private fun auxiliaryKeyColor(theme: KeyboardTheme): String {
        return when (theme) {
            KeyboardTheme.SAND -> "#E5B289"
            KeyboardTheme.TEAL -> "#8ED1C4"
            KeyboardTheme.GRAPHITE -> "#58656C"
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

    private fun parseColorOrFallback(configured: String, fallback: String): Int {
        return runCatching { Color.parseColor(configured.ifBlank { fallback }) }
            .getOrElse { Color.parseColor(fallback) }
    }

    private fun shortAddress(value: String): String {
        return if (value.length <= 12) value else "${value.take(6)}...${value.takeLast(6)}"
    }

    private fun formatLamports(lamports: Long): String {
        return String.format(Locale.US, "%.3f SOL", lamports / 1_000_000_000.0)
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics,
        ).toInt()
    }
}
