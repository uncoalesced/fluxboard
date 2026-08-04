<!-- Engineered by uncoalesced -->

# FluxBoard Roadmap and Bug Tracker

Current version: `v0.1.2-ALPHA` (versionCode 3), branch `development`.

This is the document `README.md` has pointed at since v0.1.0 and which was
never committed. It is the single place where open issues live. If something is
known to be broken and is not written down here, that is a bug in this file.

## How entries are built

Every entry below comes from one of: the git history, a commit message, the
source tree as it currently stands, or a device finding recorded in a commit
body. Where an item's status could not be established without a device, it says
so rather than guessing.

There are **no `TODO` or `FIXME` comments anywhere in this codebase** -- that
was checked, not assumed. One deliberate-shortcut marker exists
(`ponytail:` in `CursorMove.kt:47`) and it is recorded below.

## Status vocabulary

| Status | Means |
|---|---|
| **OPEN** | Reproducible or structurally present. Nothing has been done. |
| **FIXED (unverified)** | Code changed and unit-tested. Never run on a device. |
| **NEEDS DEVICE** | Believed fixed, but the only proof available is on hardware. |
| **BLOCKED** | Waiting on a decision or an asset from Joel. |
| **BY DESIGN** | Looks like a bug, is not. Recorded so it stops being re-filed. |

Nothing in this file may be marked plainly "fixed". This project has twice had
a phase marked complete on the strength of code that read correctly and did not
work. **FIXED (unverified)** is the strongest claim any entry gets before it has
been used on a phone.

---

## 1. Severity 1 -- data loss, or the app becoming unusable

### 1.1 Unrecoverable blank keyboard

**Status: OPEN (cause unknown), with mitigations landed and a recovery path added.**

Keys stop being drawn while the keyboard reportedly still responds to touch.
The state survives force-stop, cache clearing and reboot, and has only ever been
cleared by uninstalling and reinstalling -- because the active theme id and
layout id live in SharedPreferences while the theme and layout files live in
`filesDir`, and neither is touched by any of those operations.

**No root cause has been established.** What follows are ranked hypotheses, each
with the observation that would confirm or kill it. Do not treat any of them as
the answer.

**H1 -- a theme override combination paints invisible keys.** Most likely
historically. Two colour pickers defaulting to the same swatch reaches "glyph
colour equals key fill" in two taps, and a fully transparent glyph is reachable
from one slider.
*Discriminator:* on a device showing the fault,
`run-as com.uncoalesced.stickykeys cat shared_prefs/keyboard_preferences.xml`
for `active_theme_id`, plus a listing of `filesDir/themes`. If the active id
names a custom theme carrying `keyStyle` fill or text overrides, H1.
*Note:* `run-as` works on the debug build only.
*Falsification:* v0.1.2 added `KeyboardTheme.sanitized()`, which drops exactly
these combinations. **If this recurs on v0.1.2 or later, H1 is dead** -- which is
itself worth knowing, because it promotes everything below it.

**H2 -- the same defect in `LayoutManager` rather than `ThemeManager`.**
`LayoutManager` carried the identical `if (layout != null) set it` shape, so a
dangling `active_layout_id` was silently ignored and the preference never
repaired. It did not blank the keyboard on its own -- the value left in place is
the built-in default -- but it is one edit away from the same failure, and
nothing sanitised a layout.
*Discriminator:* `active_layout_id` in the same prefs file pointing at a layout
that is not in `filesDir/layouts`.
*Status:* fixed this session regardless of whether it is this bug. See 1.2.

**H3 -- Compose render failure in the IME window** (detached recomposer, or a
`ComposeView` outliving its ViewTree owners).
*Discriminator, and it is decisive:* a Compose hierarchy that draws nothing has
no semantics or gesture nodes either, so **H3 predicts dead keys, not
functional invisible ones.** The report says the keys still worked. That argues
strongly against H3 -- but the report needs re-confirming, because "it still
worked" may have meant "it still vibrated", which is not the same thing.
**This single question is the highest-value thing to establish next.**

**H4 -- the background image drawn over the keys.** *Eliminated.* Draw order in
`TypingKeyboardView` puts the image and its overlay first and the key `Column`
after. Recorded so it is not re-investigated.

