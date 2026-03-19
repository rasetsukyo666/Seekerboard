# SeekerKeyboard

Android-first private keyboard + companion-app starter aimed at a Seeker keyboard wallet flow.

## Current scope

- Compose Android companion-app shell
- Android IME module with a private keyboard service scaffold
- Companion-app keyboard customization controls
- Solana Mobile Wallet Adapter connect / disconnect
- Sign In with Solana
- Detached message signing
- SOL balance and SPL token reads over JSON-RPC
- Native SOL transfer signing through the connected wallet
- Native stake account reads over JSON-RPC
- Native stake delegate / deactivate / withdraw transaction builders
- Official SKR stake / unstake / withdraw transaction flow
- Devnet airdrop helper for device QA
- Product-facing UI for keyboard-launch flow, native stake management, and SKR actions

## Intended architecture

- Private keyboard/IME surface for quick wallet access
- Companion app screen for transaction review, wallet connect, signing, and staking

## Modules

- `:app` companion app for wallet connect, review, staking, account management, and keyboard settings
- `:ime` private keyboard service with wallet-launch key and basic customization support

## Build

Requirements:

- Android Studio with SDK 35
- JDK 17
- an MWA-compatible wallet on-device

Build debug APK:

```bash
./gradlew :app:assembleDebug
```

Build the IME library:

```bash
./gradlew :ime:assembleDebug
```

## Next steps

1. Extract the transaction-building logic from the prior React Native app into Kotlin services.
2. Validate the native stake instruction account metas against device wallet signing.
3. Add stake-account creation flow for first-time native staking.
4. Expand the IME toward HeliBoard-level customization, layouts, and gesture behavior.
