package com.androidlord.seekerkeyboard.ime

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout

class SeekerKeyboardService : InputMethodService() {
    private lateinit var settingsStore: KeyboardSettingsStore
    private lateinit var keyboardView: SeekerKeyboardView

    override fun onCreate() {
        super.onCreate()
        settingsStore = KeyboardSettingsStore(this)
    }

    override fun onCreateInputView(): View {
        keyboardView = SeekerKeyboardView(this)
        return FrameLayout(this).apply {
            addView(keyboardView)
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboardView.render(settingsStore.load(), ::handleKeyPress)
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
            "enter" -> inputConnection.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER))
            "wallet" -> launchWallet()
            "123" -> inputConnection.commitText("123 ", 1)
            else -> inputConnection.commitText(key, 1)
        }
    }

    private fun launchWallet() {
        requestHideSelf(0)
        startActivity(
            Intent().apply {
                setClassName(packageName, "com.androidlord.seekerwallet.MainActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("open_wallet_panel", true)
            }
        )
    }

    private fun vibrate() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator ?: return
        vibrator.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
