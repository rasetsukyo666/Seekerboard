package com.androidlord.seekerwallet

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.androidlord.seekerwallet.data.WalletSessionStore
import com.androidlord.seekerwallet.wallet.NativeStakeAccount
import com.androidlord.seekerwallet.wallet.OfficialUserStakeState
import com.androidlord.seekerwallet.wallet.PortfolioSnapshot
import com.androidlord.seekerwallet.wallet.SkrOfficialService
import com.androidlord.seekerwallet.wallet.SkrPosition
import com.androidlord.seekerwallet.wallet.SolanaCluster
import com.androidlord.seekerwallet.wallet.SolanaRpcService
import com.androidlord.seekerwallet.wallet.isStakeInactive
import com.funkatronics.encoders.Base58
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

class WalletBridgeActivity : ComponentActivity() {
    private lateinit var sessionStore: WalletSessionStore
    private val rpcService = SolanaRpcService()
    private val skrOfficialService = SkrOfficialService()
    private val walletAdapter by lazy {
        MobileWalletAdapter(
            connectionIdentity = ConnectionIdentity(
                identityUri = Uri.parse(IDENTITY_URI),
                iconUri = Uri.parse(ICON_URI),
                identityName = IDENTITY_NAME,
            )
        ).apply {
            authToken = sessionStore.loadAuthToken()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionStore = WalletSessionStore(applicationContext)
        lifecycleScope.launch {
            runCatching {
                when (intent?.getStringExtra("wallet_action").orEmpty()) {
                    "connect" -> handleConnect()
                    "disconnect" -> handleDisconnect()
                    else -> handleRefresh()
                }
            }.onFailure { error ->
                sessionStore.saveKeyboardStatus(error.message ?: "Keyboard wallet action failed.")
            }
            finish()
        }
    }

    private suspend fun handleConnect() {
        val sender = ActivityResultSender(this)
        when (val result = walletAdapter.connect(sender)) {
            is TransactionResult.Success -> {
                val address = Base58.encodeToString(result.authResult.accounts.first().publicKey)
                sessionStore.saveWalletAddress(address)
                sessionStore.saveAuthToken(result.authResult.authToken)
                walletAdapter.authToken = result.authResult.authToken
                sessionStore.saveKeyboardStatus("Connected ${shortAddress(address)}. Syncing wallet snapshot…")
                refreshSnapshot(address, sessionStore.loadCluster())
            }
            is TransactionResult.NoWalletFound -> {
                sessionStore.saveKeyboardStatus("No MWA wallet found on this device.")
            }
            is TransactionResult.Failure -> {
                sessionStore.saveKeyboardStatus("Wallet connection failed: ${result.e.message}")
            }
        }
    }

    private suspend fun handleDisconnect() {
        val sender = ActivityResultSender(this)
        when (val result = walletAdapter.disconnect(sender)) {
            is TransactionResult.Success, is TransactionResult.NoWalletFound -> {
                sessionStore.clearSession()
                walletAdapter.authToken = null
                sessionStore.saveKeyboardStatus("Keyboard wallet session cleared.")
            }
            is TransactionResult.Failure -> {
                sessionStore.saveKeyboardStatus("Disconnect failed: ${result.e.message}")
            }
        }
    }

    private suspend fun handleRefresh() {
        val address = sessionStore.loadWalletAddress()
        if (address.isNullOrBlank()) {
            sessionStore.saveKeyboardStatus("No wallet connected.")
            return
        }
        sessionStore.saveKeyboardStatus("Refreshing ${shortAddress(address)} from ${sessionStore.loadCluster().label}…")
        refreshSnapshot(address, sessionStore.loadCluster())
    }

    private suspend fun refreshSnapshot(
        address: String,
        cluster: SolanaCluster,
    ) {
        val bundle = coroutineScope {
            val portfolioDeferred = async {
                rpcService.loadPortfolio(cluster.rpcUrl, address)
            }
            val stakeDeferred = async {
                runCatching {
                    rpcService.fetchStakeAccounts(cluster.rpcUrl, address)
                }.getOrDefault(emptyList())
            }
            val skrDeferred = async {
                runCatching { skrOfficialService.fetchUserStake(address) }.getOrNull()
            }
            val apyDeferred = async {
                runCatching { skrOfficialService.fetchCurrentApy() }.getOrNull()
            }
            KeyboardPortfolioBundle(
                portfolio = portfolioDeferred.await(),
                stakeAccounts = stakeDeferred.await(),
                skrState = skrDeferred.await(),
                skrApy = apyDeferred.await(),
            )
        }

        val totalBalanceUsd = "$" + TWO_DECIMAL.format(bundle.portfolio.solBalance * SOL_PRICE_HINT)
        val skrPosition = buildSkrPosition(bundle.skrState, bundle.skrApy)
        sessionStore.saveNativeStakeAccountCount(bundle.stakeAccounts.size)
        sessionStore.saveWalletPanelSnapshot(
            totalBalanceUsd = totalBalanceUsd,
            skrPosition = skrPosition,
            stakeAccounts = bundle.stakeAccounts,
            eligibleConsolidationSources = estimateEligibleConsolidationSources(bundle.stakeAccounts),
        )
        sessionStore.saveKeyboardStatus("Synced ${shortAddress(address)} on ${cluster.label}.")
    }

    private fun buildSkrPosition(
        skrState: OfficialUserStakeState?,
        apy: Double?,
    ): SkrPosition {
        return SkrPosition(
            apyLabel = if (apy == null) "APY unavailable" else "${TWO_DECIMAL.format(apy)}% APY",
            stakedAmount = skrState?.stakedAmountForDisplay ?: "0",
            unstakingAmount = skrState?.unstakingAmount ?: "0",
            withdrawableAmount = skrState?.withdrawableAmountForDisplay ?: "0",
            availableBalance = skrState?.availableBalance ?: "0",
        )
    }

    private fun estimateEligibleConsolidationSources(accounts: List<NativeStakeAccount>): Int {
        val delegated = accounts.filter { it.delegationVote != null && !isStakeInactive(it) }
        val largestValidatorGroup = delegated.groupBy { it.delegationVote }.maxOfOrNull { it.value.size } ?: 0
        return (largestValidatorGroup - 1).coerceAtLeast(0)
    }

    private fun shortAddress(address: String): String {
        return if (address.length <= 10) address else address.take(4) + "…" + address.takeLast(4)
    }

    private companion object {
        const val IDENTITY_URI = "https://github.com/androidlord666/seekerkeyboard"
        const val ICON_URI = "/favicon.ico"
        const val IDENTITY_NAME = "SeekerKeyboard"
        const val SOL_PRICE_HINT = 90.0
        val TWO_DECIMAL = DecimalFormat("0.00", DecimalFormatSymbols(Locale.US))
    }
}

private data class KeyboardPortfolioBundle(
    val portfolio: PortfolioSnapshot,
    val stakeAccounts: List<NativeStakeAccount>,
    val skrState: OfficialUserStakeState?,
    val skrApy: Double?,
)