**H5 -- an extreme `KeyStyle` haze radius or border width** blowing out the
render node. Untested, cheap to bound-check, no evidence either way.

**Mitigations already in place (v0.1.2):** `ThemeManager.resolveActive` falls
back and repairs the stored preference; unparseable theme files are quarantined
as `.corrupt`; the available-themes list is never published empty;
`KeyboardTheme.sanitized()` drops invisible-key override combinations.
`ThemeFallbackTest` pins all of it.

**Recovery path (v0.1.5, unverified):** "Reset keyboard appearance" in Keyboard
settings deletes every custom theme, background and layout and returns to the
shipped defaults. This works whatever the cause turns out to be, and converts
the severity of this bug from "reinstall" to "two taps". It was deliberately
built before the investigation rather than after it.

**Open questions for Joel:**
1. When it happened, were the keys genuinely *typing*, or only vibrating?
2. Had that device ever had a custom theme or custom layout saved?
3. Is it reproducible on v0.1.2 at all, or only v0.1.1 and earlier?

### 1.2 `LayoutManager` ignored an unresolvable active layout id

**Status: FIXED (unverified). Found this session.**

Both observers were `if (layout != null) _activeLayout.value = layout`, the
exact shape `ThemeManager` was fixed for in v0.1.2. An `active_layout_id`
matching nothing was silently dropped and the preference never repaired, so the
user's chosen layout stayed un-applied on every launch with nothing anywhere
saying why. Additionally, a custom layout file that failed to parse was re-read,
re-thrown and re-skipped on every single load, and a layout that parsed but
failed validation (missing `SPACE`, `DEL`, `ENTER` or `SYMBOLS`) was loaded and
made active anyway.

Resolution now falls back to the built-in preset and repairs the preference;
unparseable and invalid layout files are quarantined as `.corrupt`.
`LayoutFallbackTest` covers all six transitions of the pure resolution function.

### 1.3 The suggestion strip deleted the wrong span of the user's text

**Status: FIXED (unverified). Found during v0.1.5 planning.**

Tapping a suggestion ran
`replaceTextBeforeCursor(currentWord.length, "$suggestion ")`. `currentWord` is
a local mirror built by appending on each key press, correct only while this
keyboard is the sole editor. After a caret tap, a paste, a host-side selection
replacement, or a space-bar scrub, it described text that was no longer there --
and the replacement then deleted that many characters of whatever *was* there.

This is the item previously filed as "autocorrect gives up on mid-word
backspace". It is not a missing feature; it is data loss.

The span is now derived from the editor at the moment of the edit, via
`KeyboardController.textBeforeCursor`. `WordContextTest` pins the extraction
rules including the cases that produce an off-by-one (interior apostrophes,
leading quote marks, punctuation boundaries).

### 1.4 The space-bar scrub was writing word fragments into the personal dictionary

**Status: FIXED (unverified). Found during v0.1.5 planning.**

`onScrub` called `onWordFinished()` on every step of the drag, and that function
*learns* what it clears. Scrubbing out of the middle of a word therefore taught
the personal dictionary the fragment under the caret at that instant. Once a
word has been seen twice, `PredictionEngine` treats it as deliberate and refuses
to autocorrect it -- so the corruption compounds silently, outlives the gesture,
and degrades correction quality in a way no user could trace back to a cursor
drag.

Fixed by adding `onWordAbandoned()`, which clears without learning. The guard
was verified by reintroducing the bug on purpose and confirming
`WordContextTest` fails, then reverting.

---

## 2. Severity 2 -- the keyboard behaves wrongly

### 2.1 Caps lock state is unpredictable

**Status: OPEN. Design agreed, not implemented.**

`handleKeyPress` cycles `LOWER -> UPPER -> CAPS_LOCK -> LOWER` unconditionally,
with no time window at all. A user tapping shift twice slowly means "on, then
off" and gets caps lock, and turning it back off then costs a third tap.

Compounding it, a `LaunchedEffect(shouldAutoCapitalize)` forces `UPPER` on and
`LOWER` off, which can override a shift the user pressed deliberately. **Both
must be fixed in the same change** or the second will keep producing the same
report after the first is fixed.

