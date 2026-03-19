#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UPSTREAM_DIR="${ROOT_DIR}/../heliboard_upstream"

if [[ ! -d "${UPSTREAM_DIR}/.git" ]]; then
  echo "Expected upstream clone at ${UPSTREAM_DIR}" >&2
  exit 1
fi

echo "HeliBoard upstream:"
git -C "${UPSTREAM_DIR}" rev-parse HEAD
git -C "${UPSTREAM_DIR}" status --short

echo
echo "Key upstream entry points:"
printf '  %s\n' \
  "app/src/main/java/helium314/keyboard/latin/LatinIME.java" \
  "app/src/main/java/helium314/keyboard/keyboard/KeyboardSwitcher.java" \
  "app/src/main/java/helium314/keyboard/latin/InputView.java" \
  "app/src/main/java/helium314/keyboard/latin/suggestions/SuggestionStripView.kt" \
  "app/src/main/java/helium314/keyboard/keyboard/MainKeyboardView.java"

echo
echo "Read docs/heliboard-pivot.md before transplanting files."
