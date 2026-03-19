package com.androidlord.seekerwallet.wallet

import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.AccountMeta
import com.solana.transaction.Message
import com.solana.transaction.Transaction
import com.solana.transaction.TransactionInstruction
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NativeStakeService {
    fun buildDelegateStakeTx(
        ownerAddress: String,
        stakeAccountAddress: String,
        validatorVoteAddress: String,
        recentBlockhash: String,
    ): Transaction {
        val instruction = TransactionInstruction(
            STAKE_PROGRAM_ID,
            listOf(
                AccountMeta(SolanaPublicKey(stakeAccountAddress), isSigner = false, isWritable = true),
                AccountMeta(SolanaPublicKey(validatorVoteAddress), isSigner = false, isWritable = false),
                AccountMeta(SYSVAR_CLOCK, isSigner = false, isWritable = false),
                AccountMeta(SYSVAR_STAKE_HISTORY, isSigner = false, isWritable = false),
                AccountMeta(STAKE_CONFIG_ID, isSigner = false, isWritable = false),
                AccountMeta(SolanaPublicKey(ownerAddress), isSigner = true, isWritable = false),
            ),
            encodeU32(2),
        )
        return Transaction(
            Message.Builder()
                .addInstruction(instruction)
                .setRecentBlockhash(recentBlockhash)
                .build()
        )
    }

    fun buildDeactivateStakeTx(
        ownerAddress: String,
        stakeAccountAddress: String,
        recentBlockhash: String,
    ): Transaction {
        val instruction = TransactionInstruction(
            STAKE_PROGRAM_ID,
            listOf(
                AccountMeta(SolanaPublicKey(stakeAccountAddress), isSigner = false, isWritable = true),
                AccountMeta(SYSVAR_CLOCK, isSigner = false, isWritable = false),
                AccountMeta(SolanaPublicKey(ownerAddress), isSigner = true, isWritable = false),
            ),
            encodeU32(5),
        )
        return Transaction(
            Message.Builder()
                .addInstruction(instruction)
                .setRecentBlockhash(recentBlockhash)
                .build()
        )
    }

    fun buildWithdrawStakeTx(
        ownerAddress: String,
        stakeAccountAddress: String,
        destinationAddress: String,
        lamports: Long,
        recentBlockhash: String,
    ): Transaction {
        val instruction = TransactionInstruction(
            STAKE_PROGRAM_ID,
            listOf(
                AccountMeta(SolanaPublicKey(stakeAccountAddress), isSigner = false, isWritable = true),
                AccountMeta(SolanaPublicKey(destinationAddress), isSigner = false, isWritable = true),
                AccountMeta(SYSVAR_CLOCK, isSigner = false, isWritable = false),
                AccountMeta(SYSVAR_STAKE_HISTORY, isSigner = false, isWritable = false),
                AccountMeta(SolanaPublicKey(ownerAddress), isSigner = true, isWritable = false),
            ),
            encodeU32(4) + encodeU64(lamports),
        )
        return Transaction(
            Message.Builder()
                .addInstruction(instruction)
                .setRecentBlockhash(recentBlockhash)
                .build()
        )
    }

    fun buildMergeStakeTx(
        ownerAddress: String,
        destinationStakeAccountAddress: String,
        sourceStakeAccountAddress: String,
        recentBlockhash: String,
    ): Transaction {
        val instruction = TransactionInstruction(
            STAKE_PROGRAM_ID,
            listOf(
                AccountMeta(SolanaPublicKey(destinationStakeAccountAddress), isSigner = false, isWritable = true),
                AccountMeta(SolanaPublicKey(sourceStakeAccountAddress), isSigner = false, isWritable = true),
                AccountMeta(SYSVAR_CLOCK, isSigner = false, isWritable = false),
                AccountMeta(SYSVAR_STAKE_HISTORY, isSigner = false, isWritable = false),
                AccountMeta(SolanaPublicKey(ownerAddress), isSigner = true, isWritable = false),
            ),
            encodeU32(7),
        )
        return Transaction(
            Message.Builder()
                .addInstruction(instruction)
                .setRecentBlockhash(recentBlockhash)
                .build()
        )
    }

    companion object {
        val DEFAULT_VALIDATOR_VOTE = mapOf(
            SolanaCluster.DEVNET to "SKRuTecmFDZHjs2DxRTJNEK7m7hunKGTWJiaZ3tMVVA",
            SolanaCluster.MAINNET to "SKRuTecmFDZHjs2DxRTJNEK7m7hunKGTWJiaZ3tMVVA",
        )

        private val STAKE_PROGRAM_ID = SolanaPublicKey("Stake11111111111111111111111111111111111111")
        private val STAKE_CONFIG_ID = SolanaPublicKey("StakeConfig11111111111111111111111111111111")
        private val SYSVAR_CLOCK = SolanaPublicKey("SysvarC1ock11111111111111111111111111111111")
        private val SYSVAR_STAKE_HISTORY = SolanaPublicKey("SysvarStakeHistory1111111111111111111111111")

        private fun encodeU32(value: Int): ByteArray {
            return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
        }

        private fun encodeU64(value: Long): ByteArray {
            return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array()
        }
    }
}