Agreed replacement: a pure `nextShiftState(current, sinceLastTapMs)` with
`Off -> OneShot`, `OneShot -> Locked` inside the window, `OneShot -> Off`
outside it, `Locked -> Off` on any tap. Threshold from
`ViewConfiguration.getDoubleTapTimeout()`.

*Flagged as high fake-plausible risk:* timing logic written inline in a
composable is untestable, and the third transition is where a plausible-looking
implementation goes wrong. Must ship as a pure function with a six-cell table
test plus the "autocap fired, then the user tapped shift 50ms later" case.

### 2.2 Autocorrect fired on the space bar and nowhere else

**Status: FIXED (unverified) for punctuation. Enter deliberately not fixed.**

Ending a word with `.` `,` `!` `?` `;` `:` never attempted a correction, so
autocorrect appeared to work on roughly half the user's words with no visible
pattern. Punctuation now runs the same path, guarded by the same generation
token.

**Enter was in scope and was deliberately left out, which needs a decision.**
Correcting on Enter is not safely possible: the correction lookup is suspending,
and this codebase forbids a key commit waiting on one. Correcting *after*
`sendEnter` is too late in any send-on-enter field -- the message is already
gone, and the correction would land in the next one. The options are to accept
no correction on Enter (current behaviour), or to block the Enter key on the
lookup, which is the thing the invariant exists to prevent. **Joel's call.**

### 2.3 The word tracker was never resynced from the editor

**Status: FIXED (unverified). Root cause of 1.3 and of the "backspace" report.**

`currentWord` was a write-only mirror. `StickyKeysIME` now predicts the caret
after each of its own edits and, in `onUpdateSelection`, re-reads a bounded 48
characters only when the actual caret disagrees with the prediction. That keeps
the read off the keystroke path -- an unconditional read there would be the
blocking-IPC-per-key this project explicitly forbids.

Auto-capitalize is re-derived from the same read, so moving the caret into the
middle of existing text no longer leaves the board latched to upper case.

### 2.4 Double-space does not insert a period

**Status: OPEN.** Not implemented. Shares a design and a threshold constant with
2.1 and should land immediately after it.

*Flagged:* the naive implementation watches for two taps and inserts a period
without reading the text. It demos perfectly and then produces `.. ` after a
sentence, `. ` at the start of a field, and a period after a newline. Requires a
pure `doubleSpaceReplacement(textBeforeCursor)` with those four negative cases
tested.

*Known limitation to accept up front:* some hosts do this themselves, producing
a doubled period, and that is not detectable from an IME.

### 2.5 Accidental `n`/`b` presses near the space bar

**Status: OPEN, partially mitigated, full fix deferred past v0.1.5.**

No hit-test tolerance exists; each key is a Compose `Box` committing its own
output. It is partly handled after the fact by
`PredictionEngine.splitOnMispressedSpace`, which repairs the resulting run-on at
the word level.

The real fix is grid-level hit testing against a key-rectangle table with
per-key bias, as LatinIME and FlorisBoard do. That **replaces the entire per-key
gesture attachment** -- `keyGestures`, press-state, alternates anchoring, the
scrub, and the recomposition-skipping work all sit on top of the current
arrangement. Deferred to its own version deliberately.

*Flagged, with the specific fakes named:* widening the space bar's layout
`weight` changes the visual layout and not hit tolerance; negative padding does
nothing to hit testing. Both look like the fix.

### 2.6 The Enter key looks the same in every app

**Status: OPEN.**

Enter *behaviour* is correct as of v0.1.2 -- `sendEnter` handles the
`IME_ACTION_UNSPECIFIED`(0) vs `IME_ACTION_NONE`(1) trap, multi-line fields, a
null `EditorInfo`, and a declined action. But `keyGlyph("ENTER")` is one static
icon that never becomes Send, Search, Go, Next or Done, so the key does the
right thing everywhere and gives the user no signal about what it will do. That
is indistinguishable from "inconsistent" from the outside, and is very likely
what the original report meant.

---

## 3. Severity 3 -- missing or incomplete

### 3.1 Symbol pages

**Status: page 1 FIXED (unverified). Page 2 BLOCKED.**

