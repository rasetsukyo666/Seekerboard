package com.androidlord.seekerwallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.androidlord.seekerwallet.theme.SeekerTheme
import com.androidlord.seekerwallet.theme.ThemePreset

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SeekerTheme(themePreset = ThemePreset.SAND) {
                SettingsScreen()
            }
        }
    }
}
