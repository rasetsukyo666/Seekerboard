package com.androidlord.seekerwallet.wallet

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.androidlord.seekerwallet.theme.LocalSeekerPalette

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SeekerWalletScreen(
    modifier: Modifier = Modifier,
    viewModel: SeekerWalletViewModel = viewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val activity = context.findActivity()
    val palette = LocalSeekerPalette.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = Color.Transparent,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        listOf(palette.backdropTop, palette.backdropBottom)
                    )
                )
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
            ) {
                item {
                    HeroCard(state = state)
                }
                item {
                    ClusterCard(
                        cluster = state.cluster,
                        onClusterSelected = viewModel::updateCluster,
                    )
                }
                item {
                    WalletActionsCard(
                        state = state,
                        onConnect = { activity?.let(viewModel::connectWallet) },
                        onDisconnect = { activity?.let(viewModel::disconnectWallet) },
                        onRefresh = viewModel::refreshPortfolio,
                        onSignIn = { activity?.let(viewModel::signIn) },
                        onSignMessage = { activity?.let(viewModel::signMessage) },
                        onAirdrop = viewModel::requestAirdrop,
                    )
                }
                item {
                    ReceiveCard(
                        state = state,
                        onAmountChange = viewModel::updateReceiveAmount,
                        onMemoChange = viewModel::updateReceiveMemo,
                    )
                }
                item {
                    SendCard(
                        state = state,
                        onRecipientChange = viewModel::updateRecipient,
                        onAmountChange = viewModel::updateAmount,
                        onMemoChange = viewModel::updateMemo,
                        onSend = { activity?.let(viewModel::sendTransfer) },
                    )
                }
                item {
                    RoadmapCard()
                }
                item {
                    SectionTitle("Portfolio")
                }
                items(state.assets, key = { it.symbol + it.name }) { asset ->
                    AssetCard(asset = asset)
                }
                item {
                    SectionTitle("Integration Lanes")
                }
                items(state.rails, key = { it.title }) { rail ->
                    SupportRailCard(rail = rail)
                }
                item {
                    SectionTitle("Status")
                }
                item {
                    StatusCard(message = state.statusMessage, lastSignature = state.lastSignature)
                }
            }
        }
    }
}

@Composable
private fun HeroCard(state: SeekerWalletUiState) {
    val palette = LocalSeekerPalette.current
    Card(
        colors = CardDefaults.cardColors(containerColor = palette.heroCard),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = state.profileName,
                color = palette.heroText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = state.jurisdictionHint,
                color = palette.heroText.copy(alpha = 0.82f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Surface(
                color = palette.heroBadge,
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    text = state.riskBanner,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = palette.heroText,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun ClusterCard(
    cluster: SolanaCluster,
    onClusterSelected: (SolanaCluster) -> Unit,
) {
    InfoCard(title = "Cluster") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SolanaCluster.entries.forEach { option ->
                AssistChip(
                    onClick = { onClusterSelected(option) },
                    label = { Text(option.label) },
                    enabled = option != cluster,
                )
            }
        }
    }
}

@Composable
private fun WalletActionsCard(
    state: SeekerWalletUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onSignIn: () -> Unit,
    onSignMessage: () -> Unit,
    onAirdrop: () -> Unit,
) {
    InfoCard(title = "Wallet Control") {
        Text(state.authLabel, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = state.walletAddress?.let(::shortAddress) ?: "No wallet address loaded",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.walletAddress == null) {
                Button(onClick = onConnect, enabled = !state.isBusy) {
                    Text("Connect Wallet")
                }
            } else {
                Button(onClick = onRefresh, enabled = !state.isBusy) {
                    Text("Refresh")
                }
                OutlinedButton(onClick = onDisconnect, enabled = !state.isBusy) {
                    Text("Disconnect")
                }
            }
            OutlinedButton(onClick = onSignIn, enabled = !state.isBusy) {
                Text("Sign In")
            }
            OutlinedButton(onClick = onSignMessage, enabled = !state.isBusy && state.walletAddress != null) {
                Text("Sign Message")
            }
            OutlinedButton(onClick = onAirdrop, enabled = !state.isBusy && state.cluster == SolanaCluster.DEVNET) {
                Text("Airdrop")
            }
        }
    }
}

@Composable
private fun ReceiveCard(
    state: SeekerWalletUiState,
    onAmountChange: (String) -> Unit,
    onMemoChange: (String) -> Unit,
) {
    InfoCard(title = "Receive") {
        Text(
            text = state.walletAddress ?: "Connect a wallet to generate a receive request.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = state.receiveAmountSol,
            onValueChange = onAmountChange,
            label = { Text("Amount (SOL)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = state.receiveMemo,
            onValueChange = onMemoChange,
            label = { Text("Memo") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Text(
            text = state.receiveUri.ifBlank { "solana:..." },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SendCard(
    state: SeekerWalletUiState,
    onRecipientChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onMemoChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    InfoCard(title = "Send") {
        OutlinedTextField(
            value = state.draftRecipient,
            onValueChange = onRecipientChange,
            label = { Text("Recipient") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = state.draftAmountSol,
            onValueChange = onAmountChange,
            label = { Text("Amount (SOL)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = state.draftMemo,
            onValueChange = onMemoChange,
            label = { Text("Memo") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Button(
            onClick = onSend,
            enabled = !state.isBusy && state.walletAddress != null,
        ) {
            Text("Review In Wallet")
        }
    }
}

@Composable
private fun RoadmapCard() {
    InfoCard(title = "Staking + Keyboard Roadmap") {
        Text(
            text = "Phase 1 keeps transfers and wallet auth live. Phase 2 extracts SOL staking, official SKR staking, and SKR fee routing into a shared module. Phase 3 adds an IME launcher that hands off to a secure review screen.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun AssetCard(asset: WalletAsset) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(asset.symbol, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(asset.name, style = MaterialTheme.typography.bodyMedium)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(asset.balance, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(asset.fiatValue, color = asset.accent, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SupportRailCard(rail: SupportRail) {
    InfoCard(title = rail.title, compact = true) {
        Text(rail.description, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = rail.tag,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun StatusCard(
    message: String,
    lastSignature: String?,
) {
    InfoCard(title = "Wallet Status", compact = true) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        if (!lastSignature.isNullOrBlank()) {
            Text(
                text = "Last signature: ${shortAddress(lastSignature)}",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun InfoCard(
    title: String,
    compact: Boolean = false,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
        shape = RoundedCornerShape(if (compact) 22.dp else 26.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compact) 16.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

private fun shortAddress(value: String): String {
    return if (value.length <= 12) value else "${value.take(6)}...${value.takeLast(6)}"
}

private fun Context.findActivity(): ComponentActivity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return (current as? Activity) as? ComponentActivity
}
