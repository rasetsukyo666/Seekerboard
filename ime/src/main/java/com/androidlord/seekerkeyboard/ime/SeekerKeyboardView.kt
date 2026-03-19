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

data class KeyboardPanelState(
    val activePanel: UtilityPanel = UtilityPanel.NONE,
    val walletTab: WalletDrawerTab = WalletDrawerTab.OVERVIEW,
    val walletSnapshot: WalletSessionSnapshot = WalletSessionSnapshot(),
    val clipboardPreview: String = "Clipboard empty",
    val clipboardRaw: String = "",
    val drafts: WalletActionDrafts = WalletActionDrafts(),
    val selectedStakeIndex: Int = 0,
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

        if (settings.showNumberRow) {
            addView(buildRow(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"), settings, onKeyPress, onUtilityPress))
        }

        rowSpecs.forEach { row ->
            addView(buildRow(row, settings, onKeyPress, onUtilityPress))
        }

        val bottomRow = mutableListOf("123", ",")
        if (settings.showWalletKey) {
            bottomRow += "wallet"
        }
        bottomRow += listOf("space", ".", "enter")
        addView(buildRow(bottomRow, settings, onKeyPress, onUtilityPress))
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
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(parseColorOrFallback(settings.panelHex, panelColor(settings.theme)))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        }
        when (panelState.activePanel) {
            UtilityPanel.WALLET -> renderWalletPanel(card, settings, panelState, onUtilityPress)
            UtilityPanel.CLIPBOARD -> {
                card.addView(panelTitle("Clipboard", settings))
                card.addView(panelText(panelState.clipboardPreview, settings))
                card.addView(panelActions(settings, listOf("paste", "clear"), onUtilityPress))
            }
            UtilityPanel.THEME -> {
                card.addView(panelTitle("Theme", settings))
                card.addView(panelText("Theme: ${settings.theme.label}", settings))
                card.addView(panelText("Height: ${settings.keyHeightDp}dp", settings))
                card.addView(panelText("Wallpaper: ${if (settings.wallpaperUri.isBlank()) "none" else "custom"}", settings))
                card.addView(panelText("Surface colors can be edited in settings with hex values.", settings))
                card.addView(panelActions(settings, listOf("cycle_theme", "height_up", "height_down", "toggle_number"), onUtilityPress))
            }
            UtilityPanel.SETTINGS -> {
                card.addView(panelTitle("Settings", settings))
                card.addView(panelText("Open the companion settings app for IME enablement and advanced theme/wallpaper options.", settings))
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
        card.addView(panelText(panelState.walletSnapshot.statusMessage, settings))
        card.addView(panelText("Session: ${panelState.walletSnapshot.walletAddress?.let(::shortAddress) ?: "disconnected"}", settings))
        card.addView(panelText("Cluster: ${panelState.walletSnapshot.clusterName.lowercase()}", settings))
        card.addView(panelText(if (panelState.walletSnapshot.authTokenPresent) "Auth token cached" else "No cached session token", settings))

        when (panelState.walletTab) {
            WalletDrawerTab.OVERVIEW -> {
                card.addView(panelText("Balance: ${panelState.walletSnapshot.totalBalanceUsd}", settings))
                card.addView(panelText("SKR: ${panelState.walletSnapshot.skrApyLabel} · staked ${panelState.walletSnapshot.skrStakedAmount} · withdrawable ${panelState.walletSnapshot.skrWithdrawableAmount}", settings))
                card.addView(panelText("Native stake accounts: ${panelState.walletSnapshot.nativeStakeAccountCount}", settings))
                card.addView(panelText("Clipboard target: ${panelState.clipboardRaw.ifBlank { "copy a wallet address to use send" }.let { if (it.length > 20) shortAddress(it) else it }}", settings))
                card.addView(panelText("Send preset: ${panelState.drafts.sendAmountSol} SOL", settings))
                card.addView(panelActions(settings, listOf("connect", "refresh", "disconnect"), onUtilityPress))
                card.addView(panelActions(settings, listOf("send_down", "send_up", "send"), onUtilityPress))
            }
            WalletDrawerTab.STAKE -> {
                val selectedStake = panelState.walletSnapshot.stakeAccountsPreview.getOrNull(panelState.selectedStakeIndex)
                card.addView(panelText("Validator lane: Solana Mobile validator", settings))
                card.addView(panelText("SKR stake preset: ${panelState.drafts.skrStakeAmount} SKR", settings))
                card.addView(panelText("SKR unstake preset: ${panelState.drafts.skrUnstakeAmount} SKR", settings))
                card.addView(panelText("SKR APY: ${panelState.walletSnapshot.skrApyLabel}", settings))
                card.addView(panelText("SKR staked: ${panelState.walletSnapshot.skrStakedAmount}", settings))
                card.addView(panelText("SKR withdrawable: ${panelState.walletSnapshot.skrWithdrawableAmount}", settings))
                card.addView(panelText("Selected stake: ${selectedStake?.let { "${shortAddress(it.pubkey)} · ${formatLamports(it.lamports)} · ${it.stakeState}" } ?: "none"}", settings))
                card.addView(panelActions(settings, listOf("skr_stake_down", "skr_stake_up", "skr_stake"), onUtilityPress))
                card.addView(panelActions(settings, listOf("skr_unstake_down", "skr_unstake_up", "skr_unstake"), onUtilityPress))
                card.addView(panelActions(settings, listOf("skr_withdraw", "stake_prev", "stake_next"), onUtilityPress))
                card.addView(panelActions(settings, listOf("native_delegate", "native_deactivate", "native_withdraw"), onUtilityPress))
            }
            WalletDrawerTab.ACCOUNTS -> {
                card.addView(panelText("Eligible consolidation sources: ${panelState.walletSnapshot.eligibleConsolidationSources}", settings))
                card.addView(panelText("Consolidation preview: ${panelState.consolidationFeeQuote.sourceCount} sources", settings))
                card.addView(panelText("Fee carry: ${panelState.consolidationFeeQuote.perSourceFeeInSkr} SKR/source, cap ${panelState.consolidationFeeQuote.capInSkr} SKR", settings))
                card.addView(panelText("Current consolidation fee: ${panelState.consolidationFeeQuote.feeInSkr} SKR", settings))
                panelState.walletSnapshot.stakeAccountsPreview.take(5).forEachIndexed { index, stake ->
                    val prefix = if (index == panelState.selectedStakeIndex) ">" else "•"
                    card.addView(panelText("$prefix ${shortAddress(stake.pubkey)} · ${formatLamports(stake.lamports)} · ${stake.stakeState}", settings))
                }
                card.addView(panelActions(settings, listOf("stake_prev", "stake_next", "refresh"), onUtilityPress))
                card.addView(panelActions(settings, listOf("sources_down", "sources_up", "consolidate"), onUtilityPress))
            }
        }
    }

    private fun panelTitle(text: String, settings: KeyboardSettings): View {
        return TextView(context).apply {
            this.text = text
            textSize = 16f
            setTextColor(foregroundColor(settings))
        }
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
                    text = displayLabel(label)
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

        cachedWallpaper?.let { drawable ->
            drawable.alpha = 92
            background = drawable
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
