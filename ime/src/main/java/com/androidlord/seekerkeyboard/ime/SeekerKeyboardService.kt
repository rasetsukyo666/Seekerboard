package com.androidlord.seekerkeyboard.ime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import java.util.Locale

class SeekerKeyboardService : InputMethodService() {
    private lateinit var settingsStore: KeyboardSettingsStore
    private lateinit var draftStore: WalletActionDraftStore
    private lateinit var clipboardHistoryStore: ClipboardHistoryStore
    private lateinit var walletStore: WalletSessionSnapshotStore
    private lateinit var walletAccessGuardStore: WalletAccessGuardStore
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var keyboardView: SeekerKeyboardView
    private var activePanel: UtilityPanel = UtilityPanel.NONE
    private var walletDrawerTab: WalletDrawerTab = WalletDrawerTab.OVERVIEW
    private var selectedStakeIndex: Int = 0
    private var keyboardLayer: KeyboardLayer = KeyboardLayer.ALPHA
    private var shiftState: ShiftState = ShiftState.OFF
    private var topStripMode: TopStripMode = TopStripMode.SUGGESTIONS
    private var ephemeralHint: String = ""
    private var alternateOptions: List<String> = emptyList()
    private var alternateAnchorRatio: Float = 0.5f
    private var alternateReplacementLength: Int = 0
    private var suggestions: List<String> = emptyList()
    private var lastClipboardPreview: String = ""

