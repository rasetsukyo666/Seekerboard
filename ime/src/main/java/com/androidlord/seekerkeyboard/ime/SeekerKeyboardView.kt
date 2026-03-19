package com.androidlord.seekerkeyboard.ime

import android.content.ClipDescription
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs

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
    val clipboardPinned: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val drafts: WalletActionDrafts = WalletActionDrafts(),
    val selectedStakeIndex: Int = 0,
    val consolidationFeeQuote: ConsolidationFeeQuote = ConsolidationFeeModel.quote(1),
    val keyboardLayer: KeyboardLayer = KeyboardLayer.ALPHA,
    val shiftState: ShiftState = ShiftState.OFF,
    val ephemeralHint: String = "",
    val alternateOptions: List<String> = emptyList(),
    val alternateAnchorRatio: Float = 0.5f,
)

private data class GlideKeyTarget(
    val label: String,
    val button: Button,
)

class SeekerKeyboardView(
    context: Context,
) : LinearLayout(context) {
    private val glideTargets = mutableListOf<GlideKeyTarget>()
    private val glidePath = mutableListOf<String>()

    private var cachedWallpaperUri: String? = null
    private var cachedWallpaper: BitmapDrawable? = null
    private var cachedFontUri: String? = null
    private var cachedTypeface: Typeface? = null
    private var glideActive = false

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
        glideTargets.clear()
        applyBackground(settings)

        if (panelState.alternateOptions.isNotEmpty()) {
            addView(buildAlternatesStrip(panelState.alternateOptions, panelState.alternateAnchorRatio, settings, onUtilityPress))
        }
        if (panelState.ephemeralHint.isNotBlank()) {
            addView(buildHintStrip(panelState.ephemeralHint, settings))
        }
        if (settings.suggestionsEnabled && panelState.suggestions.isNotEmpty()) {
            addView(buildSuggestionStrip(panelState.suggestions, settings, onUtilityPress))
        }
        addView(buildUtilityStrip(settings, panelState.activePanel, onUtilityPress))
        if (panelState.activePanel != UtilityPanel.NONE) {
            addView(buildPanel(settings, panelState, onUtilityPress))
        }

        val alphaRows = alphaRows(settings.language)
        val rowSpecs = when (panelState.keyboardLayer) {
            KeyboardLayer.ALPHA -> alphaRows
            KeyboardLayer.SYMBOLS -> symbolRows(settings.language)
            KeyboardLayer.MORE_SYMBOLS -> moreSymbolRows(settings.language)
        }
        if (settings.showNumberRow && panelState.keyboardLayer == KeyboardLayer.ALPHA) {
            addView(buildRow(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"), settings, panelState, 0, onKeyPress, onUtilityPress))
        }

        rowSpecs.forEachIndexed { index, row ->
            addView(buildRow(row, settings, panelState, index, onKeyPress, onUtilityPress))
        }

        val bottomRow = mutableListOf("123", ",")
        if (settings.showWalletKey) bottomRow += "wallet"
        bottomRow += listOf("space", ".", "enter")
        addView(buildRow(bottomRow, settings, panelState, 3, onKeyPress, onUtilityPress))
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

    private fun alphaRows(language: KeyboardLanguage): List<List<String>> {
        return when (language) {
            KeyboardLanguage.ENGLISH -> listOf(
                listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
                listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
                listOf("shift", "z", "x", "c", "v", "b", "n", "m", "⌫"),
            )
            KeyboardLanguage.SPANISH -> listOf(
                listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
                listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "ñ"),
                listOf("shift", "z", "x", "c", "v", "b", "n", "m", "⌫"),
            )
            KeyboardLanguage.PORTUGUESE -> listOf(
                listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
                listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "ç"),
                listOf("shift", "z", "x", "c", "v", "b", "n", "m", "⌫"),
            )
        }
    }

    private fun symbolRows(language: KeyboardLanguage): List<List<String>> {
        return when (language) {
            KeyboardLanguage.ENGLISH -> listOf(
                listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                listOf("@", "#", "$", "&", "-", "+", "(", ")", "/", "\""),
                listOf("shift", "*", "'", ":", ";", "!", "?", "%", "⌫"),
            )
            KeyboardLanguage.SPANISH -> listOf(
                listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                listOf("@", "#", "€", "&", "-", "+", "(", ")", "/", "\""),
                listOf("shift", "*", "'", ":", ";", "¡", "¿", "%", "⌫"),
            )
            KeyboardLanguage.PORTUGUESE -> listOf(
                listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                listOf("@", "#", "$", "&", "-", "+", "(", ")", "/", "\""),
                listOf("shift", "*", "'", ":", ";", "!", "?", "º", "⌫"),
            )
        }
    }

    private fun moreSymbolRows(language: KeyboardLanguage): List<List<String>> {
        return when (language) {
            KeyboardLanguage.ENGLISH -> listOf(
                listOf("~", "`", "|", "•", "√", "π", "÷", "×", "{", "}"),
                listOf("£", "€", "¥", "^", "_", "=", "[", "]", "<", ">"),
                listOf("shift", "\\", "©", "®", "°", "…", "¿", "¡", "⌫"),
            )
            KeyboardLanguage.SPANISH -> listOf(
                listOf("~", "`", "|", "•", "√", "π", "÷", "×", "{", "}"),
                listOf("£", "€", "¥", "^", "_", "=", "[", "]", "«", "»"),
                listOf("shift", "\\", "©", "®", "°", "…", "!", "?", "⌫"),
            )
            KeyboardLanguage.PORTUGUESE -> listOf(
                listOf("~", "`", "|", "•", "√", "π", "÷", "×", "{", "}"),
                listOf("£", "€", "¥", "^", "_", "=", "[", "]", "<", ">"),
                listOf("shift", "\\", "©", "®", "°", "…", "ª", "§", "⌫"),
            )
        }
    }

    private fun alternatesFor(language: KeyboardLanguage): Map<String, List<String>> {
        val punctuation = when (language) {
            KeyboardLanguage.ENGLISH -> mapOf(
                "." to listOf(",", "…"),
                "," to listOf(";", ":"),
                "!" to listOf("?"),
                "?" to listOf("!"),
                "\"" to listOf("'"),
            )
            KeyboardLanguage.SPANISH -> mapOf(
                "a" to listOf("á", "à"),
                "e" to listOf("é", "è"),
                "i" to listOf("í", "ï"),
                "n" to listOf("ñ"),
                "o" to listOf("ó", "ò"),
                "u" to listOf("ú", "ü"),
                "." to listOf(",", "…"),
                "," to listOf(";", ":"),
                "!" to listOf("¡"),
                "?" to listOf("¿"),
                "\"" to listOf("«", "»", "'"),
            )
            KeyboardLanguage.PORTUGUESE -> mapOf(
                "a" to listOf("á", "à", "â", "ã"),
                "c" to listOf("ç"),
                "e" to listOf("é", "ê"),
                "i" to listOf("í"),
                "o" to listOf("ó", "ô", "õ"),
                "u" to listOf("ú"),
                "." to listOf(",", "…"),
                "," to listOf(";", ":"),
                "!" to listOf("?"),
                "?" to listOf("!"),
                "\"" to listOf("'"),
            )
        }
        val shared = mapOf(
            "a" to listOf("á", "à", "ä", "â"),
            "c" to listOf("ç", "ć"),
            "e" to listOf("é", "è", "ë", "ê"),
            "i" to listOf("í", "ì", "ï", "î"),
            "l" to listOf("ł"),
            "o" to listOf("ó", "ò", "ö", "ô"),
            "s" to listOf("$", "ś"),
            "u" to listOf("ú", "ù", "ü", "û"),
            "y" to listOf("ý"),
            "z" to listOf("ž", "ź"),
        )
        return shared + punctuation
    }

    private fun buildHintStrip(text: String, settings: KeyboardSettings): View {
        return TextView(context).apply {
            this.text = text
            textSize = 12f
            gravity = Gravity.CENTER
            typeface = resolvedTypeface(settings)
            setTextColor(foregroundColor(settings))
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = pillDrawable(parseColorOrFallback(settings.utilityHex, mutedUtilityColor(settings.theme)), dpFloat(12))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
            }
        }
    }

    private fun buildSuggestionStrip(
        suggestions: List<String>,
        settings: KeyboardSettings,
        onUtilityPress: (String) -> Unit,
    ): View {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
            }
            suggestions.forEach { suggestion ->
                addView(
                    Button(context).apply {
                        text = suggestion
                        isAllCaps = false
                        typeface = resolvedTypeface(settings)
                        setTextColor(foregroundColor(settings))
                        background = pillDrawable(parseColorOrFallback(settings.utilityHex, mutedUtilityColor(settings.theme)), dpFloat(cornerRadius(settings, false)))
                        layoutParams = LayoutParams(0, dp(36), 1f).apply {
                            marginStart = dp(3)
                            marginEnd = dp(3)
                        }
                        setOnClickListener { onUtilityPress("action:pick_suggestion:$suggestion") }
                    }
                )
            }
        }
    }

    private fun buildAlternatesStrip(
        options: List<String>,
        anchorRatio: Float,
        settings: KeyboardSettings,
        onUtilityPress: (String) -> Unit,
    ): View {
        val leftWeight = (anchorRatio.coerceIn(0.0f, 1.0f) * 10f).coerceAtLeast(1f)
        val rightWeight = ((1f - anchorRatio.coerceIn(0.0f, 1.0f)) * 10f).coerceAtLeast(1f)
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
            }
            addView(Space(context).apply {
                layoutParams = LayoutParams(0, 0, leftWeight)
            })
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = pillDrawable(parseColorOrFallback(settings.panelHex, panelColor(settings.theme)), dpFloat(18))
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                options.forEach { option ->
                    addView(
                        Button(context).apply {
                            text = option
                            isAllCaps = false
                            typeface = resolvedTypeface(settings)
                            setTextColor(foregroundColor(settings))
                            background = pillDrawable(parseColorOrFallback(settings.accentHex, accentColor(settings.theme)), dpFloat(16))
                            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dp(42)).apply {
                                marginStart = dp(3)
                                marginEnd = dp(3)
                            }
                            setOnClickListener { onUtilityPress("action:pick_alt:$option") }
                        }
                    )
                }
                addView(
                    Button(context).apply {
                        text = "x"
                        isAllCaps = false
                        typeface = resolvedTypeface(settings)
                        setTextColor(foregroundColor(settings))
                        background = pillDrawable(parseColorOrFallback(settings.utilityHex, mutedUtilityColor(settings.theme)), dpFloat(16))
                        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dp(42)).apply {
                            marginStart = dp(3)
                            marginEnd = dp(3)
                        }
                        setOnClickListener { onUtilityPress("action:clear_alts") }
                    }
                )
            })
            addView(Space(context).apply {
                layoutParams = LayoutParams(0, 0, rightWeight)
            })
        }
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
            typeface = resolvedTypeface(settings)
            setTextColor(foregroundColor(settings))
            background = pillDrawable(
                parseColorOrFallback(
                    if (active) settings.accentHex else settings.utilityHex,
                    if (active) accentColor(settings.theme) else mutedUtilityColor(settings.theme)
                ),
                dpFloat(14),
            )
            layoutParams = LayoutParams(0, utilityHeight(settings), 1f).apply {
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
            background = pillDrawable(parseColorOrFallback(settings.panelHex, panelColor(settings.theme)), dpFloat(18))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        }
        when (panelState.activePanel) {
            UtilityPanel.WALLET -> renderWalletPanel(card, settings, panelState, onUtilityPress)
            UtilityPanel.CLIPBOARD -> renderClipboardPanel(card, settings, panelState, onUtilityPress)
            UtilityPanel.THEME -> {
                card.addView(panelTitle("Theme", settings))
                card.addView(statusChip("${settings.theme.label.lowercase()} · ${settings.layoutMode.label.lowercase()} · ${settings.language.label.lowercase()}", settings))
                card.addView(panelActions(settings, listOf("cycle_theme", "cycle_layout", "cycle_language", "toggle_number"), onUtilityPress))
                card.addView(panelActions(settings, listOf("height_up", "height_down"), onUtilityPress))
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

    private fun renderWalletPanel(card: LinearLayout, settings: KeyboardSettings, panelState: KeyboardPanelState, onUtilityPress: (String) -> Unit) {
        card.addView(panelTitle("Wallet", settings))
        card.addView(panelActions(settings, listOf("wallet_overview", "wallet_stake", "wallet_accounts"), onUtilityPress))
        card.addView(statusChip(compactStatus(panelState.walletSnapshot.statusMessage), settings))
        card.addView(panelMeta("session", panelState.walletSnapshot.walletAddress?.let(::shortAddress) ?: "disconnected", settings))
        card.addView(panelMeta("cluster", panelState.walletSnapshot.clusterName.lowercase(), settings))
        when (panelState.walletTab) {
            WalletDrawerTab.OVERVIEW -> {
                card.addView(panelMeta("balance", panelState.walletSnapshot.totalBalanceUsd, settings))
                card.addView(panelMeta("skr", "${panelState.walletSnapshot.skrStakedAmount} staked · ${panelState.walletSnapshot.skrWithdrawableAmount} ready", settings))
                panelState.walletSnapshot.unifiedAccounts.take(3).forEach { account ->
                    card.addView(panelMeta(account.title.lowercase(), "${account.balanceLabel} · ${account.detailLabel}", settings))
                }
                card.addView(panelMeta("review", if (panelState.walletSnapshot.reviewRequired) "approval handoff active" else "direct", settings))
                card.addView(panelMeta("send", "${panelState.drafts.sendAmountSol} SOL -> ${panelState.clipboardRaw.ifBlank { "clipboard target" }.let { if (it.length > 18) shortAddress(it) else it }}", settings))
                card.addView(panelActions(settings, listOf("connect", "disconnect"), onUtilityPress))
                card.addView(panelActions(settings, listOf("send_less", "send_more", "send"), onUtilityPress))
            }
            WalletDrawerTab.STAKE -> {
                val selectedStake = panelState.walletSnapshot.stakeAccountsPreview.getOrNull(panelState.selectedStakeIndex)
                card.addView(panelMeta("validator", "Solana Mobile", settings))
                card.addView(panelMeta("selected", selectedStake?.let { "${shortAddress(it.pubkey)} · ${formatLamports(it.lamports)}" } ?: "none", settings))
                card.addView(panelMeta("skr", "${panelState.drafts.skrStakeAmount} stake · ${panelState.drafts.skrUnstakeAmount} unstake", settings))
                card.addView(panelActions(settings, listOf("skr_less", "skr_more", "skr_stake"), onUtilityPress))
                card.addView(panelActions(settings, listOf("skr_unless", "skr_unmore", "skr_unstake"), onUtilityPress))
                card.addView(panelActions(settings, listOf("skr_withdraw", "stake_prev", "stake_next"), onUtilityPress))
                card.addView(panelActions(settings, listOf("native_delegate", "native_deactivate", "native_withdraw"), onUtilityPress))
            }
            WalletDrawerTab.ACCOUNTS -> {
                val selectedStake = panelState.walletSnapshot.stakeAccountsPreview.getOrNull(panelState.selectedStakeIndex)
                card.addView(panelMeta("selected", selectedStake?.let { "${shortAddress(it.pubkey)} · ${it.stakeState}" } ?: "none", settings))
                card.addView(panelMeta("merge", "${panelState.consolidationFeeQuote.sourceCount} src · ${panelState.consolidationFeeQuote.feeInSkr} SKR carry", settings))
                card.addView(panelMeta("compat", consolidationHint(panelState), settings))
                panelState.walletSnapshot.unifiedAccounts.forEach { account ->
                    card.addView(panelText("${account.title}: ${account.balanceLabel} · ${account.detailLabel}", settings))
                }
                panelState.walletSnapshot.stakeAccountsPreview.take(4).forEachIndexed { index, stake ->
                    val prefix = if (index == panelState.selectedStakeIndex) ">" else "•"
                    card.addView(panelText("$prefix ${shortAddress(stake.pubkey)} · ${formatLamports(stake.lamports)}", settings))
                }
                card.addView(panelActions(settings, listOf("stake_prev", "stake_next"), onUtilityPress))
                card.addView(panelActions(settings, listOf("sources_down", "sources_up", "consolidate"), onUtilityPress))
            }
        }
    }

    private fun renderClipboardPanel(card: LinearLayout, settings: KeyboardSettings, panelState: KeyboardPanelState, onUtilityPress: (String) -> Unit) {
        card.addView(panelTitle("Clipboard", settings))
        card.addView(statusChip(compactStatus(panelState.clipboardPreview), settings))
        if (panelState.clipboardPinned.isNotEmpty()) {
            card.addView(panelText("Pinned", settings))
            panelState.clipboardPinned.take(3).forEachIndexed { index, item ->
                card.addView(panelText("★ ${index + 1}. ${item.take(36)}", settings))
            }
        }
        if (panelState.clipboardHistory.isEmpty()) {
            card.addView(panelText("No history yet", settings))
        } else {
            panelState.clipboardHistory.take(4).forEachIndexed { index, item ->
                card.addView(panelText("${index + 1}. ${item.take(36)}", settings))
            }
        }
        card.addView(panelActions(settings, listOf("paste", "pin_clip", "hist_1"), onUtilityPress))
        card.addView(panelActions(settings, listOf("hist_2", "hist_3", "hist_4"), onUtilityPress))
        card.addView(panelActions(settings, listOf("pin_1", "pin_2", "clear"), onUtilityPress))
    }

    private fun panelTitle(text: String, settings: KeyboardSettings): View = TextView(context).apply {
        this.text = text
        textSize = 16f
        typeface = resolvedTypeface(settings)
        setTextColor(foregroundColor(settings))
    }

    private fun statusChip(text: String, settings: KeyboardSettings): View = TextView(context).apply {
        this.text = text
        textSize = 12f
        typeface = resolvedTypeface(settings)
        setTextColor(foregroundColor(settings))
        background = pillDrawable(parseColorOrFallback(settings.utilityHex, mutedUtilityColor(settings.theme)), dpFloat(12))
        setPadding(dp(8), dp(5), dp(8), dp(5))
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
    }

    private fun panelMeta(label: String, value: String, settings: KeyboardSettings): View = panelText("$label: $value", settings)

    private fun panelText(text: String, settings: KeyboardSettings): View = TextView(context).apply {
        this.text = text
        textSize = 13f
        typeface = resolvedTypeface(settings)
        setTextColor(foregroundColor(settings))
        setPadding(0, dp(4), 0, 0)
    }

    private fun panelActions(settings: KeyboardSettings, actions: List<String>, onUtilityPress: (String) -> Unit): View {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.START
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
            actions.forEach { action ->
                addView(
                    Button(context).apply {
                        text = action.replace('_', ' ')
                            .replace("send less", "- send")
                            .replace("send more", "+ send")
                            .replace("skr less", "- skr")
                            .replace("skr more", "+ skr")
                            .replace("skr unless", "- unstake")
                            .replace("skr unmore", "+ unstake")
                            .replace("stake prev", "prev")
                            .replace("stake next", "next")
                            .replace("sources down", "- src")
                            .replace("sources up", "+ src")
                        isAllCaps = false
                        typeface = resolvedTypeface(settings)
                        setTextColor(foregroundColor(settings))
                        background = pillDrawable(parseColorOrFallback(settings.utilityHex, mutedUtilityColor(settings.theme)), dpFloat(12))
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
        rowIndex: Int,
        onKeyPress: (String) -> Unit,
        onUtilityPress: (String) -> Unit,
    ): View {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(rowInset(settings, rowIndex), 0, rowInset(settings, rowIndex), 0)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = if (rowIndex == 0) dp(4) else dp(layoutGap(settings))
            }
            labels.forEach { label ->
                addView(buildKey(label, settings, panelState, onKeyPress, onUtilityPress))
            }
        }
    }

    private fun buildKey(
        label: String,
        settings: KeyboardSettings,
        panelState: KeyboardPanelState,
        onKeyPress: (String) -> Unit,
        onUtilityPress: (String) -> Unit,
    ): View {
        val alternatesMap = alternatesFor(settings.language)
        val button = Button(context).apply {
            text = displayLabel(label, panelState)
            isAllCaps = false
            typeface = resolvedTypeface(settings)
            setTextColor(foregroundColor(settings))
            background = keyDrawable(settings, label)
            textSize = if (label.length > 1) 13f else 16f
            minHeight = 0
            minimumHeight = 0
            layoutParams = LayoutParams(0, dp(settings.keyHeightDp), keyWeight(settings, label)).apply {
                marginStart = dp(keyMargin(settings))
                marginEnd = dp(keyMargin(settings))
            }
        }

        val isAlphaLetter = panelState.keyboardLayer == KeyboardLayer.ALPHA && label.length == 1 && label[0].isLetter()
        if (isAlphaLetter) {
            glideTargets += GlideKeyTarget(label, button)
            attachLetterTouchBehavior(button, label, settings, panelState, onKeyPress, onUtilityPress)
        } else {
            button.setOnClickListener {
                when (label) {
                    "shift" -> onUtilityPress("action:cycle_shift")
                    "123" -> onUtilityPress("action:toggle_symbols")
                    else -> onKeyPress(resolveKeyValue(label, panelState))
                }
            }
            alternatesMap[label]?.let { alternates ->
                button.setOnLongClickListener {
                    val anchor = anchorRatioFor(button)
                    onUtilityPress("action:show_alts:$anchor:${alternates.joinToString("|")}")
                    true
                }
            }
            if (label == "space" || label == "⌫") {
                button.setOnTouchListener(spaceOrDeleteGestureListener(label, onUtilityPress))
            }
        }
        return button
    }

    private fun attachLetterTouchBehavior(
        button: Button,
        label: String,
        settings: KeyboardSettings,
        panelState: KeyboardPanelState,
        onKeyPress: (String) -> Unit,
        onUtilityPress: (String) -> Unit,
    ) {
        val alternates = alternatesFor(settings.language)[label.lowercase()].orEmpty()
        val touchSlop = (ViewConfiguration.get(context).scaledTouchSlop * 1.5f)
        var downX = 0f
        var downY = 0f
        var moved = false
        var longTriggered = false
        val longPress = Runnable {
            if (!moved && alternates.isNotEmpty()) {
                longTriggered = true
                onUtilityPress("action:show_alts:${anchorRatioFor(button)}:${alternates.joinToString("|")}")
            }
        }

        button.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    glideActive = false
                    glidePath.clear()
                    downX = event.rawX
                    downY = event.rawY
                    moved = false
                    longTriggered = false
                    applyPressEffect(button, settings, true)
                    button.postDelayed(longPress, (ViewConfiguration.getLongPressTimeout() * 0.72f).toLong())
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!moved && abs(dx) + abs(dy) > touchSlop) {
                        moved = true
                        button.removeCallbacks(longPress)
                        if (settings.glideTypingEnabled) {
                            beginGlide(resolveKeyValue(label, panelState))
                        }
                    }
                    if (moved && settings.glideTypingEnabled) {
                        glideActive = true
                        findLabelAt(event.rawX, event.rawY)?.let { addGlideLetter(resolveKeyValue(it, panelState)) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    button.removeCallbacks(longPress)
                    applyPressEffect(button, settings, false)
                    when {
                        longTriggered -> true
                        settings.glideTypingEnabled && glideActive && glidePath.isNotEmpty() -> {
                            finishGlide(onKeyPress, onUtilityPress)
                            true
                        }
                        else -> {
                            onKeyPress(resolveKeyValue(label, panelState))
                            true
                        }
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    button.removeCallbacks(longPress)
                    applyPressEffect(button, settings, false)
                    glideActive = false
                    glidePath.clear()
                    true
                }
                else -> false
            }
        }
    }

    private fun beginGlide(label: String) {
        glidePath.clear()
        glidePath += label
    }

    private fun addGlideLetter(label: String) {
        if (glidePath.lastOrNull() != label) {
            glidePath += label
        }
    }

    private fun finishGlide(onKeyPress: (String) -> Unit, onUtilityPress: (String) -> Unit) {
        val word = glidePath.joinToString("")
        if (word.length > 1) {
            onUtilityPress("action:hint:glide $word")
            onKeyPress("glide:$word")
        } else {
            glidePath.firstOrNull()?.let(onKeyPress)
        }
        glideActive = false
        glidePath.clear()
    }

    private fun findLabelAt(rawX: Float, rawY: Float): String? {
        val rect = Rect()
        return glideTargets.firstOrNull {
            it.button.getGlobalVisibleRect(rect)
            rect.contains(rawX.toInt(), rawY.toInt())
        }?.label
    }

    private fun spaceOrDeleteGestureListener(label: String, onUtilityPress: (String) -> Unit): View.OnTouchListener {
        return object : View.OnTouchListener {
            private var downX = 0f
            private var consumed = false

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.x
                        consumed = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.x - downX
                        if (!consumed && abs(deltaX) > dp(28)) {
                            consumed = true
                            when (label) {
                                "space" -> onUtilityPress(if (deltaX > 0) "action:cursor_right" else "action:cursor_left")
                                "⌫" -> onUtilityPress(if (deltaX > 0) "action:delete_char" else "action:delete_word")
                            }
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (consumed) return true
                    }
                }
                return false
            }
        }
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

    private fun keyWeight(settings: KeyboardSettings, label: String): Float {
        return when (label) {
            "space" -> when (settings.layoutMode) {
                KeyboardLayoutMode.COMPACT -> 4.2f
                KeyboardLayoutMode.COMFORT -> 4.8f
                KeyboardLayoutMode.THUMB -> 5.2f
            }
            "wallet" -> 1.55f
            "enter", "shift", "123", "⌫" -> when (settings.layoutMode) {
                KeyboardLayoutMode.COMPACT -> 1.35f
                KeyboardLayoutMode.COMFORT -> 1.45f
                KeyboardLayoutMode.THUMB -> 1.55f
            }
            else -> 1f
        }
    }

    private fun rowInset(settings: KeyboardSettings, rowIndex: Int): Int {
        val base = when (settings.layoutMode) {
            KeyboardLayoutMode.COMPACT -> 6
            KeyboardLayoutMode.COMFORT -> 10
            KeyboardLayoutMode.THUMB -> 16
        }
        return when (rowIndex) {
            1 -> dp(base)
            2 -> dp(base + if (settings.layoutMode == KeyboardLayoutMode.THUMB) 14 else 8)
            else -> 0
        }
    }

    private fun layoutGap(settings: KeyboardSettings): Int {
        return when (settings.layoutMode) {
            KeyboardLayoutMode.COMPACT -> 4
            KeyboardLayoutMode.COMFORT -> 6
            KeyboardLayoutMode.THUMB -> 8
        }
    }

    private fun keyMargin(settings: KeyboardSettings): Int {
        return when (settings.layoutMode) {
            KeyboardLayoutMode.COMPACT -> 1
            KeyboardLayoutMode.COMFORT -> 2
            KeyboardLayoutMode.THUMB -> 3
        }
    }

    private fun utilityHeight(settings: KeyboardSettings): Int {
        return when (settings.layoutMode) {
            KeyboardLayoutMode.COMPACT -> dp(34)
            KeyboardLayoutMode.COMFORT -> dp(36)
            KeyboardLayoutMode.THUMB -> dp(40)
        }
    }

    private fun compactStatus(status: String): String = status.substringBefore(".").take(56).ifBlank { "ready" }

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

    private fun backgroundColor(theme: KeyboardTheme): String = when (theme) {
        KeyboardTheme.SAND -> "#F1E3D3"
        KeyboardTheme.TEAL -> "#D8F4EE"
        KeyboardTheme.GRAPHITE -> "#20272B"
    }

    private fun keyDrawable(settings: KeyboardSettings, label: String): GradientDrawable {
        val radius = when (settings.layoutMode) {
            KeyboardLayoutMode.COMPACT -> cornerRadius(settings, label == "space")
            KeyboardLayoutMode.COMFORT -> cornerRadius(settings, label == "space")
            KeyboardLayoutMode.THUMB -> cornerRadius(settings, label == "space")
        }
        return pillDrawable(keyColor(settings, label), dpFloat(radius))
    }

    private fun cornerRadius(settings: KeyboardSettings, isSpace: Boolean): Int {
        if (settings.useSquareKeys) return if (isSpace) 10 else 8
        return when (settings.layoutMode) {
            KeyboardLayoutMode.COMPACT -> 14
            KeyboardLayoutMode.COMFORT -> 16
            KeyboardLayoutMode.THUMB -> if (isSpace) 24 else 18
        }
    }

    private fun pillDrawable(color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
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
        return parseColorOrFallback(settings.textHex, if (settings.theme == KeyboardTheme.GRAPHITE) "#FFFFFF" else "#111111")
    }

    private fun accentColor(theme: KeyboardTheme): String = when (theme) {
        KeyboardTheme.SAND -> "#C16A39"
        KeyboardTheme.TEAL -> "#12786B"
        KeyboardTheme.GRAPHITE -> "#5C6F52"
    }

    private fun neutralKeyColor(theme: KeyboardTheme): String = when (theme) {
        KeyboardTheme.SAND -> "#FFF8F2"
        KeyboardTheme.TEAL -> "#F3FFFC"
        KeyboardTheme.GRAPHITE -> "#344047"
    }

    private fun auxiliaryKeyColor(theme: KeyboardTheme): String = when (theme) {
        KeyboardTheme.SAND -> "#E5B289"
        KeyboardTheme.TEAL -> "#8ED1C4"
        KeyboardTheme.GRAPHITE -> "#58656C"
    }

    private fun mutedUtilityColor(theme: KeyboardTheme): String = when (theme) {
        KeyboardTheme.SAND -> "#E8C8B0"
        KeyboardTheme.TEAL -> "#BDE8DF"
        KeyboardTheme.GRAPHITE -> "#415158"
    }

    private fun panelColor(theme: KeyboardTheme): String = when (theme) {
        KeyboardTheme.SAND -> "#8A4A26"
        KeyboardTheme.TEAL -> "#0F5C53"
        KeyboardTheme.GRAPHITE -> "#263137"
    }

    private fun parseColorOrFallback(configured: String, fallback: String): Int {
        return runCatching { Color.parseColor(configured.ifBlank { fallback }) }.getOrElse { Color.parseColor(fallback) }
    }

    private fun shortAddress(value: String): String = if (value.length <= 12) value else "${value.take(6)}...${value.takeLast(6)}"

    private fun formatLamports(lamports: Long): String = String.format(Locale.US, "%.3f SOL", lamports / 1_000_000_000.0)

    private fun anchorRatioFor(view: View): Float {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val centerX = location[0] + view.width / 2f
        val totalWidth = width.takeIf { it > 0 }?.toFloat() ?: resources.displayMetrics.widthPixels.toFloat()
        return (centerX / totalWidth).coerceIn(0.05f, 0.95f)
    }

    private fun applyPressEffect(button: Button, settings: KeyboardSettings, pressed: Boolean) {
        if (!settings.showPressEffect) return
        button.animate().scaleX(if (pressed) 0.96f else 1f).scaleY(if (pressed) 0.96f else 1f).alpha(if (pressed) 0.9f else 1f).setDuration(45).start()
    }

    private fun resolvedTypeface(settings: KeyboardSettings): Typeface? {
        return when (settings.font) {
            KeyboardFont.SYSTEM -> Typeface.DEFAULT
            KeyboardFont.SERIF -> Typeface.SERIF
            KeyboardFont.MONO -> Typeface.MONOSPACE
            KeyboardFont.ROUNDED -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            KeyboardFont.CUSTOM -> loadCustomTypeface(settings.customFontUri) ?: Typeface.DEFAULT
        }
    }

    private fun loadCustomTypeface(uri: String): Typeface? {
        if (uri.isBlank()) return null
        if (cachedFontUri == uri && cachedTypeface != null) return cachedTypeface
        cachedFontUri = uri
        cachedTypeface = runCatching {
            context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { descriptor ->
                Typeface.Builder(descriptor.fileDescriptor).build()
            }
        }.getOrNull()
        return cachedTypeface
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
    }

    private fun dpFloat(value: Int): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics)
    }
}
