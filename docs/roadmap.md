<!-- Engineered by uncoalesced -->

# FluxBoard Roadmap and Bug Tracker

Current version: `v0.1.4-ALPHA` (versionCode 4), branch `development`.

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

**Recovery path (v0.1.4, unverified):** "Reset keyboard appearance" in Keyboard
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

**Status: FIXED (unverified). Found during v0.1.4 planning.**

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

**Status: FIXED (unverified). Found during v0.1.4 planning.**

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

**Status: OPEN, partially mitigated, full fix deferred past v0.1.4.**

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

### 3.8 Passwords and PINs were not recognised at all

**Status: FIXED (unverified). Found this session. This was a live privacy defect.**

Nothing in the codebase read the password input-type variations. Incognito was
driven solely by `IME_FLAG_NO_PERSONALIZED_LEARNING`, which the *host app* has to
set and which Android's own `TextView` does not set for password fields -- AOSP's
LatinIME checks the input type itself for exactly this reason.

The consequence, traced through the code rather than assumed:

- An **alphanumeric password** went down the ordinary letter path. Every
  keystroke extended the tracked word and ran a dictionary lookup whose results
  were drawn in the suggestion strip above the keyboard, and the first space or
  punctuation afterwards called `learn()` -- **writing the password into the
  personal dictionary on disk**, where two sightings then mark it as a word the
  user means.
- A **numeric PIN** was narrower but not clean: digits route through
  `onSymbolCommitted`, which does not learn, so the PIN itself was not written to
  the dictionary. But the field still got the full QWERTY keyboard, and clipboard
  capture was still armed.

For a keyboard whose stated position is zero telemetry, a password persisted to a
local database the user cannot see is the same class of failure as sending it
somewhere.

Fixed by `fieldKindFor(inputType)`, which recognises `TYPE_NUMBER_VARIATION_PASSWORD`
and all three text password variations including `VISIBLE_PASSWORD`. A sensitive
field forces incognito (which gates dictionary writes *and* clipboard capture),
suppresses suggestion lookups, suppresses autocorrect, and -- for a numeric PIN --
shows a digits-only pad. `SensitiveFieldTest` covers it, and the suggestion guard
was verified by removing it and watching the test fail.

**Still open:** the brief also asked for "recognised payment-app fields". There is
no reliable signal for this in `EditorInfo` -- it would require package-name
heuristics, which this project explicitly rejected when incognito was designed
("deliberately no app/package heuristics"). Flagged rather than built.

### 3.12 Incognito shares the password bug's root cause, and half of it is now closed

**Status: FIXED (unverified). Both halves are built; neither has run on a
device.** The narrative below is the original finding, kept as written; the
closing note records what the second half turned into.

Investigated because the password defect (3.8) came from trusting a flag the host
app has to set, and incognito was built on the same flag.

**What was found.** `IncognitoState` is set from exactly one thing:
`IME_FLAG_NO_PERSONALIZED_LEARNING` on `EditorInfo.imeOptions`. That is the same
single point of trust that let passwords through. A host that does not set the
flag means incognito silently does not engage, with no indication to the user --
and the Lock indicator in the suggestion strip reads from the same flow, so the
absence is invisible rather than merely ineffective.

**The half that is closed.** `updateIncognito` now reads
`noLearning || fieldKindFor(inputType).isSensitive`, so password and PIN fields
force incognito on regardless of what the host declared. That covers the case
where the gap actually costs the user something concrete -- a credential in the
dictionary -- and it uses `inputType`, which is a property of the field rather
than a courtesy from the app.

**The half that is not, and why it cannot be closed the same way.** For a field
that is private but not a password -- a private-browsing address bar, a
health-app note, a message in a chat the user considers sensitive -- there is
**no signal in `EditorInfo` at all**. `fieldKindFor` cannot help: nothing
distinguishes those fields from ordinary text. The only mechanisms that exist
are the flag the host may not set, or package-name heuristics, which this
project explicitly rejected when incognito was designed.

