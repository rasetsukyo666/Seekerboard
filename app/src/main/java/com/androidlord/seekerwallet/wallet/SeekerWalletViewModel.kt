package com.androidlord.seekerwallet.wallet

import android.app.Application
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.androidlord.seekerkeyboard.ime.KeyboardSettingsStore
import com.androidlord.seekerkeyboard.ime.KeyboardTheme
import com.androidlord.seekerkeyboard.ime.UnifiedAccountPreview
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Base64
import java.util.Locale

class SeekerWalletViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionStore = WalletSessionStore(application)
    private val keyboardSettingsStore = KeyboardSettingsStore(application)
    private val rpcService = SolanaRpcService()
    private val nativeStakeService = NativeStakeService()
    private val skrOfficialService = SkrOfficialService()
    private val walletAdapter = MobileWalletAdapter(
        connectionIdentity = ConnectionIdentity(
            identityUri = Uri.parse(IDENTITY_URI),
            iconUri = Uri.parse(ICON_URI),
            identityName = IDENTITY_NAME,
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
            keyboardSettings = keyboardSettingsStore.load(),
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

    fun updateSkrStakeAmount(value: String) {
        _state.update { it.copy(skrStakeAmount = value) }
    }

    fun updateSkrUnstakeAmount(value: String) {
        _state.update { it.copy(skrUnstakeAmount = value) }
    }

    fun updateKeyboardTheme(theme: KeyboardTheme) {
        keyboardSettingsStore.saveTheme(theme)
        _state.update { it.copy(keyboardSettings = keyboardSettingsStore.load(), statusMessage = "Keyboard theme updated.") }
    }

    fun updateKeyboardNumberRow(enabled: Boolean) {
        keyboardSettingsStore.saveNumberRow(enabled)
        _state.update { it.copy(keyboardSettings = keyboardSettingsStore.load(), statusMessage = "Keyboard number row updated.") }
    }

    fun updateKeyboardWalletKey(enabled: Boolean) {
        keyboardSettingsStore.saveWalletKey(enabled)
        _state.update { it.copy(keyboardSettings = keyboardSettingsStore.load(), statusMessage = "Wallet key visibility updated.") }
    }

    fun updateKeyboardHaptics(enabled: Boolean) {
        keyboardSettingsStore.saveHapticsEnabled(enabled)
        _state.update { it.copy(keyboardSettings = keyboardSettingsStore.load(), statusMessage = "Keyboard haptics updated.") }
    }

    fun updateKeyboardKeyHeight(delta: Int) {
        val next = (_state.value.keyboardSettings.keyHeightDp + delta).coerceIn(40, 76)
        keyboardSettingsStore.saveKeyHeightDp(next)
        _state.update { it.copy(keyboardSettings = keyboardSettingsStore.load(), statusMessage = "Keyboard height updated.") }
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
                is TransactionResult.Failure -> failAction("Wallet connection failed: ${result.e.message}")
            }
        }
    }

    fun disconnectWallet(activity: ComponentActivity) {
        runWalletAction("Disconnecting wallet…") {
            val sender = ActivityResultSender(activity)
            when (val result = walletAdapter.disconnect(sender)) {
                is TransactionResult.Success, is TransactionResult.NoWalletFound -> {
                    sessionStore.clearSession()
                    walletAdapter.authToken = null
                    val cluster = state.value.cluster
                    _state.value = SeekerWalletUiState(
                        cluster = cluster,
                        statusMessage = if (result is TransactionResult.Success) "Wallet disconnected." else "Wallet session cleared.",
                    )
                    sessionStore.saveCluster(cluster)
                }
                is TransactionResult.Failure -> failAction("Disconnect failed: ${result.e.message}")
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
                coroutineScope {
                    val portfolioDeferred = async {
                        rpcService.loadPortfolio(
                            rpcUrl = state.value.cluster.rpcUrl,
                            ownerAddress = address,
                        )
                    }
                    val stakeDeferred = async {
                        runCatching {
                            rpcService.fetchStakeAccounts(
                                rpcUrl = state.value.cluster.rpcUrl,
                                ownerAddress = address,
                            )
                        }.getOrDefault(emptyList())
                    }
                    val skrDeferred = async {
                        runCatching { skrOfficialService.fetchUserStake(address) }.getOrNull()
                    }
                    val apyDeferred = async {
                        runCatching { skrOfficialService.fetchCurrentApy() }.getOrNull()
                    }
                    PortfolioBundle(
                        portfolio = portfolioDeferred.await(),
                        stakeAccounts = stakeDeferred.await(),
                        skrState = skrDeferred.await(),
                        skrApy = apyDeferred.await(),
                    )
                }
            }.onSuccess { bundle ->
                val solUsd = bundle.portfolio.solBalance * SOL_PRICE_HINT
                val tokenAssets = bundle.portfolio.tokenHoldings.take(6).map { holding ->
                    WalletAsset(
                        symbol = holding.symbol,
                        name = if (holding.symbol == "USDC") "Stablecoin balance" else holding.mint,
                        balance = "${holding.amount} ${holding.symbol}",
                        fiatValue = if (holding.symbol == "USDC") "$${holding.amount}" else "SPL token",
                        accent = if (holding.symbol == "USDC") ColorPalette.USDC else ColorPalette.ACCENT,
                    )
                }
                _state.update {
                    it.copy(
                        isRefreshing = false,
                        totalBalanceUsd = "$" + TWO_DECIMAL.format(solUsd),
                        readyCashoutText = if (bundle.portfolio.tokenHoldings.isEmpty()) {
                            "SOL ready on ${it.cluster.label}"
                        } else {
                            "${bundle.portfolio.tokenHoldings.size} token account(s) loaded"
                        },
                        assets = listOf(
                            WalletAsset(
                                symbol = "SOL",
                                name = "Wallet base layer",
                                balance = "${SIX_DECIMAL.format(bundle.portfolio.solBalance)} SOL",
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
                        stakeAccounts = bundle.stakeAccounts.sortedByDescending { account -> account.lamports },
                        skrPosition = buildSkrPosition(bundle.skrState, bundle.skrApy),
                        statusMessage = "Portfolio synced from ${it.cluster.label}.",
                    )
                }
                sessionStore.saveNativeStakeAccountCount(bundle.stakeAccounts.size)
                val skrPosition = buildSkrPosition(bundle.skrState, bundle.skrApy)
                sessionStore.saveWalletPanelSnapshot(
                    totalBalanceUsd = "$" + TWO_DECIMAL.format(solUsd),
                    skrPosition = skrPosition,
                    stakeAccounts = bundle.stakeAccounts,
                    eligibleConsolidationSources = estimateEligibleConsolidationSources(bundle.stakeAccounts),
                    unifiedAccounts = buildUnifiedAccounts(bundle.portfolio, bundle.stakeAccounts, skrPosition),
                )
            }.onFailure { error ->
                failAction("Refresh failed: ${error.message}")
                _state.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun signIn(activity: ComponentActivity) {
        runWalletAction("Signing in with Solana…") {
            val sender = ActivityResultSender(activity)
            when (
                val result = walletAdapter.signIn(
                    sender,
                    SignInWithSolana.Payload("github.com", "Sign in to SeekerKeyboard"),
                )
            ) {
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
                    _state.update { it.copy(isBusy = false, statusMessage = "No compatible wallet app found for SIWS.") }
                }
                is TransactionResult.Failure -> failAction("Sign-in failed: ${result.e.message}")
            }
        }
    }

    fun signMessage(activity: ComponentActivity) {
        val currentAddress = state.value.walletAddress ?: return
        runWalletAction("Requesting message signature…") {
            val sender = ActivityResultSender(activity)
            when (val result = walletAdapter.transact(sender) { authResult ->
                signMessagesDetached(
                    arrayOf("SeekerKeyboard verification".toByteArray()),
                    arrayOf(authResult.accounts.first().publicKey),
                )
            }) {
                is TransactionResult.Success -> {
                    val signature = result.successPayload?.messages?.firstOrNull()?.signatures?.firstOrNull()?.let(Base58::encodeToString)
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
                is TransactionResult.Failure -> failAction("Message signing failed: ${result.e.message}")
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
        val senderAddress = state.value.walletAddress ?: return
        val recipient = state.value.draftRecipient.trim()
        val amountLamports = state.value.draftAmountSol.toDoubleOrNull()?.times(1_000_000_000.0)?.toLong() ?: 0L
        if (recipient.isBlank() || amountLamports <= 0L) {
            _state.update { it.copy(statusMessage = "Enter a valid recipient and SOL amount before sending.") }
            return
        }
        runWalletAction("Preparing signed SOL transfer…") {
            val tx = Transaction(
                Message.Builder()
                    .addInstruction(
                        SystemProgram.transfer(
                            SolanaPublicKey(senderAddress),
                            SolanaPublicKey(recipient),
                            amountLamports,
                        )
                    )
                    .setRecentBlockhash(rpcService.getLatestBlockhash(state.value.cluster.rpcUrl))
                    .build()
            )
            submitSerializedTransactions(
                activity = activity,
                serializedTransactions = arrayOf(tx.serialize()),
                successMessage = "Transfer submitted from $senderAddress",
            )
        }
    }

    fun submitSkrStake(activity: ComponentActivity) {
        val address = state.value.walletAddress ?: return
        val rawAmount = uiAmountToRaw(state.value.skrStakeAmount, SKR_DECIMALS)
        if (rawAmount == "0") {
            _state.update { it.copy(statusMessage = "Enter a valid SKR amount before staking.") }
            return
        }
        runWalletAction("Preparing official SKR stake…") {
            val tx = skrOfficialService.buildStakeTx(rawAmount, address, address)
            submitBase64Transaction(activity, tx.transaction, "Official SKR stake submitted.", tx.fee)
        }
    }

    fun submitSkrUnstake(activity: ComponentActivity) {
        val address = state.value.walletAddress ?: return
        val rawAmount = uiAmountToRaw(state.value.skrUnstakeAmount, SKR_DECIMALS)
        if (rawAmount == "0") {
            _state.update { it.copy(statusMessage = "Enter a valid SKR amount before unstaking.") }
            return
        }
        runWalletAction("Preparing official SKR unstake…") {
            val tx = skrOfficialService.buildUnstakeTx(rawAmount, address)
            submitBase64Transaction(activity, tx.transaction, "Official SKR unstake submitted.", tx.fee)
        }
    }

    fun submitSkrWithdraw(activity: ComponentActivity) {
        val address = state.value.walletAddress ?: return
        runWalletAction("Preparing official SKR withdraw…") {
            val tx = skrOfficialService.buildWithdrawTx(address, address)
            submitBase64Transaction(activity, tx.transaction, "Official SKR withdraw submitted.", tx.fee)
        }
    }

    fun delegateNativeStake(activity: ComponentActivity, stakeAccountAddress: String) {
        val address = state.value.walletAddress ?: return
        val validatorVote = NativeStakeService.DEFAULT_VALIDATOR_VOTE[state.value.cluster]
            ?: NativeStakeService.DEFAULT_VALIDATOR_VOTE.getValue(SolanaCluster.MAINNET)
        runWalletAction("Preparing stake delegation…") {
            val tx = nativeStakeService.buildDelegateStakeTx(
                ownerAddress = address,
                stakeAccountAddress = stakeAccountAddress,
                validatorVoteAddress = validatorVote,
                recentBlockhash = rpcService.getLatestBlockhash(state.value.cluster.rpcUrl),
            )
            submitSerializedTransactions(activity, arrayOf(tx.serialize()), "Stake delegation submitted.")
        }
    }

    fun deactivateNativeStake(activity: ComponentActivity, stakeAccountAddress: String) {
        val address = state.value.walletAddress ?: return
        runWalletAction("Preparing stake deactivation…") {
            val tx = nativeStakeService.buildDeactivateStakeTx(
                ownerAddress = address,
                stakeAccountAddress = stakeAccountAddress,
                recentBlockhash = rpcService.getLatestBlockhash(state.value.cluster.rpcUrl),
            )
            submitSerializedTransactions(activity, arrayOf(tx.serialize()), "Stake deactivation submitted.")
        }
    }

    fun withdrawNativeStake(activity: ComponentActivity, stakeAccountAddress: String, lamports: Long) {
        val address = state.value.walletAddress ?: return
        runWalletAction("Preparing stake withdrawal…") {
            val tx = nativeStakeService.buildWithdrawStakeTx(
                ownerAddress = address,
                stakeAccountAddress = stakeAccountAddress,
                destinationAddress = address,
                lamports = lamports,
                recentBlockhash = rpcService.getLatestBlockhash(state.value.cluster.rpcUrl),
            )
            submitSerializedTransactions(activity, arrayOf(tx.serialize()), "Stake withdrawal submitted.")
        }
    }

    private suspend fun submitBase64Transaction(
        activity: ComponentActivity,
        base64Tx: String,
        successMessage: String,
        feeLabel: String?,
    ) {
        submitSerializedTransactions(
            activity = activity,
            serializedTransactions = arrayOf(Base64.getDecoder().decode(base64Tx)),
            successMessage = successMessage,
            feeLabel = feeLabel,
        )
    }

    private suspend fun submitSerializedTransactions(
        activity: ComponentActivity,
        serializedTransactions: Array<ByteArray>,
        successMessage: String,
        feeLabel: String? = null,
    ) {
        val sender = ActivityResultSender(activity)
        when (val result = walletAdapter.transact(sender) {
            val authToken = walletAdapter.authToken
            if (authToken != null) {
                reauthorize(
                    identityUri = Uri.parse(IDENTITY_URI),
                    iconUri = Uri.parse(ICON_URI),
                    identityName = IDENTITY_NAME,
                    authToken = authToken,
                )
            }
            signAndSendTransactions(serializedTransactions)
        }) {
            is TransactionResult.Success -> {
                val signature = result.successPayload?.signatures?.firstOrNull()?.let(Base58::encodeToString)
                _state.update {
                    it.copy(
                        isBusy = false,
                        statusMessage = successMessage + feeLabel?.let { fee -> " Fee: $fee" }.orEmpty(),
                        lastSignature = signature,
                        skrPosition = if (feeLabel.isNullOrBlank()) it.skrPosition else it.skrPosition.copy(lastFeeLabel = feeLabel),
                    )
                }
                refreshPortfolio()
            }
            is TransactionResult.NoWalletFound -> {
                _state.update { it.copy(isBusy = false, statusMessage = "No compatible wallet app found.") }
            }
            is TransactionResult.Failure -> {
                failAction("Transaction failed: ${result.e.message}")
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
            add("label=SeekerKeyboard")
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
            lastFeeLabel = state.value.skrPosition.lastFeeLabel,
        )
    }

    private fun buildUnifiedAccounts(
        portfolio: PortfolioSnapshot,
        stakeAccounts: List<NativeStakeAccount>,
        skrPosition: SkrPosition,
    ): List<UnifiedAccountPreview> {
        val nativeStakeLamports = stakeAccounts.sumOf { stake -> stake.lamports }
        val activeStakeCount = stakeAccounts.count { !isStakeInactive(it) }
        return listOf(
            UnifiedAccountPreview(
                title = "Spendable SOL",
                balanceLabel = "${SIX_DECIMAL.format(portfolio.solBalance)} SOL",
                detailLabel = "$" + TWO_DECIMAL.format(portfolio.solBalance * SOL_PRICE_HINT),
                emphasis = if (portfolio.solBalance > 0.0) "liquid" else "empty",
            ),
            UnifiedAccountPreview(
                title = "Native Stake",
                balanceLabel = "${SIX_DECIMAL.format(nativeStakeLamports / 1_000_000_000.0)} SOL",
                detailLabel = "$activeStakeCount account(s) · Solana Mobile validator",
                emphasis = if (activeStakeCount > 0) "earning" else "idle",
            ),
            UnifiedAccountPreview(
                title = "SKR Position",
                balanceLabel = "${skrPosition.stakedAmount} staked",
                detailLabel = "${skrPosition.withdrawableAmount} ready · ${skrPosition.apyLabel}",
                emphasis = if (skrPosition.stakedAmount != "0") "staked" else "ready",
            ),
            UnifiedAccountPreview(
                title = "Token Accounts",
                balanceLabel = "${portfolio.tokenHoldings.size} tracked",
                detailLabel = portfolio.tokenHoldings.take(2).joinToString(" · ") { "${it.amount} ${it.symbol}" }.ifBlank { "No funded token accounts" },
                emphasis = if (portfolio.tokenHoldings.isNotEmpty()) "loaded" else "quiet",
            ),
        )
    }

    private fun uiAmountToRaw(amountText: String, decimals: Int): String {
        val clean = amountText.trim()
        if (clean.isBlank()) return "0"
        if (!clean.matches(Regex("\\d+(\\.\\d+)?"))) return "0"
        val parts = clean.split('.', limit = 2)
        val whole = parts[0]
        val frac = parts.getOrElse(1) { "" }
        val padded = (frac + "0".repeat(decimals)).take(decimals)
        val raw = (whole + padded).trimStart('0')
        return raw.ifBlank { "0" }
    }

    private object ColorPalette {
        val ACCENT = androidx.compose.ui.graphics.Color(0xFF00C2A8)
        val USDC = androidx.compose.ui.graphics.Color(0xFF1D7FF2)
    }

    private fun estimateEligibleConsolidationSources(accounts: List<NativeStakeAccount>): Int {
        val delegated = accounts.filter { it.delegationVote != null && !isStakeInactive(it) }
        val largestValidatorGroup = delegated.groupBy { it.delegationVote }.maxOfOrNull { it.value.size } ?: 0
        return (largestValidatorGroup - 1).coerceAtLeast(0)
    }

    private companion object {
        const val IDENTITY_URI = "https://github.com/androidlord666/seekerkeyboard"
        const val ICON_URI = "/favicon.ico"
        const val IDENTITY_NAME = "SeekerKeyboard"
        const val SOL_PRICE_HINT = 90.0
        const val SKR_DECIMALS = 6
        val TWO_DECIMAL = java.text.DecimalFormat("0.00", java.text.DecimalFormatSymbols(Locale.US))
        val SIX_DECIMAL = java.text.DecimalFormat("0.000000", java.text.DecimalFormatSymbols(Locale.US))
    }
}

private data class PortfolioBundle(
    val portfolio: PortfolioSnapshot,
    val stakeAccounts: List<NativeStakeAccount>,
    val skrState: OfficialUserStakeState?,
    val skrApy: Double?,
)
