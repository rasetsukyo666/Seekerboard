package helium314.keyboard.seeker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.funkatronics.encoders.Base58
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import helium314.keyboard.latin.R
import kotlinx.coroutines.launch

class SeekerWalletActivity : ComponentActivity() {
    private lateinit var sessionStore: SeekerWalletSessionStore
    private lateinit var activityResultSender: ActivityResultSender
    private val rpcService = SeekerSolanaRpcService()
    private val transferService = SeekerTransferService()

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
        updateStatus(getString(R.string.seeker_wallet_status_receive, shortAddress(address)))
    }

    private suspend fun sendSol() {
        val address = sessionStore.loadWalletAddress()
        if (address.isNullOrBlank()) {
            updateStatus(getString(R.string.seeker_wallet_status_need_connect))
            return
        }

        val recipient = findViewById<EditText>(R.id.wallet_send_recipient).text.toString().trim()
        val amountSol = findViewById<EditText>(R.id.wallet_send_amount).text.toString().trim()
        val amountLamports = amountSol.toDoubleOrNull()?.times(LAMPORTS_PER_SOL)?.toLong() ?: 0L
        if (recipient.isBlank()) {
            updateStatus(getString(R.string.seeker_wallet_status_invalid_recipient))
            return
        }
        if (amountLamports <= 0L) {
            updateStatus(getString(R.string.seeker_wallet_status_invalid_amount))
            return
        }

        setBusy(true)
        try {
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
                    updateStatus(getString(R.string.seeker_wallet_status_send_success, amountSol, shortAddress(recipient)))
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