**Sizing the remaining fix.** The honest option is a manual incognito toggle the
user can turn on themselves, sitting beside the existing automatic behaviour:
one preference, one control in the quick-access row, and the existing
`IncognitoState` gate already does the work once it is set. Roughly a day
including the indicator states, and it needs a decision first -- an always-visible
toggle on the keyboard costs a slot in a row that is already contested.

**What it became.** Built as described, with the slot taken from the
quick-access row (now seven icons, which is the arithmetic ceiling: at the 48dp
touch minimum, seven fill 336dp of a 360dp phone and an eighth would have to
overlap). Three parts of it were decisions rather than mechanics, and are
recorded because they are what a later change could quietly undo:

- **It resolves to `FieldKind.PRIVATE`, not to a second boolean.** `isSensitive`
  is true for it, so it travels through the write gate (incognito) *and* the read
  gate (`updateSuggestions`, `getAutoCorrectionFor`) without either gate growing
  a branch. `effectiveFieldKind` only ever upgrades `NORMAL`, so a PIN field
  keeps its digit grid and a password field is already at least this strict.
- **It suppresses suggestions and autocorrect, not only learning.** This is what
  makes it more than "incognito with a button", and it is the choice most open to
  argument, because it costs the user the suggestion strip for as long as it is
  on. The reasoning: the strip is itself a disclosure surface -- it draws
  completions of the word being typed above the keyboard, where anyone looking at
  the screen can read them -- so a switch that left it running would be armed and
  still leaking. **Say so if that is the wrong trade.** The change is
  `FieldKind.PRIVATE` dropping out of `isSensitive`, and the seven assertions
  `PrivateModeTest` loses in that case are the exact list of what it would give
  back.
- **It is persistent and survives the end of an input session.** The automatic
  signals are session-scoped and must be; a manual switch that cleared itself
  when the keyboard was dismissed would fail *open*, which is the direction that
  leaks. It stays on until turned off, and the Lock indicator stays lit
  throughout, naming which of the two is holding it so the user knows whether to
  go looking for a switch.

The three IME lifecycle points that used to write `IncognitoState` directly
(`onFinishInputView`, `onFinishInput`, `onWindowHidden`) now call
`TypingViewModel.onInputFinished()`, which drops the field-derived inputs and
keeps the user's. The combination itself lives in exactly one function,
`publishPrivacy` -- do not add a second caller that recomputes it.

### 3.9 Numeric keypad, general case

**Status: DEFERRED to v0.1.5+ by decision.** The PIN half landed (3.8). The
general `TYPE_CLASS_NUMBER` layout, locale decimal handling and the four
decisions in `docs/planning/numeric-keypad-plan.md` section 5 are explicitly not
being worked.

### 3.10 SMS OTP auto-fill

**Status: FUTURE, not scheduled. Logged so the permission trap is recorded.**

Reading an incoming one-time code and offering it for a verification or payment
field.

**Whoever picks this up: use the SMS Retriever API, not `READ_SMS`.** SMS
Retriever needs no SMS permission at all -- the message is delivered to the app
only when it carries a hash the app itself published, so the user is never
prompted and the app never sees any other message. `READ_SMS` is a runtime
permission granting access to the entire inbox, would be a Play Store policy
problem, and is flatly incompatible with this project's privacy position.

Note that SMS Retriever normally binds to the *app*, not the IME, so the shape
of this on a keyboard needs designing before it is estimated.

### 3.11 One-handed mode and glide typing

**Status: DEFERRED, unchanged.** Both remain out of scope. Recorded here only so
that "was this ever considered" has an answer.

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

### 3.13 Media transport controls (B8)

**Status: FIXED, device-verified 2026-08-06 against a real player.**

Play/pause, skip forward and skip back, as their own row above the quick-access
icons, revealed by the same chevron.

**B8 is resolved, not half-resolved.** Transport-only is the decision, taken
explicitly: no track title, no artist, no artwork, and no
`NotificationListenerService` at any point. That permission grants the app the
text of every notification on the device, which is refused on its merits rather
than deferred pending a better moment. Metadata is not a backlog item waiting
for a yes; it is out of scope for as long as the only route to it is that
permission.

