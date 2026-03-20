package helium314.keyboard.seeker

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.funkatronics.encoders.Base58
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import helium314.keyboard.latin.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SeekerWalletActivity : ComponentActivity() {
    private lateinit var sessionStore: SeekerWalletSessionStore
    private lateinit var activityResultSender: ActivityResultSender
    private val rpcService = SeekerSolanaRpcService()
    private val transferService = SeekerTransferService()
    private val snsResolver = SeekerSnsResolver()
    private var recipientResolutionJob: Job? = null

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
        bindButton(R.id.wallet_receive) {
            copyReceiveAddress()
        }
        bindButton(R.id.wallet_send) {
            lifecycleScope.launch { sendSol() }
        }
        bindRecipientResolver()
    }

    override fun onResume() {
        super.onResume()
        bindCurrentSession()
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
        updateStatus(
            if (address.isNullOrBlank()) {
                getString(R.string.seeker_wallet_status_ready)
            } else {
                getString(R.string.seeker_wallet_status_connected, shortAddress(address))
            }
        )
        if (address.isNullOrBlank()) {
            updateBalance(null)
        } else {
            lifecycleScope.launch {
                refreshBalance(address, cluster)
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
                    refreshBalance(address, sessionStore.loadCluster())
                    updateStatus(getString(R.string.seeker_wallet_status_connected, shortAddress(address)))
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

    private fun updateStatus(status: String) {
        findViewById<TextView>(R.id.wallet_status).text = status
    }

    private fun updateConnectedState(isConnected: Boolean) {
        findViewById<Button>(R.id.wallet_connect).visibility = if (isConnected) View.GONE else View.VISIBLE
    }

    private fun setBusy(isBusy: Boolean) {
        val connectButton = findViewById<Button>(R.id.wallet_connect)
        if (connectButton.visibility == View.VISIBLE) {
            connectButton.isEnabled = !isBusy
        }
        findViewById<Button>(R.id.wallet_send).isEnabled = !isBusy
        findViewById<Button>(R.id.wallet_receive).isEnabled = !isBusy
    }

    private suspend fun refreshBalance(address: String, cluster: SolanaCluster) {
        try {
            updateBalance(rpcService.getBalance(cluster.rpcUrl, address))
        } catch (_: Throwable) {
            updateBalance(null)
        }
    }

    private fun scheduleRecipientResolution(rawInput: String) {
        recipientResolutionJob?.cancel()
        val input = rawInput.trim()
        val resolutionView = findViewById<TextView>(R.id.wallet_send_resolution)
        if (input.isBlank()) {
            resolutionView.isVisible = false
            return
        }
        if (!input.endsWith(".sol", ignoreCase = true)) {
            resolutionView.isVisible = true
            resolutionView.text = getString(R.string.seeker_wallet_resolution_direct)
            return
        }

        resolutionView.isVisible = true
        resolutionView.text = getString(R.string.seeker_wallet_resolution_resolving, input)
        recipientResolutionJob = lifecycleScope.launch {
            delay(250)
            try {
                val resolved = snsResolver.resolveRecipient(input)
                resolutionView.text = getString(
                    R.string.seeker_wallet_resolution_resolved,
                    input,
                    shortAddress(resolved),
                )
            } catch (_: Throwable) {
                resolutionView.text = getString(R.string.seeker_wallet_resolution_failed, input)
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

    private companion object {
        const val IDENTITY_NAME = "Seekerboard"
        const val IDENTITY_URI = "https://seekerkeyboard.app"
        const val ICON_URI = "favicon.ico"
        const val LAMPORTS_PER_SOL = 1_000_000_000.0
    }
}
