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

# --- 3. SharedPreferences change listeners -----------------------------------
# Banned outright, because one shipped in v0.1.5-BETA and did nothing in release.
#
# SharedPreferences stores its listeners in a WeakHashMap. The `private val
# listener` field was the only strong reference to ours, and R8 -- seeing a field
# written once and read once -- removed the field. The listener was then collected
# at the first GC and no preference change reached any StateFlow again for the life
# of the process. It worked perfectly in debug, which is why it survived to a
# release build: every settings toggle appeared dead until the app was restarted,
# and the privacy switch could be turned on and never off.
#
# The preference classes are @Singleton and every write goes through their own
# setters, so the setter can publish to its flow directly and no listener is needed.
# If one is ever genuinely required, it must be kept alive by something R8 cannot
# prove unused -- and this check must then be given a documented exemption rather
# than deleted.
listener_hits=$(grep -RIn "registerOnSharedPreferenceChangeListener" \
  --include='*.kt' --include='*.java' \
  "${SRC_GLOBS[@]}" 2>/dev/null || true)

if [ -n "$listener_hits" ]; then
  echo "PREFS LISTENER RULE VIOLATION -- SharedPreferences listeners are weakly held"
  echo "and R8 deletes the field that keeps them alive. Publish from the setter instead:"
  echo "$listener_hits"
  echo
  fail=1
fi

# --- 4. Notification listener ------------------------------------------------
# Banned outright, at the manifest level, because declaring it makes the app
# uninstallable for this project's entire audience.
#
# v0.1.6-BETA shipped a NotificationListenerService so the media row could show a
# track name -- MediaSessionManager.getActiveSessions() will not answer without one.
# It was scoped as tightly as the platform allows: off by default, granted only from
# system Settings, overriding no notification callback at all. Google Play Protect
# blocks the install anyway, with "App blocked to protect your device", because it
# reads the manifest and not the class. FluxBoard is distributed only by sideloading,
# so the app simply could not be installed.
#
# Matched on declaration syntax -- the quoted, fully-qualified permission and the
# base class itself -- rather than on the bare constant name, so the manifest and
# MediaTransport can keep explaining in prose why this is banned without tripping the
# check that enforces it.
notif_hits=$(grep -RInE '"android\.permission\.BIND_NOTIFICATION_LISTENER_SERVICE"|:[[:space:]]*NotificationListenerService\('   --include='*.xml' --include='*.kt' --include='*.java'   "${SRC_GLOBS[@]}" 2>/dev/null || true)

if [ -n "$notif_hits" ]; then
  echo "NOTIFICATION LISTENER RULE VIOLATION -- Play Protect blocks the install of any"
  echo "sideloaded app that declares BIND_NOTIFICATION_LISTENER_SERVICE, whatever the"
  echo "service actually does. See keyboard-core's manifest for the full reasoning:"
  echo "$notif_hits"
  echo
  fail=1
fi

if [ "$fail" -eq 0 ]; then
  echo "Source rule check passed: no emoji, all source files watermarked, no prefs listeners,"
  echo "no notification listener."
fi

exit "$fail"
