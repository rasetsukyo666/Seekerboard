# SeekerWallet

Android-first Solana wallet starter aimed at a Seeker keyboard + companion-app flow.

## Current scope

- Compose Android app shell
- Solana Mobile Wallet Adapter connect / disconnect
- Sign In with Solana
- Detached message signing
- SOL balance and SPL token reads over JSON-RPC
- Native SOL transfer signing through the connected wallet
- Native stake account reads over JSON-RPC
- Native stake delegate / deactivate / withdraw transaction builders
- Official SKR stake / unstake / withdraw transaction flow
- Devnet airdrop helper for device QA
- Product-facing UI for keyboard launcher direction, native stake management, and SKR actions

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
2. Validate the native stake instruction account metas against device wallet signing.
3. Add stake-account creation flow for first-time native staking.
4. Add a real IME launcher that deep-links into the companion review flow.
