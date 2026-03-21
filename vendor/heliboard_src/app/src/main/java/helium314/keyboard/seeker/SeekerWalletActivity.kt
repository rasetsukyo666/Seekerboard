package helium314.keyboard.seeker

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.funkatronics.encoders.Base58
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import java.math.BigDecimal
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SeekerWalletActivity : ComponentActivity() {
    private enum class WalletPanel {
        SEND,
        SWAP,
    }

    private lateinit var sessionStore: SeekerWalletSessionStore
    private lateinit var activityResultSender: ActivityResultSender
    private val rpcService = SeekerSolanaRpcService()
    private val transferService = SeekerTransferService()
    private val snsResolver = SeekerSnsResolver()
    private val swapService = SeekerJupiterSwapService(BuildConfig.JUPITER_API_KEY)
    private val tokenService = SeekerJupiterTokenService(BuildConfig.JUPITER_API_KEY)
    private var recipientResolutionJob: Job? = null
    private var defaultStatusMessage: String = ""
    private var selectedSwapFrom = DEFAULT_FROM_TOKEN
    private var selectedSwapTo = DEFAULT_TO_TOKEN
    private var latestQuote: SeekerJupiterSwapService.JupiterQuote? = null
    private var activePanel = WalletPanel.SEND

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
        sessionStore = SeekerWalletSessionStore(applicationContext)
        activityResultSender = ActivityResultSender(this)
        setContentView(R.layout.activity_seeker_wallet)

        bindCurrentSession()

        bindButton(R.id.wallet_close) { finish() }
        bindButton(R.id.wallet_connect) {
            lifecycleScope.launch { connectWallet() }
        }
        bindButton(R.id.wallet_disconnect) {
            disconnectWallet()
        }
        bindButton(R.id.wallet_action_receive) {
            copyReceiveAddress()
        }
        bindButton(R.id.wallet_send) {
            lifecycleScope.launch { sendSol() }
        }
        bindButton(R.id.wallet_action_send) {
            showPanel(WalletPanel.SEND)
        }
        bindButton(R.id.wallet_action_receive) {
            copyReceiveAddress()
        }
        bindButton(R.id.wallet_action_swap) {
            showPanel(WalletPanel.SWAP)
        }
        bindButton(R.id.wallet_swap_from_picker) {
            openTokenPicker(isFrom = true)
        }
        bindButton(R.id.wallet_swap_to_picker) {
            openTokenPicker(isFrom = false)
        }
        bindButton(R.id.wallet_swap_quote) {
            lifecycleScope.launch { quoteSwap() }
        }
        bindButton(R.id.wallet_swap_execute) {
            lifecycleScope.launch { executeSwap() }
        }
        bindRecipientResolver()
        renderSwapSelection()
        showPanel(activePanel)
    }

    override fun onResume() {
        super.onResume()
        bindCurrentSession()
        val address = sessionStore.loadWalletAddress()
        if (!address.isNullOrBlank()) {
            lifecycleScope.launch {
                delay(300)
                refreshAllBalances(address, sessionStore.loadCluster())
                latestQuote = null
                updateStatus(defaultStatusMessage)
            }
        }
    }

    private fun bindButton(id: Int, action: () -> Unit) {
        findViewById<Button>(id).setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            action()
        }
    }

    private fun bindRecipientResolver() {
        findViewById<EditText>(R.id.wallet_send_recipient).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                scheduleRecipientResolution(s?.toString().orEmpty())
            }
        })
    }

    private fun bindCurrentSession() {
        val address = sessionStore.loadWalletAddress()
        val cluster = sessionStore.loadCluster()
        findViewById<TextView>(R.id.wallet_cluster).text = cluster.label
        updateAddress(address)
        updateConnectedState(address.isNullOrBlank().not())
        defaultStatusMessage = if (address.isNullOrBlank()) {
            getString(R.string.seeker_wallet_status_ready)
        } else {
            getString(R.string.seeker_wallet_status_connected, shortAddress(address))
        }
        updateStatus(defaultStatusMessage)
        if (address.isNullOrBlank()) {
            updateBalance(null)
            updateTokenBalances(null, null)
        } else {
            lifecycleScope.launch {
                refreshAllBalances(address, cluster)
            }
        }
    }

    private suspend fun connectWallet() {
        setBusy(true)
        try {
            when (val result = walletAdapter.transact(activityResultSender) { authResult ->
                authResult
            }) {
                is TransactionResult.Success -> {
                    val account = result.authResult.accounts.firstOrNull()
                    if (account == null) {
                        updateStatus(getString(R.string.seeker_wallet_status_connect_failed, "wallet returned no account"))
                        return
                    }
                    val address = Base58.encodeToString(account.publicKey)
                    sessionStore.saveWalletAddress(address)
                    sessionStore.saveAuthToken(result.authResult.authToken)
                    walletAdapter.authToken = result.authResult.authToken
                    updateAddress(address)
                    updateConnectedState(true)
                    refreshAllBalances(address, sessionStore.loadCluster())
                    defaultStatusMessage = getString(R.string.seeker_wallet_status_connected, shortAddress(address))
                    updateStatus(defaultStatusMessage)
                }
                is TransactionResult.NoWalletFound -> {
                    updateStatus(getString(R.string.seeker_wallet_status_no_wallet))
                }
                is TransactionResult.Failure -> {
                    updateStatus(getString(R.string.seeker_wallet_status_connect_failed, result.e.message ?: "unknown error"))
                }
            }
        } catch (error: Throwable) {
            updateStatus(getString(R.string.seeker_wallet_status_connect_failed, error.message ?: "unexpected error"))
        } finally {
            setBusy(false)
        }
    }

    private fun copyReceiveAddress() {
        val address = sessionStore.loadWalletAddress()
        if (address.isNullOrBlank()) {
            updateStatus(getString(R.string.seeker_wallet_status_need_connect))
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Solana address", address))
        showReceiveQr(address)
        updateStatus(getString(R.string.seeker_wallet_status_receive, shortAddress(address)))
    }

    private suspend fun quoteSwap() {
        val address = sessionStore.loadWalletAddress()
        if (address.isNullOrBlank()) {
            updateStatus(getString(R.string.seeker_wallet_status_need_connect))
            return
        }
        val amountSol = findViewById<EditText>(R.id.wallet_swap_amount).text.toString().trim()
        val amountBaseUnits = amountSol.toDoubleOrNull()?.times(selectedSwapFrom.multiplier)?.toLong() ?: 0L
        if (selectedSwapFrom.mint == selectedSwapTo.mint) {
            updateStatus(getString(R.string.seeker_wallet_swap_same_token))
            return
        }
        if (amountBaseUnits <= 0L) {
            updateStatus(getString(R.string.seeker_wallet_status_invalid_amount))
            return
        }

        setBusy(true)
        try {
            val quote = swapService.getQuote(
                inputMint = selectedSwapFrom.mint,
                outputMint = selectedSwapTo.mint,
                amount = amountBaseUnits,
            )
            latestQuote = quote
            val outputAmount = BigDecimal(quote.outAmount)
                .movePointLeft(selectedSwapTo.decimals)
                .stripTrailingZeros()
                .toPlainString()
            updateStatus(
                getString(
                    R.string.seeker_wallet_swap_quote_ready,
                    amountSol,
                    selectedSwapFrom.symbol,
                    outputAmount,
                    selectedSwapTo.symbol,
                )
            )
        } catch (error: Throwable) {
            latestQuote = null
            updateStatus(getString(R.string.seeker_wallet_swap_failed, error.message ?: "quote failed"))
        } finally {
            setBusy(false)
        }
    }

    private suspend fun executeSwap() {
        val address = sessionStore.loadWalletAddress()
        if (address.isNullOrBlank()) {
            updateStatus(getString(R.string.seeker_wallet_status_need_connect))
            return
        }
        val quote = latestQuote
        if (quote == null) {
            updateStatus(getString(R.string.seeker_wallet_swap_quote_missing))
            return
        }

        setBusy(true)
        try {
            val txBytes = swapService.buildSwapTransaction(quote, address)
            when (val result = walletAdapter.transact(activityResultSender) {
                signAndSendTransactions(arrayOf(txBytes))
            }) {
                is TransactionResult.Success -> {
                    result.authResult.authToken?.let {
                        sessionStore.saveAuthToken(it)
                        walletAdapter.authToken = it
                    }
                    updateStatus(getString(R.string.seeker_wallet_swap_success, selectedSwapTo.symbol))
                    refreshAllBalances(address, sessionStore.loadCluster())
                }
                is TransactionResult.NoWalletFound -> {
                    updateStatus(getString(R.string.seeker_wallet_status_no_wallet))
                }
                is TransactionResult.Failure -> {
                    updateStatus(getString(R.string.seeker_wallet_swap_failed, result.e.message ?: "unknown error"))
                }
            }
        } catch (error: Throwable) {
            updateStatus(getString(R.string.seeker_wallet_swap_failed, error.message ?: "unexpected error"))
        } finally {
            setBusy(false)
        }
    }

    private suspend fun sendSol() {
        val address = sessionStore.loadWalletAddress()
        if (address.isNullOrBlank()) {
            updateStatus(getString(R.string.seeker_wallet_status_need_connect))
            return
        }

        val recipientInput = findViewById<EditText>(R.id.wallet_send_recipient).text.toString().trim()
        val amountSol = findViewById<EditText>(R.id.wallet_send_amount).text.toString().trim()
        val amountLamports = amountSol.toDoubleOrNull()?.times(LAMPORTS_PER_SOL)?.toLong() ?: 0L
        if (recipientInput.isBlank()) {
            updateStatus(getString(R.string.seeker_wallet_status_invalid_recipient))
            return
        }
        if (amountLamports <= 0L) {
            updateStatus(getString(R.string.seeker_wallet_status_invalid_amount))
            return
        }

        setBusy(true)
        try {
            val recipient = snsResolver.resolveRecipient(recipientInput)
            val cluster = sessionStore.loadCluster()
            val recentBlockhash = rpcService.getLatestBlockhash(cluster.rpcUrl)
            val tx = transferService.buildTransferTx(
                fromAddress = address,
                toAddress = recipient,
                lamports = amountLamports,
                recentBlockhash = recentBlockhash,
            )
            when (val result = walletAdapter.transact(activityResultSender) { authResult ->
                signAndSendTransactions(arrayOf(tx.serialize()))
            }) {
                is TransactionResult.Success -> {
                    result.authResult.authToken?.let {
                        sessionStore.saveAuthToken(it)
                        walletAdapter.authToken = it
                    }
                    val sentTo = if (recipientInput.equals(recipient, ignoreCase = true)) {
                        shortAddress(recipient)
                    } else {
                        getString(R.string.seeker_wallet_status_send_success_resolved_target, recipientInput, shortAddress(recipient))
                    }
                    updateStatus(getString(R.string.seeker_wallet_status_send_success, amountSol, sentTo))
                }
                is TransactionResult.NoWalletFound -> {
                    updateStatus(getString(R.string.seeker_wallet_status_no_wallet))
                }
                is TransactionResult.Failure -> {
                    updateStatus(getString(R.string.seeker_wallet_status_send_failed, result.e.message ?: "unknown error"))
                }
            }
        } catch (error: Throwable) {
            updateStatus(getString(R.string.seeker_wallet_status_send_failed, error.message ?: "unexpected error"))
        } finally {
            setBusy(false)
        }
    }

    private fun updateAddress(address: String?) {
        findViewById<TextView>(R.id.wallet_address).text = address ?: getString(R.string.seeker_wallet_not_connected)
    }

    private fun updateBalance(balanceLamports: Long?) {
        val balanceText = if (balanceLamports == null) {
            getString(R.string.seeker_wallet_balance_placeholder)
        } else {
            String.format("%.4f SOL", balanceLamports / LAMPORTS_PER_SOL)
        }
        findViewById<TextView>(R.id.wallet_balance).text = balanceText
    }

    private fun updateTokenBalances(usdc: Double?, skr: Double?) {
        findViewById<TextView>(R.id.wallet_usdc_balance).text =
            if (usdc == null) getString(R.string.seeker_wallet_token_balance_placeholder_primary)
            else getString(R.string.seeker_wallet_token_balance_value, selectedSwapFrom.symbol, formatTokenAmount(usdc))
        findViewById<TextView>(R.id.wallet_skr_balance).text =
            if (skr == null) getString(R.string.seeker_wallet_token_balance_placeholder_secondary)
            else getString(R.string.seeker_wallet_token_balance_value, selectedSwapTo.symbol, formatTokenAmount(skr))
    }

    private fun updateStatus(status: String) {
        findViewById<TextView>(R.id.wallet_status).text = status
    }

    private fun updateConnectedState(isConnected: Boolean) {
        findViewById<Button>(R.id.wallet_connect).visibility = if (isConnected) View.GONE else View.VISIBLE
    }

    private fun showPanel(panel: WalletPanel) {
        activePanel = panel
        findViewById<View>(R.id.wallet_send_section).visibility =
            if (panel == WalletPanel.SEND) View.VISIBLE else View.GONE
        findViewById<View>(R.id.wallet_swap_section).visibility =
            if (panel == WalletPanel.SWAP) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.wallet_action_send).alpha = if (panel == WalletPanel.SEND) 1.0f else 0.7f
        findViewById<Button>(R.id.wallet_action_swap).alpha = if (panel == WalletPanel.SWAP) 1.0f else 0.7f
    }

    private fun setBusy(isBusy: Boolean) {
        val connectButton = findViewById<Button>(R.id.wallet_connect)
        if (connectButton.visibility == View.VISIBLE) {
            connectButton.isEnabled = !isBusy
        }
        findViewById<Button>(R.id.wallet_disconnect).isEnabled = !isBusy
        findViewById<Button>(R.id.wallet_send).isEnabled = !isBusy
        findViewById<Button>(R.id.wallet_action_receive).isEnabled = !isBusy
        findViewById<Button>(R.id.wallet_swap_from_picker).isEnabled = !isBusy
        findViewById<Button>(R.id.wallet_swap_to_picker).isEnabled = !isBusy
        findViewById<Button>(R.id.wallet_swap_quote).isEnabled = !isBusy
        findViewById<Button>(R.id.wallet_swap_execute).isEnabled = !isBusy && latestQuote != null
    }

    private fun disconnectWallet() {
        sessionStore.clear()
        walletAdapter.authToken = null
        latestQuote = null
        recipientResolutionJob?.cancel()
        findViewById<EditText>(R.id.wallet_send_recipient).text?.clear()
        findViewById<EditText>(R.id.wallet_send_amount).text?.clear()
        findViewById<EditText>(R.id.wallet_swap_amount).text?.clear()
        defaultStatusMessage = getString(R.string.seeker_wallet_status_ready)
        bindCurrentSession()
    }

    private fun selectSwapFrom(token: SwapToken) {
        selectedSwapFrom = token
        if (selectedSwapFrom.mint == selectedSwapTo.mint) {
            selectedSwapTo = DEFAULT_TO_TOKEN
        }
        latestQuote = null
        renderSwapSelection()
        updateStatus(getString(R.string.seeker_wallet_swap_pair_selected, selectedSwapFrom.symbol, selectedSwapTo.symbol))
    }

    private fun selectSwapTo(token: SwapToken) {
        selectedSwapTo = token
        if (selectedSwapFrom.mint == selectedSwapTo.mint) {
            selectedSwapFrom = DEFAULT_FROM_TOKEN
        }
        latestQuote = null
        renderSwapSelection()
        updateStatus(getString(R.string.seeker_wallet_swap_pair_selected, selectedSwapFrom.symbol, selectedSwapTo.symbol))
    }

    private fun renderSwapSelection() {
        findViewById<Button>(R.id.wallet_swap_from_picker).text =
            getString(R.string.seeker_wallet_swap_from_value, selectedSwapFrom.symbol)
        findViewById<Button>(R.id.wallet_swap_to_picker).text =
            getString(R.string.seeker_wallet_swap_to_value, selectedSwapTo.symbol)
        findViewById<Button>(R.id.wallet_swap_execute).isEnabled = latestQuote != null
        val address = sessionStore.loadWalletAddress()
        if (!address.isNullOrBlank()) {
            lifecycleScope.launch {
                refreshAllBalances(address, sessionStore.loadCluster())
            }
        }
    }

    private fun openTokenPicker(isFrom: Boolean) {
        val input = EditText(this).apply {
            hint = getString(R.string.seeker_wallet_token_search_hint)
            setText(if (isFrom) selectedSwapFrom.symbol else selectedSwapTo.symbol)
        }
        AlertDialog.Builder(this)
            .setTitle(if (isFrom) getString(R.string.seeker_wallet_swap_from_title) else getString(R.string.seeker_wallet_swap_to_title))
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.seeker_wallet_token_search_go) { _: DialogInterface, _: Int ->
                lifecycleScope.launch {
                    searchAndSelectToken(input.text.toString(), isFrom)
                }
            }
            .show()
    }

    private suspend fun searchAndSelectToken(query: String, isFrom: Boolean) {
        if (query.isBlank()) return
        updateStatus(getString(R.string.seeker_wallet_token_searching, query))
        try {
            val results = tokenService.searchTokens(query).take(8)
            if (results.isEmpty()) {
                updateStatus(getString(R.string.seeker_wallet_token_search_empty, query))
                return
            }
            val labels = results.map {
                "${it.symbol} - ${it.name}" + if (it.verified) " ✓" else ""
            }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle(if (isFrom) getString(R.string.seeker_wallet_swap_from_title) else getString(R.string.seeker_wallet_swap_to_title))
                .setItems(labels) { _, which ->
                    val token = results[which]
                    val selected = SwapToken(token.mint, token.symbol, token.decimals)
                    if (isFrom) selectSwapFrom(selected) else selectSwapTo(selected)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } catch (error: Throwable) {
            updateStatus(getString(R.string.seeker_wallet_token_search_failed, error.message ?: "search failed"))
        }
    }

    private suspend fun refreshBalance(address: String, cluster: SolanaCluster) {
        try {
            updateBalance(rpcService.getBalance(cluster.rpcUrl, address))
        } catch (_: Throwable) {
            updateBalance(null)
        }
    }

    private suspend fun refreshAllBalances(address: String, cluster: SolanaCluster) {
        val solLamports = try {
            rpcService.getBalance(cluster.rpcUrl, address)
        } catch (_: Throwable) {
            null
        }
        updateBalance(solLamports)
        try {
            val fromBalance = getTokenDisplayBalance(cluster, address, selectedSwapFrom, solLamports)
            val toBalance = getTokenDisplayBalance(cluster, address, selectedSwapTo, solLamports)
            updateTokenBalances(fromBalance, toBalance)
        } catch (_: Throwable) {
            updateTokenBalances(null, null)
        }
    }

    private suspend fun getTokenDisplayBalance(
        cluster: SolanaCluster,
        address: String,
        token: SwapToken,
        solLamports: Long?,
    ): Double? {
        if (token.mint == SOL_MINT) {
            return solLamports?.div(LAMPORTS_PER_SOL)
        }
        return rpcService.getSplTokenBalance(cluster.rpcUrl, address, token.mint).amount
    }

    private fun scheduleRecipientResolution(rawInput: String) {
        recipientResolutionJob?.cancel()
        val input = rawInput.trim()
        if (input.isBlank()) {
            updateStatus(defaultStatusMessage)
            return
        }
        if (!input.endsWith(".sol", ignoreCase = true)) {
            updateStatus(getString(R.string.seeker_wallet_resolution_direct))
            return
        }

        updateStatus(getString(R.string.seeker_wallet_resolution_resolving, input))
        recipientResolutionJob = lifecycleScope.launch {
            delay(250)
            try {
                val resolved = snsResolver.resolveRecipient(input)
                updateStatus(getString(
                    R.string.seeker_wallet_resolution_resolved,
                    input,
                    shortAddress(resolved),
                ))
            } catch (_: Throwable) {
                updateStatus(getString(R.string.seeker_wallet_resolution_failed, input))
            }
        }
    }

    private fun showReceiveQr(address: String) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dp(20)
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(Color.parseColor("#FF151615"))
        }

        val qrImage = ImageView(this).apply {
            val size = dp(240)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                bottomMargin = dp(16)
            }
            setImageBitmap(buildQrBitmap(address, size))
            setBackgroundColor(Color.WHITE)
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }

        val addressView = TextView(this).apply {
            text = address
            setTextColor(Color.parseColor("#FFE8FFF9"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }

        container.addView(qrImage)
        container.addView(addressView)

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.seeker_wallet_receive))
            .setView(container)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        dialog.show()
    }

    private fun buildQrBitmap(value: String, sizePx: Int): Bitmap {
        val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics,
        ).toInt()
    }

    private fun shortAddress(address: String): String {
        return if (address.length <= 10) address else address.take(4) + "..." + address.takeLast(4)
    }

    private fun formatTokenAmount(value: Double): String {
        return String.format("%.4f", value)
    }

    private companion object {
        const val IDENTITY_NAME = "Seekerboard"
        const val IDENTITY_URI = "https://seekerkeyboard.app"
        const val ICON_URI = "favicon.ico"
        const val LAMPORTS_PER_SOL = 1_000_000_000.0
        const val USDC_MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
        const val SKR_MINT = "SKRbvo6Gf7GondiT3BbTfuRDPqLWei4j2Qy2NPGZhW3"
        const val SOL_MINT = "So11111111111111111111111111111111111111112"
        val DEFAULT_FROM_TOKEN = SwapToken(SOL_MINT, "SOL", 9)
        val DEFAULT_TO_TOKEN = SwapToken(USDC_MINT, "USDC", 6)
    }

    private data class SwapToken(val mint: String, val symbol: String, val decimals: Int) {
        val multiplier: Double
            get() = Math.pow(10.0, decimals.toDouble())
    }
}