**Why it needs no permission, and what that costs.** The obvious API is
`MediaSessionManager.getActiveSessions()`, which returns a `MediaController` per
session and with it `PlaybackState` and full metadata. It is gated behind
`BIND_NOTIFICATION_LISTENER_SERVICE`, which grants the app the text of every
notification on the device. For three buttons on a keyboard that is a
disproportionate ask; for this keyboard specifically it contradicts the pitch.
So `MediaTransport` dispatches media key events through `AudioManager`, the same
routing a hardware media button uses, which needs nothing. Two consequences,
both stated rather than papered over:

- **No metadata at all.** That is the scope, not a stub.
- **The play/pause glyph is a proxy.** `AudioManager.isMusicActive` answers "is
  audio coming out right now", not "is the session playing". A paused podcast
  reads false, and a video in a browser tab reads true. It picks an icon;
  nothing depends on it. The *buttons* are exact either way, because the key
  event reaches the active session even when it is paused -- which is what makes
  resume work while the glyph says play.

**Two things that look like polish and are not.** The row is drawn whenever the
toolbar is open rather than only when something is playing: gating it on
`isMusicActive` reads tidier and breaks the most ordinary case there is, because
pausing turns off the very signal the row was gated on and the control that
would resume it disappears. And both halves of each press are dispatched -- a
lone `ACTION_DOWN` is a held button to the receiver, and a hold on NEXT is
seek-forward in most players.

`MediaTransportTest` pins the key-code table. `KEYCODE_MEDIA_NEXT` is 87 and
`KEYCODE_MEDIA_PREVIOUS` is 88; transposed, both buttons work perfectly and
point the wrong way, which is invisible in a screenshot and obvious within a
second of holding the phone.

### 3.14 Palette rework (B1)

**Status: FIXED (unverified as a design; rendering device-verified 2026-08-06).**

"sticky6": `Indigo #394053`, `Iris #4E4A59`, `Taupe #6E6362`, `Sage #839073`,
`Fern #7CAE7A`, on a true-black base. Roles were assigned from measured contrast
rather than by eye -- against black: Fern 8.19, Sage 6.20, Taupe 3.62, Iris
2.45, Indigo 2.03.

Two consequences are load-bearing and should not be undone as tidying:

- **`onPrimary` is near-black, not cream.** Cream on Fern measures 2.15 and is
  unreadable; black on Fern is 8.19. A future accent has to be re-measured
  against its own foreground rather than inheriting this pairing.
- **Legible text comes from outside the palette.** All five swatches are
  mid-tones, so cream (dark) and Indigo (light) supply foreground.
  `FernDeep #3F6B3D` exists only because the light theme puts white on the
  accent, where Fern alone measures 2.15 against 6.21.

A Kotlin colour token is not the whole story: `assets/themes/*.json` carry their
own hex values and override it, and `values-night/themes.xml` is the pre-Compose
window which cannot read a token at all. Changing a palette value means grepping
all three.

### 3.15 Auto Backup was enabled with no scoping

**Status: FIXED (unverified). Cannot be device-verified; see below.**

`allowBackup="true"` was declared with neither `dataExtractionRules` nor
`fullBackupContent`, which is the permissive default: everything in
`shared_prefs`, `databases` and `filesDir` eligible for upload. That included
`keyboard_database` -- the learned personal dictionary and the clipboard
history, which is precisely the data this project promises never leaves a
device.

Both rule files now ship, because the platform reads one or the other by version
and never both. They are **include-only**, and that shape is the mechanism:
once a scope contains any `include`, everything else is already excluded, so
`keyboard_database` appearing in neither list is what keeps it off Google's
servers. An `exclude` for a path that was never included is a no-op and `lint`
fails the build on it, precisely because writing one usually means the author
believed the opposite. Both files carry a comment saying so.

