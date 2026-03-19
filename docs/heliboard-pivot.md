# HeliBoard Pivot

## Decision

Replace the custom typing core with HeliBoard and keep Seeker's wallet, staking, session, and security flows as a custom integration layer.

Reason:
- custom key handling is still too sticky and unreliable
- delete repeat and popup behavior are still behind mature keyboards
- suggestion quality and typing feel need a proven input engine, not more patching

## Upstream Baseline

- Upstream repo: `https://github.com/Helium314/HeliBoard`
- Inspected local clone: `/data/user/0/com.codex.mobile/files/home/codex/heliboard_upstream`
- Upstream commit: `24f91266b849db8663f907e86013cbbf0d10b6db`

## Keep

These Seeker pieces stay custom:
- wallet auth and review flow
- biometric / device credential gate
- wallet session snapshot store
- SOL / SKR staking logic
- SOL / SKR unified account model
- cluster selection and Seeker-specific settings

Current Seeker sources that should be preserved:
- `app/src/main/java/com/androidlord/seekerwallet/WalletBridgeActivity.kt`
- `app/src/main/java/com/androidlord/seekerwallet/WalletReviewActivity.kt`
- `app/src/main/java/com/androidlord/seekerwallet/WalletAccessGateActivity.kt`
- `app/src/main/java/com/androidlord/seekerwallet/data/WalletSessionStore.kt`
- `app/src/main/java/com/androidlord/seekerwallet/wallet/`
- `ime/src/main/java/com/androidlord/seekerkeyboard/ime/WalletAccessGuardStore.kt`

## Replace

HeliBoard classes to use as the typing foundation:
- `helium314.keyboard.latin.LatinIME`
- `helium314.keyboard.keyboard.KeyboardSwitcher`
- `helium314.keyboard.latin.InputView`
- `helium314.keyboard.latin.suggestions.SuggestionStripView`
- `helium314.keyboard.keyboard.MainKeyboardView`
- `helium314.keyboard.keyboard.PointerTracker`

Why these matter:
- `LatinIME` owns the real IME lifecycle, input logic, and suggestion updates
- `KeyboardSwitcher` controls keyboard states and toolbar/suggestion strip composition
- `SuggestionStripView` already supports toolbar behavior and is the cleanest place to attach Seeker controls
- `MainKeyboardView` and `PointerTracker` handle touch, repeat, popup previews, and gesture behavior

## Integration Path

### Phase 1

Vendor HeliBoard as the keyboard engine baseline and get it building in this repo without Seeker wallet features.

### Phase 2

Add Seeker controls into HeliBoard's top strip instead of the current custom strip.

Target controls:
- wallet
- clipboard
- theme
- settings

Preferred interaction:
- default strip shows suggestions
- expandable action toggles to Seeker tools
- wallet button launches Seeker auth gate before exposing wallet controls

### Phase 3

Embed the Seeker wallet drawer above the keyboard using HeliBoard's input view / toolbar area.

Wallet drawer should provide:
- connect / reconnect
- SOL account management
- SKR account management
- unified account view
- stake actions
- consolidation actions

### Phase 4

Remove the current custom typing implementation once the HeliBoard path is stable.

Files expected to disappear or shrink heavily:
- `ime/src/main/java/com/androidlord/seekerkeyboard/ime/SeekerKeyboardView.kt`
- `ime/src/main/java/com/androidlord/seekerkeyboard/ime/SeekerKeyboardService.kt`
- `ime/src/main/java/com/androidlord/seekerkeyboard/ime/GlideTypingEngine.kt`

## Theme Direction

Yes, the default Seeker look can stay.

Keep:
- current Solana-style dark gradient
- metallic keys
- `#14F195` legends and accents
- theme label `Original`

Implementation note:
- do not preserve HeliBoard visual defaults
- keep HeliBoard behavior, not its stock look
- theme mapping should happen through Seeker-controlled colors and resources

## Licensing

HeliBoard is GPL-3.0 based. If SeekerKeyboard adopts HeliBoard code directly, the resulting keyboard codebase needs to respect that license.

This is not a blocker, but it should be treated as an explicit product decision before wider distribution.

## Next Engineering Pass

1. Create a dedicated branch or module for the HeliBoard-based IME core.
2. Port Seeker's top-strip behavior onto HeliBoard's suggestion / toolbar layer.
3. Reattach wallet auth gate and review handoff.
4. Rebuild the Seeker wallet drawer on top of HeliBoard's input surface.
