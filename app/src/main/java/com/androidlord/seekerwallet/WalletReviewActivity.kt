package com.androidlord.seekerwallet

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.androidlord.seekerwallet.data.WalletReviewStore
import com.androidlord.seekerwallet.data.PendingWalletReview
import com.androidlord.seekerwallet.theme.SeekerTheme
import com.androidlord.seekerwallet.theme.ThemePreset

class WalletReviewActivity : ComponentActivity() {
    private lateinit var reviewStore: WalletReviewStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reviewStore = WalletReviewStore(applicationContext)
        val review = PendingWalletReview(
            action = intent?.getStringExtra("wallet_action").orEmpty(),
            title = intent?.getStringExtra("review_title").orEmpty(),
            summary = intent?.getStringExtra("review_summary").orEmpty(),
            detail = intent?.getStringExtra("review_detail").orEmpty(),
        ).also(reviewStore::save)
        setContent {
            SeekerTheme(themePreset = ThemePreset.GRAPHITE) {
                WalletReviewScreen(
                    review = review,
                    onConfirm = {
                        val action = intent?.getStringExtra("wallet_action").orEmpty()
                        val extras = intent?.extras
                        startActivity(
                            Intent(this, WalletBridgeActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                putExtra("wallet_action", action)
                                extras?.keySet()?.forEach { key ->
                                    if (key != "wallet_action") {
                                        putExtra(key, extras.getString(key))
                                    }
                                }
                            }
                        )
                        finishAndRemoveTask()
                    },
                    onCancel = {
                        reviewStore.clear()
                        finishAndRemoveTask()
                    },
                )
            }
        }
    }
}

@Composable
private fun WalletReviewScreen(
    review: PendingWalletReview,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface)))
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Review Wallet Action",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(review.title.ifBlank { "Pending transaction" }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(review.summary.ifBlank { "Review the transaction details before opening your wallet approval flow." }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (review.detail.isNotBlank()) {
                        Text(review.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open Wallet Approval")
            }
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cancel")
            }
        }
    }
}