`disableIfNoEncryptionCapabilities="true"` refuses cloud backup on a device with
no lock screen, where the payload arrives at Google decrypted. There is no
pre-Android-12 equivalent, so on those versions the omissions are the only
defence -- which makes the list matter more there, not less.

Sticker bytes and the sticker index are omitted from cloud backup together and
travel on device-to-device transfer only: the 25MB cloud quota does not degrade
gracefully (past it the OS silently stops backing the app up while continuing to
serve a stale snapshot), and an index restored without its bytes presents as a
library of broken thumbnails.

**Why this cannot be device-verified.** A real backup needs the device idle, on
power, on Wi-Fi, and 24 hours since the last one. What *was* checked on
2026-08-06: both files ship in the APK (`res/xml/backup_rules.xml` 684 bytes,
`res/xml/data_extraction_rules.xml` 1808 bytes), the merged manifest carries all
three attributes, and every path named in them was checked by name against the
code that creates it -- because a mistyped include backs up nothing and reports
no error.

Item 4 of `docs/auto_backup.md` -- the zero-knowledge AES-256-GCM manual export
tool over SAF -- is **not built**. If it lands, its unzip path needs canonical
path validation or it is a Zip-Slip hole.

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

**Status: CLOSED 2026-08-05 as unreproducible. Monitoring for recurrence. Not
reopened by anything since, including the 2026-08-06 device pass.**

This was carried over as an open item but could not be matched to anything in
the codebase with confidence, and guessing which bug it refers to would be worse
than saying so. The two scrollable tab surfaces were:

- `EmojiPickerView.kt:113` -- a `LazyRow` of category tabs, each a
  `Box.clickable`. Compose's scroll consumes the gesture before a click fires,
  so a tab should not activate on scroll. A related issue *was* fixed in v0.1.2:
  the back chevron sits where a horizontal scroll gesture naturally starts, which
  is why the `ABC` chip was added at the opposite edge.
- `StickerIMEView.kt` -- a Material3 `PrimaryScrollableTabRow`. **This file no
  longer exists.** It was unreachable when this entry was written, and was
  deleted outright when `AppMode.STICKERS` was retired (B13). If the symptom was
  ever here, it is gone with the file.

If the symptom is "scrolling the emoji category strip selects a tab", say so and
it becomes a five-minute fix. If it is something else, the description above is
the wrong tree.

**Closed 2026-08-05 as unreproducible.** Asked three times; no concrete
description ever came back, and the third reply returned the placeholder text
unfilled. Rather than carry an open item that depends on an answer which is not
arriving, this is now: **unreproducible -- no confirmed description after
repeated requests, monitoring for recurrence.**

If it resurfaces, the emoji category strip is now the only candidate surface
left, and a single sentence describing what was seen reopens it immediately.

### 4.5 Everything in the v0.1.4 Unreleased section

Every entry marked FIXED (unverified) in this document and in `CHANGELOG.md` has
unit tests and a green `./gradlew build`. The checklist for running them is
[`docs/testing/v0.1.4-alpha-device-checklist.md`](testing/v0.1.4-alpha-device-checklist.md).

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

### 4.7 The deep pass requested on 2026-08-06

**Status: RUN. See section 4A for what it found.** The notes below are from the
first attempt, when no device was attached; the phone was connected later the
same day and the pass ran in full.

The full pass asked for -- every button, every symbol page, every mode, repeated
autocorrect stress, every digit's hold strip, and re-verification of everything
already confirmed on 2026-08-03 now that two more changes have landed in shared
IME plumbing -- was not started. `adb devices` returned empty, before and after
an `adb kill-server; adb start-server`. Nothing in this pass is claimed as
device-verified, including the two features built the same day.

What was completed without a device, and is not a substitute for it:

