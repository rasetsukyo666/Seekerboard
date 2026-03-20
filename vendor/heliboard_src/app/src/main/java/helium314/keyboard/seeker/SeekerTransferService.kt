package helium314.keyboard.seeker

import com.funkatronics.encoders.Base58
import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.AccountMeta
import com.solana.transaction.Message
import com.solana.transaction.Transaction
import com.solana.transaction.TransactionInstruction
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SeekerTransferService {
    fun buildTransferTx(
        fromAddress: String,
        toAddress: String,
        lamports: Long,
        recentBlockhash: String,
    ): Transaction {
        val instruction = TransactionInstruction(
            SYSTEM_PROGRAM_ID,
            listOf(
                AccountMeta(pubkey(fromAddress), isSigner = true, isWritable = true),
                AccountMeta(pubkey(toAddress), isSigner = false, isWritable = true),
            ),
            encodeU32(2) + encodeU64(lamports),
        )
        return Transaction(
            Message.Builder()
                .addInstruction(instruction)
                .setRecentBlockhash(recentBlockhash)
                .build()
        )
    }

    private companion object {
        private val SYSTEM_PROGRAM_ID = pubkey("11111111111111111111111111111111")

        private fun pubkey(value: String): SolanaPublicKey {
            return SolanaPublicKey(Base58.decode(value))
        }

        private fun encodeU32(value: Int): ByteArray {
            return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
        }

        private fun encodeU64(value: Long): ByteArray {
            return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array()
        }
    }
}