The pages were hardcoded `List<List<String>>` outside `KeyboardLayoutConfig`,
converted at render time, which is the single reason they could not be remapped,
weighted, or carry hints and long-press alternates. They are now part of the
layout, with old layout files defaulting to the shipped pages
(`SymbolPageLayoutTest` pins that compatibility promise).

Page 1 content now follows the reference image supplied 2026-08-02. `/` is
present, resolving the long-standing "`/` is missing" report -- it previously
existed on neither symbol page and could only be produced by holding `m`.

**Page 2 has never had a reference** and is migrated verbatim, unchanged. The
standing instruction in `CLAUDE.md` not to invent a layout for it still applies.

**Two consequences of the reference, both now decided (2026-08-02):**
- The reference has **no `$`** on page 1 -- `£` occupies that slot. Joel: keep
  `£` there; `$` and its currency long-press belong on the number row, which is
  a different page, so there is no contradiction. See 3.7 for the part of that
  which is still open.
- The reference has **no emoji key** on the symbols action row. Joel: **deviate
  from the reference and keep it**, because losing it means returning to the
  letters page before emoji can be reached at all. Recorded as a deliberate
  deviation in `KeyboardLayouts.symbolActionRow` and asserted in
  `SymbolPageLayoutTest`.

  *Flagged:* Joel's two answers on this point disagreed with each other -- the
  selected option said "follow the reference, leave it out" and the written
  answer said "deviate, keep it". The written answer was followed because it
  carried the reasoning. Worth one line of confirmation.

### 3.2 Number row: three features want one hint slot

**Status: FIXED (unverified). Option B chosen by Joel, 2026-08-02.**

Three separate asks all wanted the digit keys' single hint slot and single hold
behaviour: corner hints showing symbols, shift-active showing the shifted
symbol, and long-press giving superscripts and vulgar fractions. `KeyGestures`
states the invariant that the corner glyph and the hold output must come from
one field, because the superscript's whole promise is "hold this and you get
that".

Option B implemented: the hint is the shifted symbol, the row shows those
symbols while shift is armed, and the hold strip *leads* with that same symbol
before the superscript and the fractions. All three come from one
`digitShiftPairs` table so they cannot drift.

`NumberRowTest` pins the load-bearing part -- that index 0 of every strip equals
the key's own hint -- plus the six-entry ceiling (digit `1` sits exactly on it)
and the ascending-by-value fraction ordering.

**Still unconfirmed:** the fraction sets for `0` and `3`-`9`. `1` and `2` are
Joel's confirmed spec. The rest follow the same rule and are a proposal;
changing them is an edit to one map in `KeyGestures.kt` and nothing else.

### 3.3 Key remapping

**Status: OPEN.**

The remap dialog is a bare text field validated only by `isNotBlank()`. A user
can set a key's output to `SPACEX`; the layout saves, the key renders that
literal text and does nothing when pressed. Needs a preset picker built on an
explicit action vocabulary, with free text kept but validated.

The layout editor also still only edits the letters pages. Now that the symbol
pages are part of the config (3.1), exposing them in the editor is a UI change
rather than a structural one.

### 3.7 Currency long-press on the number row

**Status: OPEN, needs one clarification before it can be built.**