- The backup rules, which is the one item on that list that never needed one.
  Both files ship in the APK (`res/xml/backup_rules.xml` 684 bytes,
  `res/xml/data_extraction_rules.xml` 1808 bytes), the merged manifest carries
  all three attributes, and every path named in them was checked against the
  code that creates it: `keyboard_preferences.xml` and `app_preferences.xml`
  against their `getSharedPreferences` calls, `themes/` `layouts/`
  `theme_backgrounds/` `stickers/` `stickers_thumbnails/` against the `File(
  context.filesDir, ...)` sites, and `stickykeys_db` against the sticker
  `databaseBuilder`. `keyboard_database` -- the learned dictionary and the
  clipboard history -- appears in neither scope, which is the mechanism. A
  mistyped include is the failure mode here, because it backs up nothing and
  reports no error, so the check is name-by-name rather than a read-through.
- A field-type harness for the PIN/password verification, which is the item
  that has never been device-verified and cannot be tested against the phone's
  real lock credential. Six inputs, no form action, no script: `text` as the
  control, `password`, `password + inputmode=numeric` for the PIN path, and
  `tel`/`number`/`email` as the false-positive checks that matter most --
  `fieldKindFor` treating an amount field as a passcode would silently kill
  suggestions on ordinary typing. Left in the session scratchpad as
  `field-types.html`; `adb push` it and open it in a browser.

### 4.6 Symbols-page digits inherited the fraction strip

**Status: OPEN, found during the 2026-08-03 pre-run. Not a regression, an
unplanned consequence.**

`DIGIT_ALTERNATES` is keyed on a key's output, and `"1"` is the output of a key
on both the number row and row 1 of symbols page 1 -- so the symbols page now
shows hold-affordance dots on its digits and offers the same fraction strip.
Three defensible resolutions are set out as finding F1 in the checklist. Nothing
was changed, because the choice is a product one.

**Closed by the 2026-08-06 device pass.** Verified fixed on the phone: symbols
row 1 draws its digits with no corner hints and no hold-affordance dot, and a
hold on each commits the digit itself rather than a fraction. The structural fix
(alternates hanging off `KeyDefinition` rather than a table keyed on output) is
what did it.

---

## 4A. Found by the 2026-08-06 device pass

The pass ran on a Redmi Note 11 (2201117TI, Android 15, 1080x2400 @420dpi)
against a debug build, with `run-as` reads of the dictionary before and after
every step. Everything below was observed on the phone, not read out of the
source. Two were fixed in the same session; the rest are open and none was
changed silently.

### 4A.1 A field that rejects a character still learns it

**Status: FIXED, device-verified 2026-08-06.**

Typing `qwxzj` into a `TYPE_CLASS_PHONE` field leaves the field **empty** -- the
editor's input filter drops every letter -- and puts `qwxzj` in the personal
dictionary at frequency 1. The keyboard learned a string that never appeared in
any text field on the device.

`TypingViewModel.currentWord` is appended to on each key press because reading
the editor per keystroke would be a blocking IPC. That mirror is already known
to drift, and destructive edits were taught not to trust it -- the suggestion
strip sizes its replacement with `currentWordSpan(controller)`, which re-reads
the field. **The learning path never got the same treatment.** It still writes
the mirror.

Filtered fields are common: phone, number, and anything with a custom
`InputFilter`. The damage compounds, because after two sightings
`PredictionEngine` treats a word as deliberate and stops correcting it.

### 4A.2 Enter learns the word it declined to check

**Status: FIXED, device-verified 2026-08-06.**

Enter deliberately does not autocorrect -- that is settled and defensible, since
correcting before it needs a blocking lookup and correcting after it is too late
in a send-on-enter field. But Enter *does* call `onWordFinished()`, which
**learns**. So the word is written to disk having never been checked.

Measured, in one session, on one device:

1. `teh` + space -> `The`. Autocorrect works.
2. `teh` + Enter -> `Teh`, and `teh` is learned. Frequency 1.
3. `teh` + Enter again. Frequency 2.
4. `teh` + space -> **`Teh`**. No longer corrected, permanently, on disk.
5. The suggestion strip now offers **`teh`** as a word.

