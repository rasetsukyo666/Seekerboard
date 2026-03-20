package helium314.keyboard.seeker

enum class SolanaCluster(
    val label: String,
    val rpcUrl: String,
) {
    MAINNET(
        label = "Mainnet",
        rpcUrl = "https://api.mainnet-beta.solana.com",
    ),
    DEVNET(
        label = "Devnet",
        rpcUrl = "https://api.devnet.solana.com",
    ),
}