    override fun onCreate() {
        super.onCreate()
        settingsStore = KeyboardSettingsStore(this)
        draftStore = WalletActionDraftStore(this)
        clipboardHistoryStore = ClipboardHistoryStore(this)
        walletStore = WalletSessionSnapshotStore(this)
        walletAccessGuardStore = WalletAccessGuardStore(this)
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    override fun onCreateInputView(): View {
        keyboardView = SeekerKeyboardView(this)
        return FrameLayout(this).apply {
            addView(keyboardView)
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (walletAccessGuardStore.consumePendingOpenWallet()) {
            walletDrawerTab = WalletDrawerTab.OVERVIEW
            activePanel = UtilityPanel.WALLET
            keyboardLayer = KeyboardLayer.ALPHA
            walletAccessGuardStore.lock()
        }
        renderKeyboard()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        alternateOptions = emptyList()
        alternateAnchorRatio = 0.5f
        alternateReplacementLength = 0
        suggestions = emptyList()
        topStripMode = TopStripMode.SUGGESTIONS
        ephemeralHint = ""
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        alternateOptions = emptyList()
        alternateAnchorRatio = 0.5f
        alternateReplacementLength = 0
        suggestions = emptyList()
        topStripMode = TopStripMode.SUGGESTIONS
        ephemeralHint = ""
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (::keyboardView.isInitialized && (activePanel != UtilityPanel.NONE || alternateOptions.isNotEmpty())) {
            renderKeyboard()
        }
    }

    private fun renderKeyboard() {
        val settings = settingsStore.load()
        val walletSnapshot = walletStore.load()
        val clip = clipboardManager.primaryClip?.getItemAt(0)?.coerceToText(this)
        val clipRaw = clip?.toString().orEmpty()
        if (clipRaw.isNotBlank() && clipRaw != lastClipboardPreview) {
            clipboardHistoryStore.record(clip)
            lastClipboardPreview = clipRaw
        }
        selectedStakeIndex = selectedStakeIndex.coerceIn(0, (walletSnapshot.stakeAccountsPreview.size - 1).coerceAtLeast(0))
        val clipPreview = keyboardView.clipboardPreviewFrom(
            clip,
            clipboardManager.primaryClipDescription,
        )
        suggestions = currentSuggestions(settings)
        keyboardView.render(
            settings = settings,
            panelState = KeyboardPanelState(
                activePanel = activePanel,
                walletTab = walletDrawerTab,
                walletSnapshot = walletSnapshot,
                clipboardPreview = clipPreview,
                clipboardRaw = clipRaw,
                clipboardHistory = clipboardHistoryStore.load(),
                clipboardPinned = clipboardHistoryStore.loadPinned(),
                suggestions = suggestions,
                drafts = draftStore.load(),
                selectedStakeIndex = selectedStakeIndex,
                consolidationFeeQuote = ConsolidationFeeModel.quote(settings.consolidationSourceCountPreview),
                keyboardLayer = keyboardLayer,
                shiftState = shiftState,
                topStripMode = topStripMode,
                ephemeralHint = ephemeralHint,
                alternateOptions = alternateOptions,
                alternateAnchorRatio = alternateAnchorRatio,
            ),
            onKeyPress = ::handleKeyPress,
            onUtilityPress = ::handleUtilityPress,
        )
    }

    private fun handleKeyPress(key: String) {
        val inputConnection = currentInputConnection ?: return
        val settings = settingsStore.load()
        if (settings.hapticsEnabled) {
            vibrate()
        }
        if (activePanel == UtilityPanel.WALLET && key != "wallet") {
            activePanel = UtilityPanel.NONE
            walletAccessGuardStore.lock()
            keyboardLayer = KeyboardLayer.ALPHA
        }
        when (key) {
            "⌫" -> deleteSelectionOrPreviousChar()
            "space" -> {
                maybeAutocorrectCurrentWord(settings)
                inputConnection.commitText(" ", 1)
            }
            "enter" -> inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            "wallet" -> togglePanel(UtilityPanel.WALLET)
            "emoji" -> toggleEmoji()
            else -> {
                if (key.startsWith("glide:")) {
                    val resolution = GlideTypingEngine.resolve(settings.language, key.removePrefix("glide:"))
                    inputConnection.commitText(resolution.committedText, 1)
                    val suggestions = resolution.suggestions.filter { it != resolution.committedText }
                    alternateOptions = suggestions
                    alternateAnchorRatio = 0.5f
                    alternateReplacementLength = resolution.committedText.length
                    ephemeralHint = if (resolution.committedText == resolution.rawPath || resolution.rawPath.isBlank()) {
                        "glide ${resolution.committedText}"
                    } else {
                        "glide ${resolution.rawPath} -> ${resolution.committedText}"
                    }
                } else {
                    inputConnection.commitText(key, 1)
                    alternateReplacementLength = 0
                }
            }
        }
        if (key !in setOf("wallet")) {
            if (!key.startsWith("glide:")) {
                alternateOptions = emptyList()
                alternateReplacementLength = 0
            }
        }
        if (key == "space") {
            ephemeralHint = ""
            alternateReplacementLength = 0
        }
        if (key !in setOf("⌫", "space", "enter", "wallet") && shiftState == ShiftState.ONCE && keyboardLayer == KeyboardLayer.ALPHA) {
            shiftState = ShiftState.OFF
        }
        if (shouldRenderAfterKey(key, settings)) {
            renderKeyboard()
        }
    }

    private fun handleUtilityPress(action: String) {
        when {
            action.startsWith("panel:") -> {
                topStripMode = TopStripMode.TOOLS
                val panel = when (action.removePrefix("panel:")) {
                    "wallet" -> UtilityPanel.WALLET
                    "clipboard" -> UtilityPanel.CLIPBOARD
                    "theme" -> UtilityPanel.THEME
                    "settings" -> UtilityPanel.SETTINGS
                    else -> UtilityPanel.NONE
                }
                togglePanel(panel)
            }
            action == "action:wallet_overview" -> walletDrawerTab = WalletDrawerTab.OVERVIEW
            action == "action:wallet_stake" -> walletDrawerTab = WalletDrawerTab.STAKE
            action == "action:wallet_accounts" -> walletDrawerTab = WalletDrawerTab.ACCOUNTS
            action == "action:connect" -> launchWalletReview(
                action = "connect",
                title = "Connect Wallet",
                summary = "Authorize SeekerKeyboard and establish a persistent session for wallet actions.",
                detail = "First connect should complete wallet authorization and session approval before returning to the keyboard.",
            )
            action == "action:refresh" -> launchWalletBridge("refresh")
            action == "action:disconnect" -> launchWalletBridge("disconnect")
            action == "action:send_less" -> draftStore.stepSendAmount(-0.01)
            action == "action:send_more" -> draftStore.stepSendAmount(0.01)
            action == "action:send" -> launchWalletReview(
                action = "send",
                title = "Send SOL",
                summary = "${draftStore.load().sendAmountSol} SOL to clipboard address",
                detail = clipboardManager.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty(),
                extras = mapOf("recipient" to clipboardManager.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty())
            )
            action == "action:skr_less" -> draftStore.stepSkrStakeAmount(-1)
            action == "action:skr_more" -> draftStore.stepSkrStakeAmount(1)
            action == "action:skr_stake" -> launchWalletReview(
                action = "skr_stake",
                title = "Stake SKR",
                summary = "${draftStore.load().skrStakeAmount} SKR via official Solana Mobile stake flow",
                detail = "Approval opens outside the keyboard and returns with synced wallet state.",
            )
            action == "action:skr_unless" -> draftStore.stepSkrUnstakeAmount(-1)
            action == "action:skr_unmore" -> draftStore.stepSkrUnstakeAmount(1)
            action == "action:skr_unstake" -> launchWalletReview(
                action = "skr_unstake",
                title = "Unstake SKR",
                summary = "${draftStore.load().skrUnstakeAmount} SKR into cooldown",
                detail = "Official SKR unstake request with wallet approval.",
            )
            action == "action:skr_withdraw" -> launchWalletReview(
                action = "skr_withdraw",
                title = "Withdraw SKR",
                summary = "Move ready SKR back to the liquid account",
                detail = "Uses the official SKR withdraw transaction.",
            )
            action == "action:stake_prev" -> moveSelectedStake(-1)
            action == "action:stake_next" -> moveSelectedStake(1)
            action == "action:native_delegate" -> launchStakeAction("native_delegate")
            action == "action:native_deactivate" -> launchStakeAction("native_deactivate")
            action == "action:native_withdraw" -> launchStakeAction("native_withdraw")
            action == "action:consolidate" -> launchStakeAction("consolidate")
            action == "action:cycle_shift" -> cycleShiftState()
            action == "action:toggle_top_strip" -> {
                topStripMode = if (topStripMode == TopStripMode.SUGGESTIONS) TopStripMode.TOOLS else TopStripMode.SUGGESTIONS
            }
            action == "action:toggle_symbols" -> toggleSymbols()
            action == "action:toggle_emoji" -> toggleEmoji()
            action.startsWith("action:hint:") -> ephemeralHint = action.removePrefix("action:hint:")
            action.startsWith("action:show_alts:") -> {
                val payload = action.removePrefix("action:show_alts:")
                val firstSeparator = payload.indexOf(':')
                val ratio = if (firstSeparator > 0) payload.substring(0, firstSeparator).toFloatOrNull() ?: 0.5f else 0.5f
                val items = if (firstSeparator > 0) payload.substring(firstSeparator + 1) else payload
                alternateAnchorRatio = ratio
                alternateReplacementLength = 0
                alternateOptions = items.split('|')
                    .filter { it.isNotBlank() }
                ephemeralHint = "pick alternate"
            }
            action.startsWith("action:pick_alt:") -> {
                val value = action.removePrefix("action:pick_alt:")
                currentInputConnection?.let { inputConnection ->
                    if (alternateReplacementLength > 0) {
                        inputConnection.deleteSurroundingText(alternateReplacementLength, 0)
                    }
                    inputConnection.commitText(value, 1)
                }
                alternateOptions = emptyList()
                alternateAnchorRatio = 0.5f
                alternateReplacementLength = 0
                ephemeralHint = value
            }
            action.startsWith("action:pick_suggestion:") -> {
                replaceCurrentWord(action.removePrefix("action:pick_suggestion:"))
                currentInputConnection?.commitText(" ", 1)
                suggestions = emptyList()
                ephemeralHint = "suggestion applied"
            }
            action == "action:clear_alts" -> {
                alternateOptions = emptyList()
                alternateAnchorRatio = 0.5f
                alternateReplacementLength = 0
                ephemeralHint = ""
            }
            action == "action:open_settings" -> launchSettingsApp()
            action == "action:switch_ime" -> launchImePicker()
            action == "action:paste" -> pasteClipboard()
            action.startsWith("action:clip_item:") -> {
                pasteClipboardItem(action.removePrefix("action:clip_item:"))
            }
            action == "action:cursor_left" -> moveCursor(-1)
            action == "action:cursor_right" -> moveCursor(1)
            action == "action:delete_char" -> deleteSelectionOrPreviousChar()
            action == "action:delete_word" -> deletePreviousWord()
            action == "action:clear" -> {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""))
                clipboardHistoryStore.clear()
            }
            action == "action:cycle_theme" -> cycleTheme()
            action == "action:cycle_layout" -> cycleLayoutMode()
            action == "action:cycle_language" -> cycleLanguage()
            action == "action:redraw" -> Unit
            action == "action:sources_up" -> settingsStore.saveConsolidationSourceCountPreview(settingsStore.load().consolidationSourceCountPreview + 1)
            action == "action:sources_down" -> settingsStore.saveConsolidationSourceCountPreview(settingsStore.load().consolidationSourceCountPreview - 1)
            action == "action:height_up" -> settingsStore.saveKeyHeightDp(settingsStore.load().keyHeightDp + 4)
            action == "action:height_down" -> settingsStore.saveKeyHeightDp(settingsStore.load().keyHeightDp - 4)
            action == "action:toggle_number" -> settingsStore.saveNumberRow(!settingsStore.load().showNumberRow)
        }
        renderKeyboard()
    }

