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
    private lateinit var walletStore: WalletSessionSnapshotStore
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var keyboardView: SeekerKeyboardView
    private var activePanel: UtilityPanel = UtilityPanel.NONE

    override fun onCreate() {
        super.onCreate()
        settingsStore = KeyboardSettingsStore(this)
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
        val clip = clipboardManager.primaryClip?.getItemAt(0)?.coerceToText(this)
        val clipPreview = keyboardView.clipboardPreviewFrom(
            clip,
            clipboardManager.primaryClipDescription,
        )
        keyboardView.render(
            settings = settings,
            panelState = KeyboardPanelState(
                activePanel = activePanel,
                walletSnapshot = walletStore.load(),
                clipboardPreview = clipPreview,
                consolidationFeeQuote = ConsolidationFeeModel.quote(settings.consolidationSourceCountPreview),
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
            "123" -> inputConnection.commitText("123 ", 1)
            "shift" -> Unit
            else -> inputConnection.commitText(key, 1)
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
            action == "action:connect" || action == "action:stake" || action == "action:accounts" || action == "action:send" -> launchSettingsApp()
            action == "action:consolidate" -> launchSettingsApp("wallet_action", "consolidate")
            action == "action:open_settings" -> launchSettingsApp()
            action == "action:switch_ime" -> launchImePicker()
            action == "action:paste" -> pasteClipboard()
            action == "action:clear" -> clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""))
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

    private fun togglePanel(panel: UtilityPanel) {
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
