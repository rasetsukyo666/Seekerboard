package com.androidlord.seekerwallet.wallet

import android.app.Application
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.androidlord.seekerwallet.data.WalletSessionStore
import com.funkatronics.encoders.Base58
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.SignInWithSolana
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.Message
import com.solana.transaction.SystemProgram
import com.solana.transaction.Transaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

class SeekerWalletViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionStore = WalletSessionStore(application)
    private val rpcService = SolanaRpcService()
    private val walletAdapter = MobileWalletAdapter(
        connectionIdentity = ConnectionIdentity(
            identityUri = Uri.parse("https://github.com/androidlord666/Matsukaze"),
            iconUri = Uri.parse("/favicon.ico"),
            identityName = "SeekerWallet",
        )
    ).apply {
        authToken = sessionStore.loadAuthToken()
    }

    private val _state = MutableStateFlow(
        SeekerWalletUiState(
            cluster = sessionStore.loadCluster(),
            walletAddress = sessionStore.loadWalletAddress(),
            authLabel = if (sessionStore.loadWalletAddress().isNullOrBlank()) {
                "No wallet connected"
            } else {
                "Restored prior wallet session"
            },
            receiveUri = buildReceiveUri(
                address = sessionStore.loadWalletAddress(),
                amountSol = SeekerWalletUiState().receiveAmountSol,
                memo = SeekerWalletUiState().receiveMemo,
            ),
        )
    )
    val state: StateFlow<SeekerWalletUiState> = _state.asStateFlow()

    init {
        if (!state.value.walletAddress.isNullOrBlank()) {
            refreshPortfolio()
        }
    }

    fun updateRecipient(value: String) {
        _state.update { it.copy(draftRecipient = value) }
    }

    fun updateReceiveAmount(value: String) {
        _state.update {
            it.copy(
                receiveAmountSol = value,
                receiveUri = buildReceiveUri(
                    address = it.walletAddress,
                    amountSol = value,
                    memo = it.receiveMemo,
                ),
            )
        }
    }

    fun updateReceiveMemo(value: String) {
        _state.update {
            it.copy(
                receiveMemo = value,
                receiveUri = buildReceiveUri(
                    address = it.walletAddress,
                    amountSol = it.receiveAmountSol,
                    memo = value,
                ),
            )
        }
    }

    fun updateAmount(value: String) {
        _state.update { it.copy(draftAmountSol = value) }
    }

    fun updateMemo(value: String) {
        _state.update { it.copy(draftMemo = value) }
    }

    fun updateCluster(cluster: SolanaCluster) {
        sessionStore.saveCluster(cluster)
        _state.update {
            it.copy(
                cluster = cluster,
                statusMessage = "Switched to ${cluster.label}. Refresh to reload balances.",
            )
        }
        if (state.value.walletAddress != null) {
            refreshPortfolio()
        }
    }

    fun connectWallet(activity: ComponentActivity) {
        runWalletAction("Connecting wallet…") {
            val sender = ActivityResultSender(activity)
            when (val result = walletAdapter.connect(sender)) {
                is TransactionResult.Success -> {
                    val address = Base58.encodeToString(result.authResult.accounts.first().publicKey)
                    persistSession(address, result.authResult.authToken)
                    _state.update {
                        it.copy(
                            walletAddress = address,
                            authLabel = "Connected via Mobile Wallet Adapter",
                            receiveUri = buildReceiveUri(
                                address = address,
                                amountSol = it.receiveAmountSol,
                                memo = it.receiveMemo,
                            ),
                            statusMessage = "Wallet connected. Refreshing portfolio…",
                            isBusy = false,
                        )
                    }
                    refreshPortfolio()
                }
                is TransactionResult.NoWalletFound -> {
                    _state.update {
                        it.copy(
                            isBusy = false,
                            statusMessage = "No MWA wallet found. Install Seeker, Seed Vault Wallet, Phantom, or Solflare.",
                        )
                    }
                }
                is TransactionResult.Failure -> {
                    failAction("Wallet connection failed: ${result.e.message}")
                }
            }
        }
    }

    fun disconnectWallet(activity: ComponentActivity) {
        runWalletAction("Disconnecting wallet…") {
            val sender = ActivityResultSender(activity)
            when (val result = walletAdapter.disconnect(sender)) {
                is TransactionResult.Success -> {
                    sessionStore.clearSession()
                    walletAdapter.authToken = null
                    val cluster = state.value.cluster
                    _state.value = SeekerWalletUiState(
                        cluster = cluster,
                        statusMessage = "Wallet disconnected.",
                    )
                    sessionStore.saveCluster(cluster)
                }
                is TransactionResult.NoWalletFound -> {
                    sessionStore.clearSession()
                    walletAdapter.authToken = null
                    val cluster = state.value.cluster
                    _state.value = SeekerWalletUiState(
                        cluster = cluster,
                        statusMessage = "Wallet session cleared.",
                    )
                    sessionStore.saveCluster(cluster)
                }
                is TransactionResult.Failure -> {
                    failAction("Disconnect failed: ${result.e.message}")
                }
            }
        }
    }

    fun refreshPortfolio() {
        val address = state.value.walletAddress ?: return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isRefreshing = true,
                    statusMessage = "Refreshing ${it.cluster.label} balances…",
                )
            }
            runCatching {
                rpcService.loadPortfolio(
                    rpcUrl = state.value.cluster.rpcUrl,
                    ownerAddress = address,
                )
            }.onSuccess { snapshot ->
                val solUsd = snapshot.solBalance * SOL_PRICE_HINT
                val tokenAssets = snapshot.tokenHoldings.take(6).map { holding ->
                    WalletAsset(
                        symbol = holding.symbol,
                        name = if (holding.symbol == "USDC") "Stablecoin balance" else holding.mint,
                        balance = "${holding.amount} ${holding.symbol}",
                        fiatValue = if (holding.symbol == "USDC") {
                            "$${holding.amount}"
                        } else {
                            "SPL token"
                        },
                        accent = if (holding.symbol == "USDC") ColorPalette.USDC else ColorPalette.ACCENT,
                    )
                }
                _state.update {
                    it.copy(
                        isRefreshing = false,
                        totalBalanceUsd = "$" + TWO_DECIMAL.format(solUsd),
                        readyCashoutText = if (snapshot.tokenHoldings.isEmpty()) {
                            "SOL ready on ${it.cluster.label}"
                        } else {
                            "${snapshot.tokenHoldings.size} token account(s) loaded"
                        },
                        assets = listOf(
                            WalletAsset(
                                symbol = "SOL",
                                name = "Wallet base layer",
                                balance = "${SIX_DECIMAL.format(snapshot.solBalance)} SOL",
                                fiatValue = "$" + TWO_DECIMAL.format(solUsd),
                                accent = ColorPalette.ACCENT,
                            )
                        ) + if (tokenAssets.isEmpty()) {
                            listOf(
                                WalletAsset(
                                    symbol = "TOKENS",
                                    name = "SPL token accounts",
                                    balance = "No funded token accounts found",
                                    fiatValue = it.cluster.label,
                                    accent = ColorPalette.USDC,
                                )
                            )
                        } else {
                            tokenAssets
                        },
                        statusMessage = "Portfolio synced from ${it.cluster.label}.",
                    )
                }
            }.onFailure { error ->
                failAction("Refresh failed: ${error.message}")
                _state.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun signIn(activity: ComponentActivity) {
        runWalletAction("Signing in with Solana…") {
            val sender = ActivityResultSender(activity)
            when (val result = walletAdapter.signIn(
                sender,
                SignInWithSolana.Payload("github.com", "Sign in to SeekerWallet"),
            )) {
                is TransactionResult.Success -> {
                    val address = Base58.encodeToString(result.authResult.accounts.first().publicKey)
                    persistSession(address, result.authResult.authToken)
                    _state.update {
                        it.copy(
                            walletAddress = address,
                            authLabel = "Signed in with Solana",
                            receiveUri = buildReceiveUri(
                                address = address,
                                amountSol = it.receiveAmountSol,
                                memo = it.receiveMemo,
                            ),
                            statusMessage = "Sign-in completed for $address",
                            isBusy = false,
                        )
                    }
                }
                is TransactionResult.NoWalletFound -> {
                    _state.update {
                        it.copy(isBusy = false, statusMessage = "No compatible wallet app found for SIWS.")
                    }
                }
                is TransactionResult.Failure -> {
                    failAction("Sign-in failed: ${result.e.message}")
                }
            }
        }
    }

    fun signMessage(activity: ComponentActivity) {
        val currentAddress = state.value.walletAddress ?: return
        runWalletAction("Requesting message signature…") {
            val sender = ActivityResultSender(activity)
            when (val result = walletAdapter.transact(sender) { authResult ->
                signMessagesDetached(
                    arrayOf("SeekerWallet verification".toByteArray()),
                    arrayOf(authResult.accounts.first().publicKey),
                )
            }) {
                is TransactionResult.Success -> {
                    val signature = result.successPayload
                        ?.messages
                        ?.firstOrNull()
                        ?.signatures
                        ?.firstOrNull()
                        ?.let { Base58.encodeToString(it) }
                    _state.update {
                        it.copy(
                            isBusy = false,
                            statusMessage = "Message signed by $currentAddress",
                            lastSignature = signature,
                        )
                    }
                }
                is TransactionResult.NoWalletFound -> {
                    _state.update { it.copy(isBusy = false, statusMessage = "No compatible wallet app found.") }
                }
                is TransactionResult.Failure -> {
                    failAction("Message signing failed: ${result.e.message}")
                }
            }
        }
    }

    fun requestAirdrop() {
        val address = state.value.walletAddress ?: return
        if (state.value.cluster != SolanaCluster.DEVNET) {
            _state.update { it.copy(statusMessage = "Airdrop is only available on Devnet.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, statusMessage = "Requesting 1 SOL devnet airdrop…") }
            runCatching {
                rpcService.requestAirdrop(
                    rpcUrl = state.value.cluster.rpcUrl,
                    ownerAddress = address,
                    lamports = 1_000_000_000L,
                )
            }.onSuccess { signature ->
                _state.update {
                    it.copy(
                        isBusy = false,
                        statusMessage = "Airdrop requested. Signature: $signature",
                        lastSignature = signature,
                    )
                }
                refreshPortfolio()
            }.onFailure { error ->
                failAction("Airdrop failed: ${error.message}")
            }
        }
    }

    fun sendTransfer(activity: ComponentActivity) {
        val draft = state.value
        val senderAddress = draft.walletAddress ?: return
        val recipient = draft.draftRecipient.trim()
        val amountLamports = draft.draftAmountSol.toDoubleOrNull()
            ?.times(1_000_000_000.0)
            ?.toLong()
            ?: 0L
        if (recipient.isBlank() || amountLamports <= 0L) {
            _state.update { it.copy(statusMessage = "Enter a valid recipient and SOL amount before sending.") }
            return
        }

        runWalletAction("Preparing signed SOL transfer…") {
            val sender = ActivityResultSender(activity)
            val blockhash = rpcService.getLatestBlockhash(state.value.cluster.rpcUrl)
            when (val result = walletAdapter.transact(sender) { authResult ->
                val userAddress = SolanaPublicKey(authResult.accounts.first().publicKey)
                val transferTx = Transaction(
                    Message.Builder()
                        .addInstruction(
                            SystemProgram.transfer(
                                userAddress,
                                SolanaPublicKey(recipient),
                                amountLamports,
                            )
                        )
                        .setRecentBlockhash(blockhash)
                        .build()
                )
                signAndSendTransactions(arrayOf(transferTx.serialize()))
            }) {
                is TransactionResult.Success -> {
                    val signature = result.successPayload?.signatures?.firstOrNull()?.let(Base58::encodeToString)
                    _state.update {
                        it.copy(
                            isBusy = false,
                            statusMessage = "Transfer submitted from $senderAddress",
                            lastSignature = signature,
                        )
                    }
                    refreshPortfolio()
                }
                is TransactionResult.NoWalletFound -> {
                    _state.update { it.copy(isBusy = false, statusMessage = "No compatible wallet app found.") }
                }
                is TransactionResult.Failure -> {
                    failAction("Transfer failed: ${result.e.message}")
                }
            }
        }
    }

    private fun persistSession(
        address: String,
        authToken: String?,
    ) {
        sessionStore.saveWalletAddress(address)
        sessionStore.saveAuthToken(authToken)
        walletAdapter.authToken = authToken
    }

    private fun buildReceiveUri(
        address: String?,
        amountSol: String,
        memo: String,
    ): String {
        if (address.isNullOrBlank()) return ""
        val params = buildList {
            amountSol.takeIf { it.toDoubleOrNull() != null }?.let { add("amount=$it") }
            add("label=SeekerWallet")
            memo.takeIf { it.isNotBlank() }?.let { add("message=${Uri.encode(it)}") }
        }
        return "solana:$address?${params.joinToString("&")}"
    }

    private fun runWalletAction(
        status: String,
        action: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, statusMessage = status) }
            runCatching { action() }
                .onFailure { error ->
                    failAction(error.message ?: "Wallet action failed.")
                }
        }
    }

    private fun failAction(message: String) {
        _state.update {
            it.copy(
                isBusy = false,
                isRefreshing = false,
                statusMessage = message,
            )
        }
    }

    private object ColorPalette {
        val ACCENT = androidx.compose.ui.graphics.Color(0xFF00C2A8)
        val USDC = androidx.compose.ui.graphics.Color(0xFF1D7FF2)
    }

    private companion object {
        val TWO_DECIMAL = java.text.DecimalFormat("0.00", java.text.DecimalFormatSymbols(Locale.US))
        val SIX_DECIMAL = java.text.DecimalFormat("0.000000", java.text.DecimalFormatSymbols(Locale.US))
        const val SOL_PRICE_HINT = 90.0
    }
}
