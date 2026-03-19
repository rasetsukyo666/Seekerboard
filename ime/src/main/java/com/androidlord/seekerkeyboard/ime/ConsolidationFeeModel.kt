package com.androidlord.seekerkeyboard.ime

data class ConsolidationFeeQuote(
    val sourceCount: Int,
    val feeInSkr: Int,
    val perSourceFeeInSkr: Int,
    val capInSkr: Int,
)

object ConsolidationFeeModel {
    const val PLATFORM_FEE_PER_SOURCE_SKR = 10
    const val PLATFORM_FEE_CAP_SKR = 100

    fun quote(sourceCount: Int): ConsolidationFeeQuote {
        val normalized = sourceCount.coerceIn(1, 99)
        return ConsolidationFeeQuote(
            sourceCount = normalized,
            feeInSkr = (normalized * PLATFORM_FEE_PER_SOURCE_SKR).coerceAtMost(PLATFORM_FEE_CAP_SKR),
            perSourceFeeInSkr = PLATFORM_FEE_PER_SOURCE_SKR,
            capInSkr = PLATFORM_FEE_CAP_SKR,
        )
    }
}