Agreed: `$` stays on the number row (as digit `4`'s shifted symbol) and holding
it should offer four more currencies.

**The collision to resolve.** Under option B (3.2), digit `4`'s hold strip is
already `$ ⁴ ⅘` -- the shifted symbol, the superscript, the fraction. Adding four
currencies makes seven entries, and six is the practical ceiling for the 40dp
strip on a narrow phone before it clamps to stay on screen. `NumberRowTest`
asserts that ceiling.

Three ways out, none of them picked:
- drop `⁴` and `⅘` from digit `4` only, so its strip becomes `$` plus four
  currencies (breaks the "every digit offers its superscript" consistency);
- keep six and ship three currencies rather than four;
- raise the strip ceiling by shrinking the cell width, which affects every
  long-press on the board and needs measuring on a device.

Also still needed: **which four currencies, and in what order.** Index 0 is what
a TalkBack long-press commits and where the finger already sits.

### 3.4 Ephemeral link sharing does not work off-LAN

**Status: OPEN, known since v0.1.0.**

`RelayClient` points at `ws://10.0.2.2:8080`. `relay/server.js` is real code and
is deployed nowhere. The previous `CHANGELOG.md` listed this feature as
delivered; it is not.

### 3.5 Voice input is a stub

**Status: OPEN.** The mic in the suggestion strip shows a "coming soon" notice.
Wiring it means either `RecognizerIntent` (hands off to another app, which may
not exist on a de-Googled device) or `SpeechRecognizer` with `RECORD_AUDIO`, a
real prompted permission. Needs a decision before any work starts.

Related and now fixed: the comma key carried `hint = "MIC"`, but `longPressFor`
checks `PUNCTUATION_ALTERNATES` before the hint branch and `,` is in that map --
so holding it produced `. ? !` and never voice. The mic glyph advertised
behaviour that never existed.
**Status of that sub-item: OPEN** (the false hint is still in
`KeyboardLayouts.kt`; removing it is queued behind Joel's choice of what the
comma key's alternates should be).

### 3.6 Translate and grammar-check are stubs

**Status: BY DESIGN, for now.** Both are `comingSoon = true` in
`QuickAccessRow`, which carries the flag on the action rather than at the call
site so neither can be wired to a real handler by accident.

---

## 4. Needs a device before anything can be claimed

### 4.1 Sticker delivery to WhatsApp and Discord

**Status: NEEDS DEVICE. Highest-confidence unverified fix in the project.**

The root cause is certain and the fix is verified at the provider level:
`.sticker` is an extension `MimeTypeMap` cannot resolve, so the stock
`FileProvider` answered null for `getType()` on every sticker this app has
produced. Apps that resolve the URI's own type before accepting content --
WhatsApp and Discord both do -- refused it. The same null type is why GIFs fell
out of WhatsApp's inline path into its attachment editor.

**It has never been tried on a device that has either app.** The project's test
phone runs LineageOS with neither installed. An open question that only a device
can settle: whether WhatsApp additionally requires its official sticker-pack
Content Provider *in addition to* generic `commitContent`.

**Do not close this from code review.**

### 4.2 Emoji picker back button

**Status: NEEDS DEVICE, believed resolved.**

Two exits exist -- a leading chevron and a trailing `ABC` chip. The commit
comment records that the chevron alone was tried first and still reported as "no
way back", which is why the `ABC` chip was added. Confirm and close.

### 4.3 Space-bar scrub bounds

**Status: NEEDS DEVICE, believed resolved.**

`cursorTargetFor` returns null at both ends and `moveCursor` skips the redundant
`setSelection`, so a scrub past either end is a no-op rather than an escape.
`CursorMoveTest` covers the boundaries. The severe part of this bug -- the DPAD
focus escape that left the text field unfocused -- was fixed in v0.1.2.

Remaining and genuinely open: there is no feedback when the caret reaches an
end, so the gesture reads as having died.

### 4.4 Scroll-gesture "Tab leak"

**Status: CANNOT IDENTIFY -- needs Joel to restate the symptom.**

This was carried over as an open item but could not be matched to anything in
the codebase with confidence, and guessing which bug it refers to would be worse
than saying so. The two scrollable tab surfaces are:

- `EmojiPickerView.kt:113` -- a `LazyRow` of category tabs, each a
  `Box.clickable`. Compose's scroll consumes the gesture before a click fires,
  so a tab should not activate on scroll. A related issue *was* fixed in v0.1.2:
  the back chevron sits where a horizontal scroll gesture naturally starts, which
  is why the `ABC` chip was added at the opposite edge.
- `StickerIMEView.kt:60` -- a Material3 `PrimaryScrollableTabRow`. **This screen
  is unreachable**: no key routes to `AppMode.STICKERS` any more, since the emoji
  key and the quick-access grid both open `EMOJI_PICKER`. A bug here would be in
  dead code.

If the symptom is "scrolling the emoji category strip selects a tab", say so and
it becomes a five-minute fix. If it is something else, the description above is
the wrong tree.

### 4.5 Everything in the v0.1.5 Unreleased section

Every entry marked FIXED (unverified) in this document and in `CHANGELOG.md` has
unit tests and a green `./gradlew build`. The checklist for running them is
[`docs/testing/v0.1.5-alpha-device-checklist.md`](testing/v0.1.5-alpha-device-checklist.md).

**Partially pre-run on 2026-08-03** against a Redmi Note 11 (LineageOS, Android
15), using a signed release build installed over the existing app with the same
key so no user data was lost. Fourteen steps passed and none failed, covering
the symbols page content (3.1) and the whole number-row design (3.2). Details
and step numbers are in the checklist.

**Sections 1, 2, 3 and 5 of the checklist could not be run at all.** They read
app-private storage, which needs `run-as`, which works only on a debug build --
and swapping the release build for a debug one requires an uninstall, because
the signing keys differ. That would delete every sticker, theme, custom layout,
clipboard entry and learned word on the device. Joel's call whether to make that
trade or to test those items on a spare phone.

### 4.6 Symbols-page digits inherited the fraction strip

**Status: OPEN, found during the 2026-08-03 pre-run. Not a regression, an
unplanned consequence.**

`DIGIT_ALTERNATES` is keyed on a key's output, and `"1"` is the output of a key
on both the number row and row 1 of symbols page 1 -- so the symbols page now
shows hold-affordance dots on its digits and offers the same fraction strip.
Three defensible resolutions are set out as finding F1 in the checklist. Nothing
was changed, because the choice is a product one.

---

## 5. Deliberate shortcuts, recorded so they are not mistaken for bugs

- **Vertical caret movement counts hard newlines only** (`CursorMove.kt:47`,
  carries a `ponytail:` marker). Visual soft-wrapped rows depend on the host's
  own measured layout and are not knowable through an InputConnection at all.
  The only alternative was the DPAD key event that the focus-escape fix exists
  to remove.
- **No automatic segmentation in v1.** The `segmentation/` package is gone.
  `TouchUpScreen` is unreachable, kept for when a real model lands. Do not add an
  engine without a real `.tflite` in the repo.
- **`AppMode.STICKERS` and its view are unreachable.** Retiring them is
  recommended and pending Joel's decision.
- **`connectedAndroidTest` has never run in this project's history.**
  `StickerRepositoryTest` and `SettingsInstrumentedTest` compile and are
  unexecuted.
- **No baseline profile.** `androidx.profileinstaller` is present transitively,
  so this is a build-time addition only -- but generating one needs a device
  running Macrobenchmark, and profiling the IME needs `adb shell ime enable`
  first or it covers only the app UI.
- **The emoji layer has no unit tests.** `build_emoji_data.py`'s parser and
  `EmojiRepository`'s `hasGlyph` filter are both testable and are currently
  verified on-device only.

---

## 6. Blocked on Joel

| # | Item | What is needed |
|---|---|---|
| B1 | Palette rework | Concrete direction: hex list, reference screenshots, or a statement of what is wrong with the current one |
| B2 | Symbols page 2 | A reference image, or explicit permission to design it |
| B4 | Currency long-press | The strip collision in 3.7, plus which four currencies and in what order |
| B5 | Comma-key alternates | The list. Currently `. ? !` |
| B6 | uncoalesced site link | Ship now, or hold behind a null constant until launch |
| B7 | Keyboard resize | The 70-150% height slider already exists; which dimension was meant |
| B8 | Media metadata | Is a Notification Listener ever acceptable |
| B9 | Blank keyboard | The three questions in 1.1 |
| B12 | Double-space-to-period | Default on or off |
| B13 | Retire `AppMode.STICKERS` | Yes or no |
| B15 | Fraction sets for `0` and `3`-`9` | Implemented as a proposal; confirm or amend the one table |
| B16 | Scroll-gesture Tab leak | Still unanswered -- one sentence on the observed behaviour (see 4.4) |

### Resolved 2026-08-02

| # | Item | Decision |
|---|---|---|
| B3 | Number-row hint slot | Option B. Implemented, see 3.2 |
| B10 | Autocorrect on Enter | No correction on Enter. Punctuation only |
| B11 | Emoji key on symbols page | Keep it, deviating from the reference. See the flag in 3.1 |
| B14 | Retroactive `v0.1.2-ALPHA` tag | Not needed; the gap is documented in `CHANGELOG.md` instead |