    private fun cycleShiftState() {
        shiftState = when (shiftState) {
            ShiftState.OFF -> ShiftState.ONCE
            ShiftState.ONCE -> ShiftState.CAPS
            ShiftState.CAPS -> ShiftState.OFF
        }
    }

    private fun toggleSymbols() {
        if (activePanel == UtilityPanel.WALLET) {
            keyboardLayer = KeyboardLayer.ALPHA
            return
        }
        keyboardLayer = when (keyboardLayer) {
            KeyboardLayer.ALPHA -> KeyboardLayer.SYMBOLS
            KeyboardLayer.SYMBOLS -> KeyboardLayer.MORE_SYMBOLS
            KeyboardLayer.MORE_SYMBOLS -> KeyboardLayer.ALPHA
            KeyboardLayer.EMOJI -> KeyboardLayer.ALPHA
        }
        shiftState = ShiftState.OFF
        alternateOptions = emptyList()
        alternateReplacementLength = 0
    }

    private fun toggleEmoji() {
        keyboardLayer = if (keyboardLayer == KeyboardLayer.EMOJI) KeyboardLayer.ALPHA else KeyboardLayer.EMOJI
        shiftState = ShiftState.OFF
        alternateOptions = emptyList()
        alternateReplacementLength = 0
        if (activePanel == UtilityPanel.WALLET) {
            activePanel = UtilityPanel.NONE
        }
    }

