#!/usr/bin/env bash
# Engineered by uncoalesced
#
# Enforces two hard project rules that have regressed repeatedly:
#   1. Zero emoji anywhere in source (comments, strings, identifiers, resources).
#   2. Every source file carries the "Engineered by uncoalesced" provenance
#      watermark near the top.
#
# Runs in CI (see .github/workflows/ci.yml) and is safe to run locally:
#   bash scripts/check-source-rules.sh
#
# Exit code 0 = clean, 1 = at least one violation (details printed).

set -uo pipefail

# Scan real source only; never build output, generated code, or vendored trees.
SRC_GLOBS=(
  "app/src" "keyboard-core/src" "sticker-core/src" "transfer/src"
)
# File types that must obey the watermark rule.
WATERMARK_EXT_RE='\.(kt|kts|java)$'

fail=0

# --- 1. Emoji scan -----------------------------------------------------------
# Ranges cover the common pictographic/emoji blocks. Dingbats that are NOT emoji
# by the Unicode Emoji property (stars, arrows, keycap symbols such as the
# shift/backspace/enter glyphs) are intentionally excluded so functional
# typography is not flagged.
#
# The pattern uses PCRE \x{...} codepoint escapes (NOT shell \U expansion) and
# grep must run in a UTF-8 locale, or `grep -P` refuses astral-plane ranges with
# "supports only unibyte and UTF-8 locales". Both are required for detection to
# actually work -- see scripts test in the commit that introduced this.
# Note the split around U+2605-2606: BLACK STAR / WHITE STAR are dingbats with
# Emoji=No and are used as the (non-emoji) favourite indicator, so they are
# excluded while the rest of the Misc-Symbols block is scanned.
EMOJI_RE='[\x{1F000}-\x{1FAFF}\x{2600}-\x{2604}\x{2607}-\x{26FF}\x{2700}-\x{27BF}\x{2B00}-\x{2BFF}\x{FE0F}]'

emoji_hits=$(LC_ALL=C.UTF-8 grep -RInP "$EMOJI_RE" \
  --include='*.kt' --include='*.kts' --include='*.java' --include='*.xml' \
  "${SRC_GLOBS[@]}" 2>/dev/null || true)

if [ -n "$emoji_hits" ]; then
  echo "EMOJI RULE VIOLATION -- emoji found in source:"
  echo "$emoji_hits"
  echo
  fail=1
fi

# --- 2. Watermark scan -------------------------------------------------------
missing_watermark=""
while IFS= read -r -d '' f; do
  # Look in the first 5 lines so a leading license/package block is allowed.
  if ! head -n 5 "$f" | grep -q "Engineered by uncoalesced"; then
    missing_watermark+="$f"$'\n'
  fi
done < <(find "${SRC_GLOBS[@]}" -type f -regextype posix-extended -regex ".*${WATERMARK_EXT_RE}" -print0 2>/dev/null)

if [ -n "$missing_watermark" ]; then
  echo "WATERMARK RULE VIOLATION -- files missing '// Engineered by uncoalesced':"
  echo "$missing_watermark"
  fail=1
fi

if [ "$fail" -eq 0 ]; then
  echo "Source rule check passed: no emoji, all source files watermarked."
fi

exit "$fail"