Every other path learns only after autocorrect has had its say -- either the
correction is learned, or the typed word is learned because the engine judged it
acceptable. Enter is the one caller that learns without a verdict, and in a
send-on-enter chat app that is the *normal* typing path. Two sends of the same
typo make it un-correctable, and nothing looks broken while it happens.

This is the same failure class as the space-bar scrub poisoning that
`onWordAbandoned()` was added to fix. Enter is a genuine completion, so it is
not the same fix -- **this needs a decision**, and the options are at least: do
not learn on Enter at all; learn only words the dictionary already contains; or
learn on Enter but run the lookup first and skip the write when a correction was
available.

### 4A.3 Switching IME mode resizes the window by 48dp

**Status: FIXED, device-verified 2026-08-06.**

Measured from `dumpsys window` -- the authoritative frame, sampled in the same
command as each screenshot:

| Mode | IME window height |
|---|---|
| Typing | 956px (364dp) |
| Emoji picker | 830px (316dp) |
| Clipboard | 830px (316dp) |
| Text edit | 830px (316dp) |
| PIN pad | 956px (364dp) |

The 126px gap is exactly `ime_number_row_height` (48dp). `MainIMEView` sets

```kotlin
showNumberRow = numberRowShown && currentAppMode == AppMode.TYPING,
```

whose comment reads: *"Adding it everywhere would make the emoji picker taller
than the keyboard it replaces, and every mode switch would resize the window."*
The measurement says the gate produces that resize rather than preventing it:
with the number row on -- the default -- opening the emoji picker shrinks the
IME window by 48dp and closing it grows it back, which shoves the host app's
content down and up. With the number row off, all modes agree and there is no
jump.

`dimens.xml` states the intent plainly: *"Every mode uses this one value so
switching between typing, stickers and clipboard never resizes the window."*
Which of the two behaviours is wanted is a product call, so nothing was changed.

### 4A.4 Every copy is stored twice

**Status: FIXED, device-verified 2026-08-06.**

One Select-all-then-Copy from the text-edit panel writes two identical rows to
`clipboard_entries`, 253ms apart the first time and 21ms apart the second. There
is also no dedupe of a repeated copy of the same text, so the history fills with
consecutive duplicates.

### 4A.5 Auto-capitalize fires on password fields

**Status: FIXED, device-verified 2026-08-06.**

A `TYPE_TEXT_VARIATION_VISIBLE_PASSWORD` field seeded from `initialCapsMode`
arms shift, so `correcthorse` was entered as `Correcthorse`. Invisible in a
masked field, which is worse rather than better: the user cannot see that the
first character is not what they pressed. `FieldKind.isSensitive` is already
known at `onInputStarted`; suppressing auto-capitalize there is the obvious fix.

### 4A.6 Focusing a new field does not leave CLIPBOARD or EMOJI mode

**Status: FIXED, device-verified 2026-08-06.**

`AppMode` lives in `MainIMEView` and is not reset by a new input session, so
tapping a different text field while the clipboard panel is open leaves the
clipboard panel open over the new field. Every latch that *is* session-scoped
(shift, caps lock, symbols page) resets; this one does not.

### 4A.8 How the six were fixed, and the one thing that had to change underneath

All six were fixed and re-verified on the same phone on 2026-08-06.

- **4A.1 and 4A.2 share one root fix.** Learning no longer takes its word from
  the keyboard's running buffer at any call site. `currentWordText(controller)`
  reads the editor at the word boundary and every learn path is handed that,
  so a field which rejected the keystrokes has nothing to teach.
  `onWordFinished` takes the word as a parameter rather than reading
  `currentWord`, which is what makes the old mistake un-writable rather than
  merely absent. One editor read per word boundary, never per keystroke.
- **Enter is split on what Enter *is*.** `enterInsertsNewline(inputType,
  imeOptions)` is the single classifier, extracted from `sendEnter` which now
  calls it -- previously the same `EditorInfo` arithmetic decided what Enter did
  and would have separately decided what Enter learned. Submit-style Enter calls
  `onWordAbandoned()`; newline Enter runs the same correct-then-learn path as the
  space bar. `EnterLearningTest` pins the classifier including the
  `IME_MULTI_LINE` flag and the non-text-class case.