    private fun moveSelectedStake(delta: Int) {
        val count = walletStore.load().stakeAccountsPreview.size
        if (count <= 0) {
            selectedStakeIndex = 0
            return
        }
        selectedStakeIndex = (selectedStakeIndex + delta).coerceIn(0, count - 1)
    }

    private fun togglePanel(panel: UtilityPanel) {
        if (panel == UtilityPanel.WALLET && activePanel != panel) {
            walletAccessGuardStore.lock()
            launchWalletAccessGate()
            ephemeralHint = "unlock wallet"
            return
        }
        if (panel == UtilityPanel.WALLET && activePanel == panel) {
            walletAccessGuardStore.lock()
            activePanel = UtilityPanel.NONE
            return
        }
        if (activePanel == UtilityPanel.WALLET && panel != UtilityPanel.WALLET) {
            walletAccessGuardStore.lock()
        }
        if (panel == UtilityPanel.WALLET) {
            walletDrawerTab = WalletDrawerTab.OVERVIEW
            keyboardLayer = KeyboardLayer.ALPHA
        }
        activePanel = if (activePanel == panel) UtilityPanel.NONE else panel
    }

    private fun cycleTheme() {
        val current = settingsStore.load().theme
        val next = when (current) {
            KeyboardTheme.SAND -> KeyboardTheme.TEAL
            KeyboardTheme.TEAL -> KeyboardTheme.GRAPHITE
            KeyboardTheme.GRAPHITE -> KeyboardTheme.SAND
        }
        settingsStore.saveTheme(next)
        ephemeralHint = "theme ${next.label.lowercase()}"
    }

    private fun cycleLayoutMode() {
        val current = settingsStore.load().layoutMode
        val next = when (current) {
            KeyboardLayoutMode.COMPACT -> KeyboardLayoutMode.COMFORT
            KeyboardLayoutMode.COMFORT -> KeyboardLayoutMode.THUMB
            KeyboardLayoutMode.THUMB -> KeyboardLayoutMode.COMPACT
        }
        settingsStore.saveLayoutMode(next)
        ephemeralHint = "layout ${next.label.lowercase()}"
    }

    private fun cycleLanguage() {
        val current = settingsStore.load().language
        val next = when (current) {
            KeyboardLanguage.ENGLISH -> KeyboardLanguage.SPANISH
            KeyboardLanguage.SPANISH -> KeyboardLanguage.PORTUGUESE
            KeyboardLanguage.PORTUGUESE -> KeyboardLanguage.ENGLISH
        }
        settingsStore.saveLanguage(next)
        ephemeralHint = "language ${next.label.lowercase()}"
    }

