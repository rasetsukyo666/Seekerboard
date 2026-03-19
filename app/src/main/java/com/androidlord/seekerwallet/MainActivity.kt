package com.androidlord.seekerwallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.androidlord.seekerwallet.theme.SeekerTheme
import com.androidlord.seekerwallet.theme.ThemePreset

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val requestedWalletAction by remember {
                androidx.compose.runtime.mutableStateOf(intent?.getStringExtra("wallet_action"))
            }
            SeekerTheme(themePreset = ThemePreset.TEAL) {
                SettingsScreen(requestedWalletAction = requestedWalletAction)
            }
        }
    }
}
