package com.androidlord.seekerwallet

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.androidlord.seekerkeyboard.ime.KeyboardSettingsStore
import com.androidlord.seekerkeyboard.ime.KeyboardTheme
import com.androidlord.seekerwallet.theme.LocalSeekerPalette

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    requestedWalletAction: String? = null,
) {
    val context = LocalContext.current
    val version = remember { mutableIntStateOf(0) }
    version.intValue
    val settingsStore = KeyboardSettingsStore(context)
    val settings = settingsStore.load()
    val palette = LocalSeekerPalette.current

    fun refresh() {
        version.intValue += 1
    }

    fun saveTheme(theme: KeyboardTheme) {
        KeyboardSettingsStore(context).saveTheme(theme)
        refresh()
    }

    fun saveNumberRow(enabled: Boolean) {
        KeyboardSettingsStore(context).saveNumberRow(enabled)
        refresh()
    }

    fun saveWalletKey(enabled: Boolean) {
        KeyboardSettingsStore(context).saveWalletKey(enabled)
        refresh()
    }

    fun saveHaptics(enabled: Boolean) {
        KeyboardSettingsStore(context).saveHapticsEnabled(enabled)
        refresh()
    }

    fun saveHeight(value: Float) {
        KeyboardSettingsStore(context).saveKeyHeightDp(value.toInt())
        refresh()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(palette.backdropTop, palette.backdropBottom)))
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                HeaderCard()
            }
            if (!requestedWalletAction.isNullOrBlank()) {
                item {
                    SettingsCard("Keyboard Action Request") {
                        Text(
                            "The keyboard requested `$requestedWalletAction`. This fallback exists until the full wallet flow is moved completely into the IME surface.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            item {
                SettingsCard("Keyboard Enablement") {
                    Text("This app is only for onboarding and advanced settings. Typing, wallet access, clipboard, and theme toggles happen inside the keyboard.", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) {
                            Text("Enable IME")
                        }
                        OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SUBTYPE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) {
                            Text("Pick Keyboard")
                        }
                    }
                }
            }
            item {
                SettingsCard("Theme") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        KeyboardTheme.entries.forEach { option ->
                            AssistChip(
                                onClick = { saveTheme(option) },
                                label = { Text(option.label) },
                                enabled = option != settings.theme,
                            )
                        }
                    }
                }
            }
            item {
                SettingsCard("Layout") {
                    ToggleRow("Number row", settings.showNumberRow, ::saveNumberRow)
                    ToggleRow("Wallet button", settings.showWalletKey, ::saveWalletKey)
                    ToggleRow("Haptics", settings.hapticsEnabled, ::saveHaptics)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Key height: ${settings.keyHeightDp}dp", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Slider(
                            value = settings.keyHeightDp.toFloat(),
                            onValueChange = ::saveHeight,
                            valueRange = 40f..76f,
                        )
                    }
                }
            }
            item {
                SettingsCard("Roadmap") {
                    Text("Next: richer keyboard-only wallet drawers, clipboard history, custom wallpapers, and deeper HeliBoard-style key customization.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun HeaderCard() {
    SettingsCard("SeekerKeyboard") {
        Text("Private keyboard with discreet wallet, clipboard, theme, and settings controls embedded directly in the typing surface.", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
        shape = RoundedCornerShape(26.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