    private fun pasteClipboard() {
        val clip = clipboardManager.primaryClip?.getItemAt(0)?.coerceToText(this) ?: return
        currentInputConnection?.commitText(clip, 1)
    }

    private fun pasteHistory(index: Int) {
        val item = clipboardHistoryStore.load().getOrNull(index) ?: return
        currentInputConnection?.commitText(item, 1)
        ephemeralHint = "pasted history ${index + 1}"
        alternateOptions = emptyList()
        alternateReplacementLength = 0
    }

    private fun pasteClipboardItem(value: String) {
        if (value.isBlank()) return
        currentInputConnection?.commitText(value, 1)
        ephemeralHint = "clipboard item pasted"
        alternateOptions = emptyList()
        alternateReplacementLength = 0
    }

    private fun moveCursor(delta: Int) {
        val inputConnection = currentInputConnection ?: return
        val keyCode = if (delta < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        ephemeralHint = if (delta < 0) "cursor left" else "cursor right"
        alternateOptions = emptyList()
    }

    private fun deletePreviousWord() {
        val inputConnection = currentInputConnection ?: return
        val selected = inputConnection.getSelectedText(0)?.toString().orEmpty()
        if (selected.isNotEmpty()) {
            inputConnection.commitText("", 1)
            ephemeralHint = "delete selection"
            alternateOptions = emptyList()
            return
        }
        val before = inputConnection.getTextBeforeCursor(64, 0)?.toString().orEmpty()
        if (before.isBlank()) {
            inputConnection.deleteSurroundingText(1, 0)
            ephemeralHint = "delete"
            alternateOptions = emptyList()
            return
        }
        val trimmed = before.trimEnd()
        val wordLength = trimmed.takeLastWhile { !it.isWhitespace() }.length
        val spacesLength = before.length - trimmed.length
        val deleteCount = (wordLength + spacesLength).coerceAtLeast(1)
        inputConnection.deleteSurroundingText(deleteCount, 0)
        ephemeralHint = "delete word"
        alternateOptions = emptyList()
    }

    private fun deleteSelectionOrPreviousChar() {
        val inputConnection = currentInputConnection ?: return
        val selected = inputConnection.getSelectedText(0)?.toString().orEmpty()
        if (selected.isNotEmpty()) {
            inputConnection.commitText("", 1)
            ephemeralHint = "delete selection"
        } else {
            inputConnection.deleteSurroundingText(1, 0)
        }
        alternateOptions = emptyList()
    }

    private fun launchSettingsApp(extraKey: String? = null, extraValue: String? = null) {
        requestHideSelf(0)
        startActivity(
            Intent().apply {
                setClassName(packageName, "com.androidlord.seekerwallet.MainActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (extraKey != null && extraValue != null) {
                    putExtra(extraKey, extraValue)
                }
            }
        )
    }

    private fun launchWalletBridge(walletAction: String, extras: Map<String, String> = emptyMap()) {
        requestHideSelf(0)
        startActivity(
            Intent().apply {
                setClassName(packageName, "com.androidlord.seekerwallet.WalletBridgeActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("wallet_action", walletAction)
                extras.forEach { (key, value) -> putExtra(key, value) }
            }
        )
    }

    private fun launchWalletAccessGate() {
        requestHideSelf(0)
        startActivity(
            Intent().apply {
                setClassName(packageName, "com.androidlord.seekerwallet.WalletAccessGateActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("open_wallet_on_success", true)
            }
        )
    }

    private fun launchStakeAction(action: String) {
        val selected = walletStore.load().stakeAccountsPreview.getOrNull(selectedStakeIndex)
        val autoSourceCount = walletStore.load().eligibleConsolidationSources.coerceIn(1, 3)
        val extras = buildMap {
            put("destination_stake_pubkey", selected?.pubkey.orEmpty())
            put("selected_stake_pubkey", selected?.pubkey.orEmpty())
            put("selected_stake_lamports", selected?.lamports?.toString().orEmpty())
            put("consolidation_source_count", autoSourceCount.toString())
        }
        val title = when (action) {
            "native_delegate" -> "Delegate Native Stake"
            "native_deactivate" -> "Deactivate Native Stake"
            "native_withdraw" -> "Withdraw Native Stake"
            "consolidate" -> "Consolidate Stake Accounts"
            else -> "Wallet Action"
        }
        val summary = when (action) {
            "native_delegate" -> "${selected?.let { shortStakeLabel(it.pubkey) } ?: "selected stake"} to Solana Mobile validator"
            "native_deactivate" -> "Stop earning on ${selected?.let { shortStakeLabel(it.pubkey) } ?: "selected stake"}"
            "native_withdraw" -> "Withdraw ${selected?.lamports?.let(::lamportsToSolLabel) ?: "selected balance"} to the main wallet"
            "consolidate" -> "Merge ${settingsStore.load().consolidationSourceCountPreview} source account(s) into ${selected?.let { shortStakeLabel(it.pubkey) } ?: "selected stake"}"
            else -> action
        }
        val detail = when (action) {
            "consolidate" -> "Carries ${ConsolidationFeeModel.quote(autoSourceCount).feeInSkr} SKR fee preview and uses the best likely source set automatically."
            else -> "Approval opens outside the keyboard, then syncs state back into the IME."
        }
        launchWalletReview(
            action = action,
            title = title,
            summary = summary,
            detail = detail,
            extras = extras,
        )
    }

    private fun launchWalletReview(
        action: String,
        title: String,
        summary: String,
        detail: String,
        extras: Map<String, String> = emptyMap(),
    ) {
        requestHideSelf(0)
        startActivity(
            Intent().apply {
                setClassName(packageName, "com.androidlord.seekerwallet.WalletReviewActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("wallet_action", action)
                putExtra("review_title", title)
                putExtra("review_summary", summary)
                putExtra("review_detail", detail)
                extras.forEach { (key, value) -> putExtra(key, value) }
            }
        )
    }

    private fun launchImePicker() {
        requestHideSelf(0)
        startActivity(
            Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    private fun vibrate() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator ?: return
        vibrator.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun currentSuggestions(settings: KeyboardSettings): List<String> {
        if (!settings.suggestionsEnabled) return emptyList()
        val (currentWord, previousWord) = currentWordContext()
        if (currentWord.length == 1 && !currentWord[0].isLetter()) return emptyList()
        return GlideTypingEngine.suggestCorrections(settings.language, currentWord, previousWord)
    }

    private fun currentWordBeforeCursor(): String {
        val before = currentInputConnection?.getTextBeforeCursor(64, 0)?.toString().orEmpty()
        return before.takeLastWhile { !it.isWhitespace() && (it.isLetter() || it == '\'') }.lowercase(Locale.US)
    }

    private fun currentWordContext(): Pair<String, String> {
        val before = currentInputConnection?.getTextBeforeCursor(96, 0)?.toString().orEmpty()
        val trimmedEnd = before.trimEnd()
        val currentWord = trimmedEnd.takeLastWhile { !it.isWhitespace() && (it.isLetter() || it == '\'') }
        val previousChunk = if (currentWord.isNotEmpty()) {
            trimmedEnd.dropLast(currentWord.length).trimEnd()
        } else {
            trimmedEnd
        }
        val previousWord = previousChunk.takeLastWhile { !it.isWhitespace() && (it.isLetter() || it == '\'') }
        return currentWord to previousWord
    }

    private fun maybeAutocorrectCurrentWord(settings: KeyboardSettings) {
        if (!settings.autocorrectEnabled) return
        // Forced replacement is currently too error-prone for this keyboard.
        // Keep suggestions/manual picks available, but do not auto-apply changes.
        return
    }

    private fun replaceCurrentWord(replacement: String) {
        val inputConnection = currentInputConnection ?: return
        val current = currentWordBeforeCursor()
        if (current.isBlank()) return
        inputConnection.deleteSurroundingText(current.length, 0)
        inputConnection.commitText(replacement, 1)
    }

    private fun shouldRenderAfterKey(key: String, settings: KeyboardSettings): Boolean {
        if (activePanel != UtilityPanel.NONE) return true
        if (alternateOptions.isNotEmpty()) return true
        if (settings.suggestionsEnabled && topStripMode == TopStripMode.SUGGESTIONS && key.length == 1) return true
        if (settings.autocorrectEnabled) return true
        if (key == "wallet" || key == "emoji" || key == "space" || key == "enter" || key == "⌫") return true
        if (key.startsWith("glide:")) return true
        return shiftState == ShiftState.ONCE
    }

    private fun shortStakeLabel(pubkey: String): String {
        return if (pubkey.length <= 12) pubkey else "${pubkey.take(4)}…${pubkey.takeLast(4)}"
    }

    private fun lamportsToSolLabel(lamports: Long): String {
        return String.format(java.util.Locale.US, "%.3f SOL", lamports / 1_000_000_000.0)
    }
}
