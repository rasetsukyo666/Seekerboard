# Seekerboard Store Readiness

This is the minimum release checklist for shipping Seekerboard to the Solana Mobile dApp Store.

## Branding

- Final app icon
- Final splash/startup assets
- Final app name and store description
- Screenshots for listing

## Legal and Attribution

- Keep HeliBoard/Open Source attribution in repo and distributed source
- Keep upstream license files in `vendor/heliboard_src/`
- Include `NOTICE` in the shipped source tree

## Product Requirements

- Real wallet connect flow
- Real send flow
- Real receive flow
- Stable return path from wallet approval back into keyboard/app
- No broken placeholder actions in the shipped APK

## Solana Scope for V1

- Solana-first wallet UX
- No fee lane in the primary wallet flow
- Optional staking/consolidation can come later
- SKR can remain secondary or disabled for store v1

## Release Requirements

- Signed release APK
- Version name/code finalized
- Basic privacy policy / app support URL
- Submission metadata prepared for Solana Mobile publisher flow

## Current Status

Current HeliBoard pivot branch:
- HeliBoard typing base is in place
- Seeker wallet toolbar entry exists
- Solana branding/theme exists

Still required before claiming store-ready:
- Replace wallet placeholder screen with working connect/send/receive
- Validate real device behavior on Solana phones