- **One thing had to change underneath, and it is worth knowing.**
  `sendRawEnter` never called `predictCaret`, so `onUpdateSelection` saw the
  newline as somebody else's edit, re-read the field and bumped the generation
  token -- which invalidated the correction dispatched a moment earlier. The
  first device run of the multi-line path learned `brwon` instead of correcting
  it to `brown` for exactly this reason. `sendEnter` now predicts its caret on
  the newline branch, which also removes a blocking IPC that was firing on every
  Enter.
- **4A.5** gates auto-capitalize on the new `FieldKind.isCredential`, which is
  PIN and PASSWORD only. Deliberately *not* `isSensitive`: that also covers the
  manual privacy switch, and a privacy control must not silently stop
  capitalizing ordinary prose. Verified both ways on device.
- **4A.3** drops the `currentAppMode == AppMode.TYPING` term. All modes now
  measure 956px.
- **4A.4** claims the clip text synchronously on the listener thread before
  dispatching the write, because the duplicate callback arrives faster than a
  database round-trip could return. Also suppresses an immediately repeated
  identical copy; a different copy in between is still stored.
- **4A.6** resets `appModeState` on `inputSession`.

Re-verified unchanged after all six landed: the currency default (5/5 holds
commit `$`), the toolbar staying open on the privacy toggle, password and PIN
suppression, the scrub poisoning fix, rapid-typing autocorrect at 40ms/key, and
mid-word backspace-and-retype.

### 4A.7 Fixed during the pass

- **The alternates strip committed a different character than it highlighted.**
  Holding the currency key drew `$` selected (its `defaultIndex`) and committed
  `€` on release. The gesture loop called `moveTo` on every pointer event and a
  release *is* a pointer event carrying the finger's position, so `defaultIndex`
  was overwritten before it could ever be committed. Invisible on all ten digits,
  whose default is index 0 and whose finger sits over cell 0 -- the right answer
  from the wrong mechanism. The TalkBack path reads `options[defaultIndex]`
  directly, so touch and screen reader disagreed on the same key. Fixed with a
  6dp movement slop before tracking begins; `AlternatesDefaultTest` pins the
  state machine and the device confirms `$` on 3/3 holds with all five cells
  still individually reachable.
- **The quick-access toolbar closed when the privacy toggle was tapped.**
  `LaunchedEffect(inputSession, fieldKind)` cleared the panel state, and the
  manual switch changes `fieldKind` in place, so the row shut under the finger
  that had just opened it. Split so the resets key on `inputSession` alone.

---

## 4B. v0.1.5 backlog, as stated 2026-08-08

Six items. Recorded before any of them was started so the list is not
reconstructed later from whatever happened to get built.

### 4B.1 Glide typing -- the headline feature for v0.1.5

**Status: FIXED (unverified). Built end to end; never run on a device.**

Decoder in `GlideStroke.kt` plus `PredictionEngine.decodeGlide`; gesture capture
in `GlideTracker` and the glide branch of `keyGestures`. On by default with a
settings switch. Gated on `isSensitive`, so passwords and PINs never decode.

Swipe across the letters to type a word. The one large piece of work in this
release; everything else here is small by comparison.

### 4B.2 The emoji picker has no backspace

**Status: FIXED (unverified).** Bottom right, repeating on hold. There is no way to delete from inside the picker, so a
mistyped emoji means switching back to the keyboard and returning. Every other
mode reaches backspace without leaving.

### 4B.3 Typing does not feel smooth

**Status: CLOSED, does not reproduce. Confirmed by Joel on device 2026-08-09.**

Raised against an earlier build and no longer present. Deliberately closed on a
report rather than on a fix, because nothing was changed to address it -- so if
it returns it is a new observation and should be filed as one rather than
reopened against work that never happened.

Nothing here was measured, and that is the honest record: the item asked for
measurement before optimizing, and the measurement that resolved it was somebody
using the keyboard and finding it fine. The candidates listed originally (no
baseline profile, debug-build overhead, the unguarded suggestion strip) remain
true and remain unaddressed. They are not defects on their own.

