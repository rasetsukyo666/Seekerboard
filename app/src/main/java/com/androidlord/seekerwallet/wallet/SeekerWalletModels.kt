package com.androidlord.seekerwallet.wallet

import androidx.compose.ui.graphics.Color

enum class SolanaCluster(
    val label: String,
    val rpcUrl: String,
) {
    DEVNET(
        label = "Devnet",
        rpcUrl = "https://api.devnet.solana.com",
    ),
    MAINNET(
        label = "Mainnet",
        rpcUrl = "https://api.mainnet-beta.solana.com",
    ),
}

data class SeekerWalletUiState(
    val profileName: String = "SeekerWallet",
    val jurisdictionHint: String = "Seeker-first Solana wallet shell with keyboard launch hooks.",
    val riskBanner: String = "Keyboard access should launch review and signing, not silently sign inside the IME surface.",
    val cluster: SolanaCluster = SolanaCluster.DEVNET,
    val walletAddress: String? = null,
    val authLabel: String = "No wallet connected",
    val totalBalanceUsd: String = "$0.00",
    val readyCashoutText: String = "Wallet launch rail ready",
    val receiveUri: String = "",
    val receiveAmountSol: String = "0.05",
    val receiveMemo: String = "SeekerWallet receive request",
    val draftRecipient: String = "",
    val draftAmountSol: String = "0.01",
    val draftMemo: String = "SeekerWallet transfer",
    val skrStakeAmount: String = "1",
    val skrUnstakeAmount: String = "1",
    val isBusy: Boolean = false,
    val isRefreshing: Boolean = false,
    val assets: List<WalletAsset> = defaultAssets(),
    val stakeAccounts: List<NativeStakeAccount> = emptyList(),
    val skrPosition: SkrPosition = SkrPosition(),
    val rails: List<SupportRail> = defaultRails(),
    val checklist: List<SeekerChecklistItem> = defaultChecklist(),
    val contacts: List<RecoveryContact> = defaultContacts(),
    val statusMessage: String = "Connect a wallet to start reading balances and sending transactions.",
    val lastSignature: String? = null,
)

data class WalletAsset(
    val symbol: String,
    val name: String,
    val balance: String,
    val fiatValue: String,
    val accent: Color,
)

data class SupportRail(
    val title: String,
    val description: String,
    val tag: String,
)

data class NativeStakeAccount(
    val pubkey: String,
    val lamports: Long,
    val stakeState: String = "unknown",
    val delegationVote: String? = null,
    val activationEpoch: String? = null,
    val deactivationEpoch: String? = null,
    val canStake: Boolean = false,
    val canWithdraw: Boolean = false,
)

data class SkrPosition(
    val apyLabel: String = "APY syncing",
    val stakedAmount: String = "0",
    val unstakingAmount: String = "0",
    val withdrawableAmount: String = "0",
    val availableBalance: String = "0",
    val lastFeeLabel: String = "",
)

data class SeekerChecklistItem(
    val title: String,
    val description: String,
    val complete: Boolean,
)

data class RecoveryContact(
    val name: String,
    val role: String,
    val responseWindow: String,
)

private fun defaultAssets(): List<WalletAsset> {
    return listOf(
        WalletAsset(
            symbol = "SOL",
            name = "Network balance",
            balance = "0.00 SOL",
            fiatValue = "$0.00",
            accent = Color(0xFF00C2A8),
        ),
        WalletAsset(
            symbol = "TOKENS",
            name = "SPL token accounts",
            balance = "Connect wallet to load",
            fiatValue = "RPC ready",
            accent = Color(0xFF1D7FF2),
        )
    )
}

private fun defaultRails(): List<SupportRail> {
    return listOf(
        SupportRail(
            title = "Keyboard wallet button",
            description = "Launch wallet review from the keyboard instead of signing inside the IME.",
            tag = "IME",
        ),
        SupportRail(
            title = "SKR fee lane",
            description = "Reserve a dedicated transfer lane for optional SKR platform fees around wallet actions.",
            tag = "Fees",
        ),
        SupportRail(
            title = "Stake and unstake flow",
            description = "Manage native stake accounts and route official SKR stake, unstake, and withdraw transactions through wallet review.",
            tag = "Staking",
        )
    )
}

private fun defaultChecklist(): List<SeekerChecklistItem> {
    return listOf(
        SeekerChecklistItem(
            title = "Wallet review screen",
            description = "Every transfer or staking action needs a review surface outside the keyboard.",
            complete = true,
        ),
        SeekerChecklistItem(
            title = "MWA connection test",
            description = "Verify Seeker, Seed Vault Wallet, Phantom, or Solflare can connect over Mobile Wallet Adapter.",
            complete = false,
        ),
        SeekerChecklistItem(
            title = "Stake flow extraction",
            description = "Move SOL/SKR transaction builders into a reusable module for the keyboard launcher and companion app.",
            complete = false,
        )
    )
}

fun formatLamportsAsSol(lamports: Long): String {
    return String.format(java.util.Locale.US, "%.4f SOL", lamports / 1_000_000_000.0)
}

fun isStakeInactive(account: NativeStakeAccount): Boolean {
    return account.stakeState == "inactive" || account.stakeState == "undelegated" || account.stakeState == "initialized"
}

private fun defaultContacts(): List<RecoveryContact> {
    return listOf(
        RecoveryContact(
            name = "Wallet companion app",
            role = "Review and signing surface",
            responseWindow = "Immediate",
        ),
        RecoveryContact(
            name = "Keyboard extension",
            role = "Quick launcher and wallet shortcut",
            responseWindow = "Immediate",
        ),
        RecoveryContact(
            name = "RPC fallback",
            role = "Chain reads and portfolio refresh",
            responseWindow = "30 second target",
        )
    )
}
