package com.androidlord.seekerwallet

import android.os.Bundle
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.androidlord.seekerkeyboard.ime.KeyboardSettingsStore
import com.androidlord.seekerkeyboard.ime.WalletAccessGuardStore
import com.androidlord.seekerwallet.data.WalletSessionStore

class WalletAccessGateActivity : AppCompatActivity() {
    private lateinit var accessGuardStore: WalletAccessGuardStore
    private lateinit var settingsStore: KeyboardSettingsStore
    private lateinit var sessionStore: WalletSessionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accessGuardStore = WalletAccessGuardStore(applicationContext)
        settingsStore = KeyboardSettingsStore(applicationContext)
        sessionStore = WalletSessionStore(applicationContext)

        val biometricManager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

        if (biometricManager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            sessionStore.saveKeyboardStatus("Set up biometrics or device PIN to unlock wallet controls.")
            finish()
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    accessGuardStore.unlock(settingsStore.load().walletUnlockMode)
                    if (intent?.getBooleanExtra("open_wallet_on_success", false) == true) {
                        accessGuardStore.markPendingOpenWallet()
                    }
                    sessionStore.saveKeyboardStatus("Wallet controls unlocked.")
                    runOnUiThread { finish() }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    sessionStore.saveKeyboardStatus(errString.toString().ifBlank { "Wallet unlock canceled." })
                    runOnUiThread { finish() }
                }

                override fun onAuthenticationFailed() {
                    sessionStore.saveKeyboardStatus("Authentication failed.")
                }
            }
        )

        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock SeekerKeyboard Wallet")
                .setSubtitle("Use biometrics or device PIN to open wallet controls.")
                .setDeviceCredentialAllowed(true)
                .build()
        )
    }
}