### 4B.4 Space on a symbols page should return to letters

**Status: FIXED (unverified).** Typing a symbol then space leaves the board on the symbols
page, so the next word is typed on the wrong plane. Enter already resets the
page; space does not.

### 4B.5 Caps lock double-tap

**Status: FIXED (unverified).** `nextShiftMode` is pure and tested;
`ShiftCycleTest` covers both sides of the window and the always-escapable latch. The current shift key is a three-way cycle -- lower, upper,
caps lock -- so reaching caps lock is one tap away from lower case and is easy
to enter by accident. Wanted instead:

- One tap arms shift for the next letter, as now. The number row shows its
  shifted symbols, as now.
- A second tap **within a short window** (about a second) latches caps lock, and
  the shift key shows a bar under the arrow to say so.
- A second tap **after** that window returns to lower case rather than latching.

So the same key does two different things depending on timing, which is the
part that needs a pure, tested decision function rather than a state machine
spread across the view.

### 4B.7 Symbols on keys can be turned off

**Status: FIXED (unverified).** Added this release, not on the original list.
Settings > Keyboard, under the number row, default on. Presentation only -- the
hint still reaches `longPressFor`, so hiding it cannot remove long-press access
to the punctuation set. `KeyHintToggleTest` pins that.

### 4B.6 Only the letters plane can be remapped

**Status: OPEN.** The layout editor remaps the main letters keyboard. The
symbols pages and the number row are not editable, even though both became part
of `KeyboardLayoutConfig` in v0.1.4 specifically so they could be. The data
model is ready; the editor has not caught up with it.

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
| B2 | Symbols page 2 | A reference image, or explicit permission to design it |
| B9 | Blank keyboard | The three questions in 1.1. No fix attempted; the recovery path in 1.1 is the mitigation |
| B15 | Fraction sets for `0` and `3`-`9` | Shipped as a proposal and now device-verified as rendering and committing. Confirm or amend the one table |

### Resolved 2026-08-05 / 2026-08-06

| # | Item | Decision and where it landed |
|---|---|---|
| B1 | Palette rework | "sticky6" applied. See 3.14 |
| B4 | Currency long-press | Standalone `$` key with `EUR YEN $ CENT RUPEE`, opening on `$`. Resolved structurally by moving alternates onto `KeyDefinition` -- see 3.11. Device-verified |
| B5 | Comma-key alternates | Confirmed by Joel and device-verified in an earlier session: no mic icon on the key, alternates stay `. ? !`. Settled |
| B6 | uncoalesced site link | Shipped. `REPO_URL` points at `github.com/uncoalesced/fluxboard` (source code) and `SITE_URL` at `github.com/uncoalesced` (the author), until the site is live |
| B8 | Media transport | Transport-only, decided explicitly: play/pause and skip, no metadata, and **no `NotificationListenerService`** -- the permission grants the text of every notification on the device and is refused on those grounds, not deferred. See 3.13 |
| B7 | Keyboard resize | Three sliders shipped: overall height 70-150%, key size within its cell 70-130%, and space below the bottom row 0-48dp. They are independent on purpose |
| B12 | Double-space-to-period | Default **on**. `DoubleSpaceTest` pins the cases where it must not fire |
| B13 | Retire `AppMode.STICKERS` | Retired. Both files deleted; `StickerThumbnail` rescued out of them because the emoji picker's sticker tab draws through it |

### Resolved 2026-08-02

| # | Item | Decision |
|---|---|---|
| B3 | Number-row hint slot | Option B. Implemented, see 3.2 |
| B10 | Autocorrect on Enter | No correction on Enter. Punctuation only |
| B11 | Emoji key on symbols page | Keep it, deviating from the reference. See the flag in 3.1 |
| B14 | Retroactive `v0.1.2-ALPHA` tag | Not needed; the gap is documented in `CHANGELOG.md` instead |
