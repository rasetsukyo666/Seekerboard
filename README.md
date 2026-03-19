# SeekerKeyboard

Android-first private keyboard + companion-app starter aimed at a Seeker keyboard wallet flow.

## Current scope

- Compose Android settings/onboarding app
- Android IME module with a private keyboard service scaffold
- Keyboard utility strip with wallet, clipboard, theme, and settings drawers
- Review handoff activity for high-risk wallet actions launched from the keyboard
- Alternate chooser strip for long-press characters
- Swipe gestures for cursor move and delete actions
- Multi-layer symbols and stronger daily-driver key layout
- Unified SOL + native stake + SKR portfolio snapshot carried into the keyboard
- Clipboard history plus pinned clipboard snippets
- Consolidation fee preview carried into the keyboard wallet drawer
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
- Companion app screen for onboarding, advanced settings, and transaction review handoff

## Modules

- `:app` settings/onboarding app only
- `:ime` private keyboard service with wallet, clipboard, theme, and settings utility drawers
- consolidation fee model currently carried as `10 SKR/source` with a `100 SKR` cap in the keyboard wallet drawer
- unified account model currently surfaced as `Spendable SOL`, `Native Stake`, `SKR Position`, and `Token Accounts`

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

CI is the intended verification path for builds and artifacts. The workflow now splits `app` and `ime` into separate jobs so failures isolate faster and reports upload even when one module breaks.

## Device QA

Recommended manual QA on Seeker-class devices:

1. Enable `SeekerKeyboard`, switch to it, and confirm the IME reopens cleanly after app switches.
2. Test typing basics:
   `shift`, `caps`, `123`, `#+=`, backspace, long-press alternates, and clipboard history.
3. Test gesture actions:
   swipe on `space` for cursor movement, swipe on `⌫` for char/word deletion.
4. Test wallet lifecycle:
   connect, refresh, send, SKR action, native stake action, confirm the review screen copy is accurate, close wallet app, then return to the same text field.
5. Test consolidation from the keyboard accounts drawer with both likely-compatible and risky source sets.
6. Confirm the IME clears transient alternate chooser state after hiding/reopening.
7. Test clipboard pinning and reuse from the keyboard clipboard drawer.
8. Confirm the unified account rows match on-chain SOL balance, native stake totals, SKR totals, and token-account count after every wallet action.

## Release discipline

1. Treat `main` as CI-clean only.
2. Ship device QA from Seeker-class hardware before promoting a build.
3. Keep review-handoff copy accurate for every wallet action so keyboard intent matches wallet approval.
4. Validate session restore after process death, wallet app switch, and IME hide/show.

## Next steps

1. Add true popup-style alternate selection anchored to touch position.
2. Expand swipe input beyond gesture actions into fuller glide typing support.
3. Add more layout modes, language packs, and per-layout long-press data.
4. Keep tightening Seeker/Saga device QA around IME lifecycle and wallet return flows.
