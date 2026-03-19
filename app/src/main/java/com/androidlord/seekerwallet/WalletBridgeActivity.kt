package com.androidlord.seekerwallet

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.androidlord.seekerkeyboard.ime.ConsolidationFeeModel
import com.androidlord.seekerkeyboard.ime.KeyboardSettingsStore
import com.androidlord.seekerkeyboard.ime.UnifiedAccountPreview
import com.androidlord.seekerkeyboard.ime.WalletActionDraftStore
import com.androidlord.seekerkeyboard.ime.WalletAccessGuardStore
import com.androidlord.seekerwallet.data.WalletSessionStore
import com.androidlord.seekerwallet.wallet.NativeStakeAccount
import com.androidlord.seekerwallet.wallet.NativeStakeService
import com.androidlord.seekerwallet.wallet.OfficialUserStakeState
import com.androidlord.seekerwallet.wallet.OfficialTxEnvelope
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
import java.util.Base64
import java.util.Locale

class WalletBridgeActivity : ComponentActivity() {
    private lateinit var sessionStore: WalletSessionStore
    private lateinit var settingsStore: KeyboardSettingsStore
    private lateinit var draftStore: WalletActionDraftStore
    private lateinit var walletAccessGuardStore: WalletAccessGuardStore
    private val rpcService = SolanaRpcService()
    private val nativeStakeService = NativeStakeService()
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
        settingsStore = KeyboardSettingsStore(applicationContext)
        draftStore = WalletActionDraftStore(applicationContext)
        walletAccessGuardStore = WalletAccessGuardStore(applicationContext)
        lifecycleScope.launch {
            runCatching {
                when (intent?.getStringExtra("wallet_action").orEmpty()) {
                    "connect" -> handleConnect()
                    "disconnect" -> handleDisconnect()
                    "send" -> handleSend()
                    "skr_stake" -> handleSkrStake()
                    "skr_unstake" -> handleSkrUnstake()
                    "skr_withdraw" -> handleSkrWithdraw()
                    "native_delegate" -> handleNativeDelegate()
                    "native_deactivate" -> handleNativeDeactivate()
                    "native_withdraw" -> handleNativeWithdraw()
                    "consolidate" -> handleConsolidate()
                    else -> handleRefresh()
                }
            }.onFailure { error ->
                sessionStore.saveKeyboardStatus(error.message ?: "Keyboard wallet action failed.")
            }
            finish()
        }
    }

    private suspend fun handleConnect() {
        val existingAddress = sessionStore.loadWalletAddress()
        val existingAuthToken = sessionStore.loadAuthToken()
        if (!existingAddress.isNullOrBlank() && !existingAuthToken.isNullOrBlank()) {
            walletAdapter.authToken = existingAuthToken
            refreshSnapshot(existingAddress, sessionStore.loadCluster())
            walletAccessGuardStore.unlock(settingsStore.load().walletUnlockMode)
            walletAccessGuardStore.markPendingOpenWallet()
            sessionStore.saveReviewState(reviewRequired = true, lastReviewedAction = "connect")
            sessionStore.saveKeyboardStatus("Wallet session active for ${shortAddress(existingAddress)}.")
            return
        }

        val sender = ActivityResultSender(this)
        when (val result = walletAdapter.connect(sender)) {
            is TransactionResult.Success -> {
                val address = Base58.encodeToString(result.authResult.accounts.first().publicKey)
                sessionStore.saveWalletAddress(address)
                sessionStore.saveAuthToken(result.authResult.authToken)
                walletAdapter.authToken = result.authResult.authToken
                refreshSnapshot(address, sessionStore.loadCluster())
                sessionStore.saveReviewState(reviewRequired = true, lastReviewedAction = "connect")
                walletAccessGuardStore.unlock(settingsStore.load().walletUnlockMode)
                walletAccessGuardStore.markPendingOpenWallet()
                sessionStore.saveKeyboardStatus("Connected ${shortAddress(address)}. Wallet session stored, return to keyboard.")
            }
            is TransactionResult.NoWalletFound -> sessionStore.saveKeyboardStatus("No MWA wallet found on this device.")
            is TransactionResult.Failure -> sessionStore.saveKeyboardStatus("Wallet connection failed: ${result.e.message}")
        }
    }

    private suspend fun handleDisconnect() {
        val sender = ActivityResultSender(this)
        when (val result = walletAdapter.disconnect(sender)) {
            is TransactionResult.Success, is TransactionResult.NoWalletFound -> {
                sessionStore.clearSession()
                walletAdapter.authToken = null
                walletAccessGuardStore.lock()
                sessionStore.saveReviewState(reviewRequired = true, lastReviewedAction = "disconnect")
                sessionStore.saveKeyboardStatus("Keyboard wallet session cleared.")
            }
            is TransactionResult.Failure -> sessionStore.saveKeyboardStatus("Disconnect failed: ${result.e.message}")
        }
    }

    private suspend fun handleRefresh() {
        val address = sessionStore.loadWalletAddress()
        if (address.isNullOrBlank()) {
            sessionStore.saveKeyboardStatus("No wallet connected.")
            return
        }
        refreshSnapshot(address, sessionStore.loadCluster())
        sessionStore.saveKeyboardStatus("Synced ${shortAddress(address)} on ${sessionStore.loadCluster().label}.")
    }

    private suspend fun handleSend() {
        val address = requireWalletAddress()
        val recipient = intent?.getStringExtra("recipient").orEmpty().trim()
        val amountSol = draftStore.load().sendAmountSol
        val amountLamports = amountSol.toDoubleOrNull()?.times(1_000_000_000.0)?.toLong() ?: 0L
        require(recipient.isNotBlank()) { "Copy a destination wallet address to the clipboard first." }
        require(amountLamports > 0L) { "Invalid SOL send amount." }

        val tx = nativeStakeService.buildTransferTx(
            fromAddress = address,
            toAddress = recipient,
            lamports = amountLamports,
            recentBlockhash = rpcService.getLatestBlockhash(sessionStore.loadCluster().rpcUrl),
        )
        submitSerializedTransactions(arrayOf(tx.serialize()), "Sent $amountSol SOL to ${shortAddress(recipient)}.")
    }

    private suspend fun handleSkrStake() {
        val address = requireWalletAddress()
        val rawAmount = uiAmountToRaw(draftStore.load().skrStakeAmount, SKR_DECIMALS)
        require(rawAmount != "0") { "Invalid SKR stake amount." }
        submitBase64Envelope(
            skrOfficialService.buildStakeTx(rawAmount, address, address),
            "SKR stake submitted."
        )
    }

    private suspend fun handleSkrUnstake() {
        val address = requireWalletAddress()
        val rawAmount = uiAmountToRaw(draftStore.load().skrUnstakeAmount, SKR_DECIMALS)
        require(rawAmount != "0") { "Invalid SKR unstake amount." }
        submitBase64Envelope(
            skrOfficialService.buildUnstakeTx(rawAmount, address),
            "SKR unstake submitted."
        )
    }

    private suspend fun handleSkrWithdraw() {
        val address = requireWalletAddress()
        submitBase64Envelope(
            skrOfficialService.buildWithdrawTx(address, address),
            "SKR withdraw submitted."
        )
    }

    private suspend fun handleNativeDelegate() {
        val address = requireWalletAddress()
        val stakeAccountAddress = requireSelectedStakePubkey()
        val validatorVote = NativeStakeService.DEFAULT_VALIDATOR_VOTE[sessionStore.loadCluster()]
            ?: NativeStakeService.DEFAULT_VALIDATOR_VOTE.getValue(SolanaCluster.MAINNET)
        val tx = nativeStakeService.buildDelegateStakeTx(
            ownerAddress = address,
            stakeAccountAddress = stakeAccountAddress,
            validatorVoteAddress = validatorVote,
            recentBlockhash = rpcService.getLatestBlockhash(sessionStore.loadCluster().rpcUrl),
        )
        submitSerializedTransactions(arrayOf(tx.serialize()), "Stake delegation submitted.")
    }

    private suspend fun handleNativeDeactivate() {
        val address = requireWalletAddress()
        val stakeAccountAddress = requireSelectedStakePubkey()
        val tx = nativeStakeService.buildDeactivateStakeTx(
            ownerAddress = address,
            stakeAccountAddress = stakeAccountAddress,
            recentBlockhash = rpcService.getLatestBlockhash(sessionStore.loadCluster().rpcUrl),
        )
        submitSerializedTransactions(arrayOf(tx.serialize()), "Stake deactivation submitted.")
    }

    private suspend fun handleNativeWithdraw() {
        val address = requireWalletAddress()
        val stakeAccountAddress = requireSelectedStakePubkey()
        val lamports = intent?.getStringExtra("selected_stake_lamports")?.toLongOrNull() ?: 0L
        require(lamports > 0L) { "Selected stake account is empty." }
        val tx = nativeStakeService.buildWithdrawStakeTx(
            ownerAddress = address,
            stakeAccountAddress = stakeAccountAddress,
            destinationAddress = address,
            lamports = lamports,
            recentBlockhash = rpcService.getLatestBlockhash(sessionStore.loadCluster().rpcUrl),
        )
        submitSerializedTransactions(arrayOf(tx.serialize()), "Stake withdrawal submitted.")
    }

    private suspend fun handleConsolidate() {
        val address = requireWalletAddress()
        val cluster = sessionStore.loadCluster()
        val accounts = rpcService.fetchStakeAccounts(cluster.rpcUrl, address).sortedByDescending { it.lamports }
        val preferredDestination = intent?.getStringExtra("destination_stake_pubkey").orEmpty()
        val destination = accounts.firstOrNull { it.pubkey == preferredDestination } ?: selectConsolidationDestination(accounts)
        require(destination != null) { "No destination stake account available for consolidation." }

        val sourceLimit = intent?.getStringExtra("consolidation_source_count")?.toIntOrNull()?.coerceIn(1, 99) ?: 1
        val sources = selectConsolidationSources(destination, accounts, sourceLimit)
        require(sources.isNotEmpty()) { "No source stake accounts available for consolidation." }

        val blockhash = rpcService.getLatestBlockhash(cluster.rpcUrl)
        val txs = sources.map { source ->
            nativeStakeService.buildMergeStakeTx(
                ownerAddress = address,
                destinationStakeAccountAddress = destination.pubkey,
                sourceStakeAccountAddress = source.pubkey,
                recentBlockhash = blockhash,
            ).serialize()
        }.toTypedArray()

        val feeQuote = ConsolidationFeeModel.quote(sources.size)
        submitSerializedTransactions(
            serializedTransactions = txs,
            successMessage = "Consolidation submitted to ${shortAddress(destination.pubkey)}. Fee carry ${feeQuote.feeInSkr} SKR.",
        )
    }

    private suspend fun refreshSnapshot(
        address: String,
        cluster: SolanaCluster,
    ) {
        val bundle = coroutineScope {
            val portfolioDeferred = async { rpcService.loadPortfolio(cluster.rpcUrl, address) }
            val stakeDeferred = async { runCatching { rpcService.fetchStakeAccounts(cluster.rpcUrl, address) }.getOrDefault(emptyList()) }
            val skrDeferred = async { runCatching { skrOfficialService.fetchUserStake(address) }.getOrNull() }
            val apyDeferred = async { runCatching { skrOfficialService.fetchCurrentApy() }.getOrNull() }
            KeyboardPortfolioBundle(
                portfolio = portfolioDeferred.await(),
                stakeAccounts = stakeDeferred.await(),
                skrState = skrDeferred.await(),
                skrApy = apyDeferred.await(),
            )
        }

        val totalBalanceUsd = "$" + TWO_DECIMAL.format(bundle.portfolio.solBalance * SOL_PRICE_HINT)
        val skrPosition = buildSkrPosition(bundle.skrState, bundle.skrApy)
        val unifiedAccounts = buildUnifiedAccounts(bundle.portfolio, bundle.stakeAccounts, skrPosition)
        sessionStore.saveNativeStakeAccountCount(bundle.stakeAccounts.size)
        sessionStore.saveWalletPanelSnapshot(
            totalBalanceUsd = totalBalanceUsd,
            skrPosition = skrPosition,
            stakeAccounts = bundle.stakeAccounts,
            eligibleConsolidationSources = estimateEligibleConsolidationSources(bundle.stakeAccounts),
            unifiedAccounts = unifiedAccounts,
        )
    }

    private suspend fun submitBase64Envelope(
        envelope: OfficialTxEnvelope,
        successMessage: String,
    ) {
        submitSerializedTransactions(
            serializedTransactions = arrayOf(Base64.getDecoder().decode(envelope.transaction)),
            successMessage = successMessage,
            feeLabel = envelope.fee,
        )
    }

    private suspend fun submitSerializedTransactions(
        serializedTransactions: Array<ByteArray>,
        successMessage: String,
        feeLabel: String? = null,
    ) {
        val sender = ActivityResultSender(this)
        when (val result = walletAdapter.transact(sender) {
            walletAdapter.authToken?.let { authToken ->
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
                sessionStore.loadWalletAddress()?.let { refreshSnapshot(it, sessionStore.loadCluster()) }
                sessionStore.saveReviewState(reviewRequired = true, lastReviewedAction = successMessage)
                sessionStore.saveKeyboardStatus(
                    buildString {
                        append(successMessage)
                        feeLabel?.takeIf { it.isNotBlank() }?.let { append(" Fee: $it") }
                    }
                )
            }
            is TransactionResult.NoWalletFound -> sessionStore.saveKeyboardStatus("No compatible wallet app found.")
            is TransactionResult.Failure -> sessionStore.saveKeyboardStatus("Transaction failed: ${result.e.message}")
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
        )
    }

    private fun buildUnifiedAccounts(
        portfolio: PortfolioSnapshot,
        stakeAccounts: List<NativeStakeAccount>,
        skrPosition: SkrPosition,
    ): List<UnifiedAccountPreview> {
        val nativeStakeLamports = stakeAccounts.sumOf { it.lamports }
        val activeStakeCount = stakeAccounts.count { !isStakeInactive(it) }
        val tokenCount = portfolio.tokenHoldings.size
        return listOf(
            UnifiedAccountPreview(
                title = "Spendable SOL",
                balanceLabel = "${SIX_DECIMAL.format(portfolio.solBalance)} SOL",
                detailLabel = "$" + TWO_DECIMAL.format(portfolio.solBalance * SOL_PRICE_HINT),
                emphasis = if (portfolio.solBalance > 0.0) "liquid" else "empty",
            ),
            UnifiedAccountPreview(
                title = "Native Stake",
                balanceLabel = solLabel(nativeStakeLamports),
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
                balanceLabel = "$tokenCount tracked",
                detailLabel = portfolio.tokenHoldings.take(2).joinToString(" · ") { "${it.amount} ${it.symbol}" }.ifBlank { "No funded token accounts" },
                emphasis = if (tokenCount > 0) "loaded" else "quiet",
            ),
        )
    }

    private fun estimateEligibleConsolidationSources(accounts: List<NativeStakeAccount>): Int {
        val delegated = accounts.filter { it.delegationVote != null && !isStakeInactive(it) }
        val largestValidatorGroup = delegated.groupBy { it.delegationVote }.maxOfOrNull { it.value.size } ?: 0
        return (largestValidatorGroup - 1).coerceAtLeast(0)
    }

    private fun selectConsolidationDestination(accounts: List<NativeStakeAccount>): NativeStakeAccount? {
        return accounts
            .filter { it.delegationVote != null && !isStakeInactive(it) }
            .maxByOrNull { it.lamports }
    }

    private fun selectConsolidationSources(
        destination: NativeStakeAccount,
        accounts: List<NativeStakeAccount>,
        limit: Int,
    ): List<NativeStakeAccount> {
        val likelyCompatible = accounts
            .asSequence()
            .filter { it.pubkey != destination.pubkey }
            .filter { it.delegationVote != null && it.delegationVote == destination.delegationVote }
            .filter { !isStakeInactive(it) }
            .take(limit.coerceAtLeast(1))
            .toList()
        if (likelyCompatible.isNotEmpty()) return likelyCompatible

        // Soft validation mode: if no likely-compatible sources are found, still allow
        // submission against other non-destination stake accounts and let the chain decide.
        return accounts
            .asSequence()
            .filter { it.pubkey != destination.pubkey }
            .take(limit.coerceAtLeast(1))
            .toList()
    }

    private fun requireWalletAddress(): String {
        return sessionStore.loadWalletAddress().orEmpty().also {
            require(it.isNotBlank()) { "No wallet connected." }
        }
    }

    private fun requireSelectedStakePubkey(): String {
        return intent?.getStringExtra("selected_stake_pubkey").orEmpty().also {
            require(it.isNotBlank()) { "Select a stake account first." }
        }
    }

    private fun uiAmountToRaw(amountText: String, decimals: Int): String {
        val clean = amountText.trim()
        if (clean.isBlank() || !clean.matches(Regex("\\d+(\\.\\d+)?"))) return "0"
        val parts = clean.split('.', limit = 2)
        val whole = parts[0]
        val frac = parts.getOrElse(1) { "" }
        val padded = (frac + "0".repeat(decimals)).take(decimals)
        val raw = (whole + padded).trimStart('0')
        return raw.ifBlank { "0" }
    }

    private fun shortAddress(address: String): String {
        return if (address.length <= 10) address else address.take(4) + "…" + address.takeLast(4)
    }

    private fun solLabel(lamports: Long): String {
        return "${SIX_DECIMAL.format(lamports / 1_000_000_000.0)} SOL"
    }

    private companion object {
        const val IDENTITY_URI = "https://github.com/androidlord666/seekerkeyboard"
        const val ICON_URI = "/favicon.ico"
        const val IDENTITY_NAME = "SeekerKeyboard"
        const val SOL_PRICE_HINT = 90.0
        const val SKR_DECIMALS = 6
        val TWO_DECIMAL = DecimalFormat("0.00", DecimalFormatSymbols(Locale.US))
        val SIX_DECIMAL = DecimalFormat("0.000000", DecimalFormatSymbols(Locale.US))
    }
}

private data class KeyboardPortfolioBundle(
    val portfolio: PortfolioSnapshot,
    val stakeAccounts: List<NativeStakeAccount>,
    val skrState: OfficialUserStakeState?,
    val skrApy: Double?,
)
