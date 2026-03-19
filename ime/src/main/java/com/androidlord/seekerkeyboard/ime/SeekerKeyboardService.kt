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

class SeekerKeyboardService : InputMethodService() {
    private lateinit var settingsStore: KeyboardSettingsStore
    private lateinit var draftStore: WalletActionDraftStore
    private lateinit var clipboardHistoryStore: ClipboardHistoryStore
    private lateinit var walletStore: WalletSessionSnapshotStore
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var keyboardView: SeekerKeyboardView
    private var activePanel: UtilityPanel = UtilityPanel.NONE
    private var walletDrawerTab: WalletDrawerTab = WalletDrawerTab.OVERVIEW
    private var selectedStakeIndex: Int = 0
    private var keyboardLayer: KeyboardLayer = KeyboardLayer.ALPHA
    private var shiftState: ShiftState = ShiftState.OFF
    private var ephemeralHint: String = ""

    override fun onCreate() {
        super.onCreate()
        settingsStore = KeyboardSettingsStore(this)
        draftStore = WalletActionDraftStore(this)
        clipboardHistoryStore = ClipboardHistoryStore(this)
        walletStore = WalletSessionSnapshotStore(this)
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
        renderKeyboard()
    }

    private fun renderKeyboard() {
        val settings = settingsStore.load()
        val walletSnapshot = walletStore.load()
        val clip = clipboardManager.primaryClip?.getItemAt(0)?.coerceToText(this)
        val clipRaw = clip?.toString().orEmpty()
        clipboardHistoryStore.record(clip)
        selectedStakeIndex = selectedStakeIndex.coerceIn(0, (walletSnapshot.stakeAccountsPreview.size - 1).coerceAtLeast(0))
        val clipPreview = keyboardView.clipboardPreviewFrom(
            clip,
            clipboardManager.primaryClipDescription,
        )
        keyboardView.render(
            settings = settings,
            panelState = KeyboardPanelState(
                activePanel = activePanel,
                walletTab = walletDrawerTab,
                walletSnapshot = walletSnapshot,
                clipboardPreview = clipPreview,
                clipboardRaw = clipRaw,
                clipboardHistory = clipboardHistoryStore.load(),
                drafts = draftStore.load(),
                selectedStakeIndex = selectedStakeIndex,
                consolidationFeeQuote = ConsolidationFeeModel.quote(settings.consolidationSourceCountPreview),
                keyboardLayer = keyboardLayer,
                shiftState = shiftState,
                ephemeralHint = ephemeralHint,
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
        when (key) {
            "⌫" -> inputConnection.deleteSurroundingText(1, 0)
            "space" -> inputConnection.commitText(" ", 1)
            "enter" -> inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            "wallet" -> togglePanel(UtilityPanel.WALLET)
            else -> inputConnection.commitText(key, 1)
        }
        if (key == "space") {
            ephemeralHint = ""
        }
        if (key !in setOf("⌫", "space", "enter", "wallet") && shiftState == ShiftState.ONCE && keyboardLayer == KeyboardLayer.ALPHA) {
            shiftState = ShiftState.OFF
        }
        renderKeyboard()
    }

    private fun handleUtilityPress(action: String) {
        when {
            action.startsWith("panel:") -> {
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
            action == "action:connect" -> launchWalletBridge("connect")
            action == "action:refresh" -> launchWalletBridge("refresh")
            action == "action:disconnect" -> launchWalletBridge("disconnect")
            action == "action:send_down" -> draftStore.stepSendAmount(-0.01)
            action == "action:send_up" -> draftStore.stepSendAmount(0.01)
            action == "action:send" -> launchWalletBridge(
                "send",
                mapOf("recipient" to clipboardManager.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty())
            )
            action == "action:skr_stake_down" -> draftStore.stepSkrStakeAmount(-1)
            action == "action:skr_stake_up" -> draftStore.stepSkrStakeAmount(1)
            action == "action:skr_stake" -> launchWalletBridge("skr_stake")
            action == "action:skr_unstake_down" -> draftStore.stepSkrUnstakeAmount(-1)
            action == "action:skr_unstake_up" -> draftStore.stepSkrUnstakeAmount(1)
            action == "action:skr_unstake" -> launchWalletBridge("skr_unstake")
            action == "action:skr_withdraw" -> launchWalletBridge("skr_withdraw")
            action == "action:stake_prev" -> moveSelectedStake(-1)
            action == "action:stake_next" -> moveSelectedStake(1)
            action == "action:native_delegate" -> launchStakeAction("native_delegate")
            action == "action:native_deactivate" -> launchStakeAction("native_deactivate")
            action == "action:native_withdraw" -> launchStakeAction("native_withdraw")
            action == "action:consolidate" -> launchStakeAction("consolidate")
            action == "action:cycle_shift" -> cycleShiftState()
            action == "action:toggle_symbols" -> toggleSymbols()
            action.startsWith("action:hint:") -> ephemeralHint = action.removePrefix("action:hint:")
            action == "action:open_settings" -> launchSettingsApp()
            action == "action:switch_ime" -> launchImePicker()
            action == "action:paste" -> pasteClipboard()
            action == "action:hist_1" -> pasteHistory(0)
            action == "action:hist_2" -> pasteHistory(1)
            action == "action:hist_3" -> pasteHistory(2)
            action == "action:hist_4" -> pasteHistory(3)
            action == "action:cursor_left" -> moveCursor(-1)
            action == "action:cursor_right" -> moveCursor(1)
            action == "action:delete_char" -> currentInputConnection?.deleteSurroundingText(1, 0)
            action == "action:delete_word" -> deletePreviousWord()
            action == "action:clear" -> {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""))
                clipboardHistoryStore.clear()
            }
            action == "action:cycle_theme" -> cycleTheme()
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
        keyboardLayer = when (keyboardLayer) {
            KeyboardLayer.ALPHA -> KeyboardLayer.SYMBOLS
            KeyboardLayer.SYMBOLS -> KeyboardLayer.MORE_SYMBOLS
            KeyboardLayer.MORE_SYMBOLS -> KeyboardLayer.ALPHA
        }
        shiftState = ShiftState.OFF
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
            walletDrawerTab = WalletDrawerTab.OVERVIEW
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
    }

    private fun pasteClipboard() {
        val clip = clipboardManager.primaryClip?.getItemAt(0)?.coerceToText(this) ?: return
        currentInputConnection?.commitText(clip, 1)
    }

    private fun pasteHistory(index: Int) {
        val item = clipboardHistoryStore.load().getOrNull(index) ?: return
        currentInputConnection?.commitText(item, 1)
        ephemeralHint = "pasted history ${index + 1}"
    }

    private fun moveCursor(delta: Int) {
        val inputConnection = currentInputConnection ?: return
        val keyCode = if (delta < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        ephemeralHint = if (delta < 0) "cursor left" else "cursor right"
    }

    private fun deletePreviousWord() {
        val inputConnection = currentInputConnection ?: return
        val before = inputConnection.getTextBeforeCursor(64, 0)?.toString().orEmpty()
        if (before.isBlank()) {
            inputConnection.deleteSurroundingText(1, 0)
            ephemeralHint = "delete"
            return
        }
        val trimmed = before.trimEnd()
        val wordLength = trimmed.takeLastWhile { !it.isWhitespace() }.length
        val spacesLength = before.length - trimmed.length
        val deleteCount = (wordLength + spacesLength).coerceAtLeast(1)
        inputConnection.deleteSurroundingText(deleteCount, 0)
        ephemeralHint = "delete word"
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

    private fun launchStakeAction(action: String) {
        val selected = walletStore.load().stakeAccountsPreview.getOrNull(selectedStakeIndex)
        launchWalletBridge(
            walletAction = action,
            extras = buildMap {
                put("destination_stake_pubkey", selected?.pubkey.orEmpty())
                put("selected_stake_pubkey", selected?.pubkey.orEmpty())
                put("selected_stake_lamports", selected?.lamports?.toString().orEmpty())
                put("consolidation_source_count", settingsStore.load().consolidationSourceCountPreview.toString())
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
}
