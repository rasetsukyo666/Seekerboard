package helium314.keyboard.seeker

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import helium314.keyboard.latin.R

class SeekerWalletActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_seeker_wallet)

        val status = findViewById<TextView>(R.id.wallet_status)
        status.text = getString(R.string.seeker_wallet_status_ready)

        findViewById<Button>(R.id.wallet_close).setOnClickListener { finish() }
        findViewById<Button>(R.id.wallet_connect).setOnClickListener {
            status.text = getString(R.string.seeker_wallet_status_connect)
        }
        findViewById<Button>(R.id.wallet_accounts).setOnClickListener {
            status.text = getString(R.string.seeker_wallet_status_accounts)
        }
        findViewById<Button>(R.id.wallet_stake).setOnClickListener {
            status.text = getString(R.string.seeker_wallet_status_stake)
        }
        findViewById<Button>(R.id.wallet_skr).setOnClickListener {
            status.text = getString(R.string.seeker_wallet_status_skr)
        }
    }
}
