# Seeker Integration

This directory is a vendored snapshot of HeliBoard used as the future typing engine base for SeekerKeyboard.

Upstream:
- Repo: `https://github.com/Helium314/HeliBoard`
- Commit: `24f91266b849db8663f907e86013cbbf0d10b6db`

Seeker-specific direction:
- keep HeliBoard for typing, popup previews, long-press behavior, delete repeat, and suggestions
- rebrand the app and IME to SeekerKeyboard
- keep the Seeker wallet auth, review, SOL/SKR staking, and unified account flows as custom integration
- keep the Seeker default visual direction: Solana-style dark gradient, metallic keys, and green accents

Primary integration points:
- `app/src/main/java/helium314/keyboard/latin/LatinIME.java`
- `app/src/main/java/helium314/keyboard/keyboard/KeyboardSwitcher.java`
- `app/src/main/java/helium314/keyboard/latin/InputView.java`
- `app/src/main/java/helium314/keyboard/latin/suggestions/SuggestionStripView.kt`

The current Seeker custom IME should be treated as a wallet UI reference, not as the long-term typing engine.
