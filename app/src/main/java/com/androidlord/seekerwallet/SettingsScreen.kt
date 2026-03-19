package com.androidlord.seekerwallet

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
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
import androidx.compose.material3.TextField
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
import com.androidlord.seekerkeyboard.ime.KeyboardFont
import com.androidlord.seekerkeyboard.ime.KeyboardLanguage
import com.androidlord.seekerkeyboard.ime.KeyboardLayoutMode
import com.androidlord.seekerkeyboard.ime.KeyboardSettingsStore
import com.androidlord.seekerkeyboard.ime.KeyboardTheme
import com.androidlord.seekerkeyboard.ime.WalletActionDraftStore
import com.androidlord.seekerwallet.data.WalletSessionStore
import com.androidlord.seekerwallet.wallet.SolanaCluster
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
    val sessionStore = WalletSessionStore(context)
    val draftStore = WalletActionDraftStore(context)
    val settings = settingsStore.load()
    val cluster = sessionStore.loadCluster()
    val drafts = draftStore.load()
    val palette = LocalSeekerPalette.current

    fun refresh() {
        version.intValue += 1
    }

    val wallpaperPicker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            settingsStore.saveWallpaperUri(uri.toString())
            refresh()
        }
    }
    val fontPicker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            settingsStore.saveCustomFontUri(uri.toString())
            settingsStore.saveFont(KeyboardFont.CUSTOM)
            refresh()
        }
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
            item { HeaderCard() }
            if (!requestedWalletAction.isNullOrBlank()) {
                item {
                    SettingsCard("Keyboard Action Request") {
                        Text(
                            "The keyboard requested `$requestedWalletAction`. Most wallet execution now starts from the IME bridge; this fallback remains for onboarding and recovery.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            item {
                SettingsCard("Keyboard Enablement") {
                    Text("This app is for onboarding, recovery, and advanced customization. Wallet, stake, clipboard, theme controls, and layout modes are driven from the keyboard.", style = MaterialTheme.typography.bodyMedium)
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
                SettingsCard("Theme Preset") {
                    Text("Base theme uses a Solana-style dark gradient with metallic keys and green legends.", style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        KeyboardTheme.entries.forEach { option ->
                            AssistChip(
                                onClick = {
                                    settingsStore.saveTheme(option)
                                    refresh()
                                },
                                label = { Text(option.label) },
                                enabled = option != settings.theme,
                            )
                        }
                    }
                }
            }
            item {
                SettingsCard("Cluster") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SolanaCluster.entries.forEach { option ->
                            AssistChip(
                                onClick = {
                                    sessionStore.saveCluster(option)
                                    refresh()
                                },
                                label = { Text(option.label) },
                                enabled = option != cluster,
                            )
                        }
                    }
                }
            }
            item {
                SettingsCard("Fonts") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        KeyboardFont.entries.forEach { option ->
                            AssistChip(
                                onClick = {
                                    settingsStore.saveFont(option)
                                    refresh()
                                },
                                label = { Text(option.label) },
                                enabled = option != settings.font,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { fontPicker.launch(arrayOf("font/*", "application/octet-stream")) }) {
                            Text("Choose Font")
                        }
                        OutlinedButton(onClick = {
                            settingsStore.saveCustomFontUri("")
                            if (settings.font == KeyboardFont.CUSTOM) {
                                settingsStore.saveFont(KeyboardFont.SYSTEM)
                            }
                            refresh()
                        }) {
                            Text("Clear Font")
                        }
                    }
                }
            }
            item {
                SettingsCard("Language") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        KeyboardLanguage.entries.forEach { option ->
                            AssistChip(
                                onClick = {
                                    settingsStore.saveLanguage(option)
                                    refresh()
                                },
                                label = { Text(option.label) },
                                enabled = option != settings.language,
                            )
                        }
                    }
                }
            }
            item {
                SettingsCard("Layout") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        KeyboardLayoutMode.entries.forEach { option ->
                            AssistChip(
                                onClick = {
                                    settingsStore.saveLayoutMode(option)
                                    refresh()
                                },
                                label = { Text(option.label) },
                                enabled = option != settings.layoutMode,
                            )
                        }
                    }
                    ToggleRow("Number row", settings.showNumberRow) {
                        settingsStore.saveNumberRow(it)
                        refresh()
                    }
                    ToggleRow("Wallet button", settings.showWalletKey) {
                        settingsStore.saveWalletKey(it)
                        refresh()
                    }
                    ToggleRow("Haptics", settings.hapticsEnabled) {
                        settingsStore.saveHapticsEnabled(it)
                        refresh()
                    }
                    ToggleRow("Press effect", settings.showPressEffect) {
                        settingsStore.savePressEffect(it)
                        refresh()
                    }
                    ToggleRow("Square keys", settings.useSquareKeys) {
                        settingsStore.saveSquareKeys(it)
                        refresh()
                    }
                    ToggleRow("Autocorrect", settings.autocorrectEnabled) {
                        settingsStore.saveAutocorrectEnabled(it)
                        refresh()
                    }
                    ToggleRow("Suggestions", settings.suggestionsEnabled) {
                        settingsStore.saveSuggestionsEnabled(it)
                        refresh()
                    }
                    ToggleRow("Glide typing", settings.glideTypingEnabled) {
                        settingsStore.saveGlideTypingEnabled(it)
                        refresh()
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Key height: ${settings.keyHeightDp}dp", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Slider(
                            value = settings.keyHeightDp.toFloat(),
                            onValueChange = {
                                settingsStore.saveKeyHeightDp(it.toInt())
                                refresh()
                            },
                            valueRange = 40f..76f,
                        )
                    }
                }
            }
            item {
                SettingsCard("Wallet Presets") {
                    LabeledField("Send SOL preset", drafts.sendAmountSol) {
                        draftStore.saveSendAmount(it)
                        refresh()
                    }
                    LabeledField("SKR stake preset", drafts.skrStakeAmount) {
                        draftStore.saveSkrStakeAmount(it)
                        refresh()
                    }
                    LabeledField("SKR unstake preset", drafts.skrUnstakeAmount) {
                        draftStore.saveSkrUnstakeAmount(it)
                        refresh()
                    }
                }
            }
            item {
                SettingsCard("Wallpaper") {
                    Text(if (settings.wallpaperUri.isBlank()) "No wallpaper selected." else "Wallpaper linked to keyboard background.", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { wallpaperPicker.launch(arrayOf("image/*")) }) {
                            Text("Choose Image")
                        }
                        OutlinedButton(onClick = {
                            settingsStore.saveWallpaperUri("")
                            refresh()
                        }) {
                            Text("Clear")
                        }
                    }
                }
            }
            item {
                SettingsCard("Custom Colors") {
                    Text("Use hex values like `#20272B`. Leave blank to inherit the preset theme.", style = MaterialTheme.typography.bodyMedium)
                    LabeledField("Background", settings.backgroundHex) {
                        settingsStore.saveBackgroundHex(it)
                        refresh()
                    }
                    LabeledField("Key", settings.keyHex) {
                        settingsStore.saveKeyHex(it)
                        refresh()
                    }
                    LabeledField("Aux key", settings.auxiliaryKeyHex) {
                        settingsStore.saveAuxiliaryKeyHex(it)
                        refresh()
                    }
                    LabeledField("Accent", settings.accentHex) {
                        settingsStore.saveAccentHex(it)
                        refresh()
                    }
                    LabeledField("Utility", settings.utilityHex) {
                        settingsStore.saveUtilityHex(it)
                        refresh()
                    }
                    LabeledField("Panel", settings.panelHex) {
                        settingsStore.savePanelHex(it)
                        refresh()
                    }
                    LabeledField("Text", settings.textHex) {
                        settingsStore.saveTextHex(it)
                        refresh()
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCard() {
    SettingsCard("SeekerKeyboard") {
        Text("Private keyboard with discreet wallet, clipboard, theme, layout, and settings controls embedded directly in the typing surface.", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
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

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        TextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
