# SeekerWallet

Android-first Solana wallet starter aimed at a Seeker keyboard + companion-app flow.

## Current scope

- Compose Android app shell
- Solana Mobile Wallet Adapter connect / disconnect
- Sign In with Solana
- Detached message signing
- SOL balance and SPL token reads over JSON-RPC
- Native SOL transfer signing through the connected wallet
- Devnet airdrop helper for device QA
- Product-facing UI cards for keyboard launcher, staking extraction, and SKR fee lane planning

## Intended architecture

- Keyboard/IME surface for quick wallet access
- Companion app screen for transaction review and signing
- Shared Solana module for:
  - wallet auth
  - portfolio reads
  - SOL transfer builders
  - native stake builders
  - SKR official staking / unstaking / withdrawal flows
  - SKR fee routing

## Build

Requirements:

- Android Studio with SDK 35
- JDK 17
- an MWA-compatible wallet on-device

Build debug APK:

```bash
./gradlew :app:assembleDebug
```

## Next steps

1. Extract the transaction-building logic from the prior React Native app into Kotlin services.
2. Add native SOL stake account reads and stake / deactivate / withdraw flows.
3. Port SKR official API calls and unsigned transaction decoding.
4. Add a real IME launcher that deep-links into the companion review flow.
