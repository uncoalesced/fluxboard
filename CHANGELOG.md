<!-- Engineered by uncoalesced -->

# Changelog

All notable changes to FluxBoard. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning is
alpha-track and not yet semantic.

## A note on how this file was rebuilt

Entries below are derived from `git log`, the two release tags, and the source
tree as it actually stands, not from a summary of what any phase was supposed
to produce.

The previous version of this file was a restatement of the 37-phase plan
presented as release history, and it disagreed with the code in at least two
places that matter: it listed "On-Device Segmentation Integration using ML Kit
Subject Segmentation" as shipped when ML Kit was deliberately removed from the
project (see v0.1.1 below, and `AGENTS.md` on Play Services), and it listed
ephemeral link sharing as delivered when `RelayClient` still points at
`ws://10.0.2.2:8080` and `relay/server.js` is deployed nowhere. Both are now
recorded where they belong: the removal as a removal, the relay as an open item
in [`docs/roadmap.md`](docs/roadmap.md).

**Version boundaries are only partly reliable, and this is stated rather than
smoothed over.** Two tags exist -- `v0.1.0-alpha` (2026-07-24) and
`v0.1.1-ALPHA` (2026-07-30). **`v0.1.2-ALPHA` was never tagged**, and a
retroactive tag was considered and declined. Its boundary below is inferred from
`versionName` in `app/build.gradle.kts` and from the commit that names it in its
own subject line; read the v0.1.2 section as "everything on `development` after
the v0.1.1 tag".

---

## [Unreleased] - v0.1.4-ALPHA

Work in progress. Entries are added as they land, not when they are planned.
Planning for this release is in
[`docs/planning/v0.1.4-alpha-plan.md`](docs/planning/v0.1.4-alpha-plan.md).

**Device status:** everything below marked with a dagger was exercised on a
Redmi Note 11 (Android 15) on 2026-08-06 -- real key taps and real gestures, not
`adb shell input text`, which bypasses the IME entirely and measures nothing.
The rest is unit-tested only. `docs/roadmap.md` section 4A carries the pass
itself, including what it found.

### Added

- **"Reset keyboard appearance" in Keyboard settings.** Deletes every custom
  theme, keyboard background and custom layout and returns to the shipped dark
  theme and QWERTY. This is the recovery path for the blank-keyboard fault: the
  active theme and layout ids live in SharedPreferences with the files in
  `filesDir`, so a keyboard that renders itself unreadable survives force-stop,
  cache clearing and reboot, and every control that could fix it is on the
  keyboard that cannot be read. Deliberately independent of diagnosing the cause.
- Symbol pages are now part of `KeyboardLayoutConfig` (`symbolRows`,
  `symbolShiftedRows`) rather than hardcoded string tables converted at render
  time. This is what unblocks remapping, per-key weights, corner hints and
  long-press alternates on the symbol pages. Layout files written before this
  change load with the shipped pages rather than an empty symbols plane.
- `KeyboardController.textBeforeCursor(maxChars)`, so the keyboard can re-read
  what the editor actually contains instead of only trusting its own running copy.
- **Symbols on the number row.** Each digit carries its shifted symbol as a
  corner hint, and the row shows those symbols in place of the digits while
  shift is armed. Pressing one consumes a one-shot shift through the same branch
  as any other key.
- **Long-press a digit for its superscript and vulgar fractions.** The strip
  opens on the superscript, then the fractions with that digit as numerator
  ascending by value. `1` and `5` follow the confirmed spec; the rest are a
  proposal living in one table.
- **Password and PIN fields are recognised at last.** `fieldKindFor(inputType)`
  detects `TYPE_NUMBER_VARIATION_PASSWORD` and all three text password
  variations. A sensitive field forces incognito, suppresses suggestion lookups
  and autocorrect, and shows a digits-only pad for a numeric PIN. See Fixed
  below for what this was actually protecting against.
- **A currency key on the symbols page.** It types `$` and holding it offers
  `EUR YEN $ CENT RUPEE`, opening on `$` so a hold-and-release gives back the
  character already printed on the key. The pound sign is dropped rather than
  moved into the strip.
- Long-press strips size their own cells, so a strip can be any length instead of
  being kept short by a test assertion. (dagger)
- **Media transport controls in the quick-access row (B8, transport half).**
  Play/pause and skip for whatever the platform considers the active media
  session, as their own row above the action icons. No track title, artist or
  artwork -- that half of B8 is still blocked. The point of the design is what it
  does *not* ask for: the obvious API needs
  `BIND_NOTIFICATION_LISTENER_SERVICE`, which grants the text of every
  notification on the device, so this dispatches media key events through
  `AudioManager` instead and needs no permission at all. The play/pause glyph
  comes from `AudioManager.isMusicActive`, which answers "is audio coming out
  right now" rather than reading the session's state; it picks an icon and
  nothing depends on it. (dagger)
- **Private mode: a manual privacy switch.** The half of incognito no
  `EditorInfo` signal can reach -- an ordinary message box holding a recovery
  phrase or a diagnosis looks exactly like every other message box, so there is
  no honest automatic answer and this is deliberately a labelled switch rather
  than a heuristic on package names. In the keyboard's quick-access row and
  mirrored in Keyboard settings. While on, nothing is learned, suggested,
  corrected or captured to clipboard history, and the padlock in the suggestion
  strip stays lit saying so. Persistent by design: a privacy switch that clears
  itself when the keyboard is dismissed fails open. (dagger)
- **Scoped Auto Backup.** `allowBackup` was declared with no rules at all, which
  is the permissive default -- everything in `shared_prefs`, `databases` and
  `filesDir` eligible for upload, including `keyboard_database`, which holds the
  learned dictionary and the clipboard history. Both rule formats now ship
  (`data_extraction_rules.xml` for Android 12+, `backup_rules.xml` below it) and
  are include-only: `keyboard_database` appears in neither scope, and that
  absence is the mechanism. Cloud backup additionally refuses to run on a device
  with no lock screen, where the payload would reach Google decrypted. Sticker
  bytes and the sticker index travel together on device-to-device transfer only,
  because the 25MB cloud quota does not degrade gracefully and an index restored
  without its bytes is a library of broken thumbnails.
- A debug-only field-type harness
  (`app/src/debug/.../FieldTypeHarnessActivity`) covering every `inputType` the
  keyboard classifies. It exists because the password gate had gone two releases
  unverified: proving it needs a field the keyboard treats as a secret, and the
  only ones on a real phone are the lock credential and a Wi-Fi dialog. Lives in
  `src/debug` rather than behind a flag, for the same reason `UsageRecorder`
  does -- a runtime boolean cannot make a class stop existing.
- `scripts/device/adb-safe.ps1`: `force-stop` and `pm clear` guards that refuse
  to act on whichever package is the bound IME, plus `Assert-ImeBound` for use
  before a measurement. Both traps are documented in `CLAUDE.md` and both were
  still tripped during this release's device work.

### Fixed
- **Passwords could be written to the personal dictionary.** Nothing in the
  codebase read the password input-type variations; incognito was driven only by
  `IME_FLAG_NO_PERSONALIZED_LEARNING`, a flag the host app must set and which
  Android's own `TextView` does not set for password fields. An alphanumeric
  password therefore went down the ordinary letter path: every keystroke ran a
  dictionary lookup whose results were drawn in the suggestion strip, and the
  first space afterwards persisted the password to the on-disk dictionary. For a
  keyboard whose position is zero telemetry, that is the same class of failure as
  sending it somewhere.

- **The suggestion strip deleted the wrong span of text.** It replaced
  `currentWord.length` characters, where `currentWord` is a local mirror that is
  correct only while this keyboard is the sole editor. After a caret tap, a
  paste, a selection replaced by the host, or a space-bar scrub, it described
  text that was no longer there and the replacement ate the wrong characters.
  The span is now read from the editor at the moment of the edit.
- **The space-bar scrub was poisoning the personal dictionary.** It called
  `onWordFinished()` on every step of the drag, and that function *learns* what
  it clears -- so scrubbing out of the middle of a word wrote the fragment under
  the caret into the dictionary. After two sightings `PredictionEngine` treats a
  word as deliberate and stops correcting it, so the damage accumulated silently
  and outlived the gesture. Scrubbing now abandons the word without learning it.
- **Autocorrect fired on the space bar and nowhere else.** Ending a word with
  `.` `,` `!` `?` `;` `:` never attempted a correction, so it worked on some
  words and not others with no pattern the user could see. Punctuation now runs
  the same correction path under the same generation-token guard. Enter
  deliberately does not; see the roadmap for why.
- Committing punctuation no longer learns the word first. It used to call
  `onWordFinished()` before `onSymbolCommitted()`, so a misspelling ending in a
  full stop went into the personal dictionary before anything decided whether it
  needed correcting -- which then vetoed its own future correction.
- The word tracker and auto-capitalize are re-derived from the editor after any
  change this keyboard did not make. Detected by comparing the caret against a
  prediction recorded at each of this keyboard's own edits, so the field is never
  re-read on the keystroke path.
- `LayoutManager` silently ignored an `active_layout_id` that matched nothing,
  leaving the stored pointer broken so every launch rediscovered it. It now falls
  back and repairs the preference -- the same fix `ThemeManager` received in
  v0.1.2, applied to the one place it had not been.
- Custom layout files that cannot be parsed, or that fail validation and would
  strand the user on a page with no way back, are quarantined on load instead of
  being re-read and re-skipped forever.
- `/` is reachable from the symbols page. It previously existed on neither symbol
  page and could only be produced by holding the `m` key.

Found by the 2026-08-06 device pass and fixed the same day, all re-verified on
the phone:

- **A field that rejected a character still learned it.** (dagger) Typing into a
  `TYPE_CLASS_PHONE` field left it empty -- the editor's own input filter drops
  letters -- and put the letters in the personal dictionary anyway. Learning was
  reading the keyboard's running buffer rather than the text that exists. The
  buffer had already been taken away from destructive edits; this was the other
  half of the same gap. Every learn path now derives its word from the editor,
  and `onWordFinished` takes it as a parameter so the old mistake cannot be
  written again.
- **Enter learned the word it had declined to check.** (dagger) Enter
  deliberately does not autocorrect, but it still learned -- so the one path
  that skips the check was writing to disk. Measured: two sends of `teh` put it
  in the dictionary at frequency 2, after which autocorrect stopped fixing it
  permanently and the suggestion strip began offering it. In a send-on-enter
  chat app that is the ordinary typing path. Enter now splits on what it *is*:
  a submit abandons the word without learning; a newline finishes it. Neither
  corrects. `enterInsertsNewline` is the single classifier, and `sendEnter`
  calls it, so what Enter does and what Enter learns cannot drift apart.
- **Switching IME mode resized the window by 48dp.** (dagger) With the number row
  on -- the default -- typing measured 956px and the emoji picker, clipboard and
  text-edit panels 830px, so opening a panel shrank the IME window and closing it
  grew it back, shoving the host app's content up and down. The gate causing it
  carried a comment claiming it prevented exactly that. All modes now measure
  956px.
- **Every copy was stored twice.** (dagger) `OnPrimaryClipChangedListener` is not
  once-per-copy; redeliveries were measured 21ms and 253ms after the original.
  A repeat of the same text inside a one-second window is now ignored. The window
  is load-bearing: suppressing on text alone also swallowed a *deliberate*
  re-copy seconds later, which is worse than the duplicate it fixed.
- **Auto-capitalize fired on password fields.** (dagger) `initialCapsMode` is
  non-zero for any empty text field whatever its variation, so `correcthorse` was
  entered as `Correcthorse` -- invisible in a masked field, which makes it worse
  rather than more forgivable. Gated on a new `FieldKind.isCredential`, which is
  PIN and PASSWORD only and deliberately not the broader `isSensitive`: that
  also covers private mode, and a privacy switch must not stop capitalizing
  ordinary prose.
- **Focusing a new field left the previous panel open.** (dagger) Every other
  latch is session-scoped -- shift, caps lock, symbols page, quick-access row --
  and the app mode was the one that was not, so tapping a different text field
  while clipboard history was open left it sitting over the new field.
- **The alternates strip committed a different character than it highlighted.**
  (dagger) Holding the currency key drew `$` selected and committed `EUR`. The
  gesture loop tracked the finger on every pointer event, and a release *is* a
  pointer event carrying a position that never left the key -- so the strip's
  default was overwritten before it could be committed. Invisible on all ten
  digits, whose default is the cell under the finger anyway. The TalkBack path
  reads the default directly, so touch and screen reader disagreed on the same
  key.
- **The quick-access row closed when the privacy toggle was tapped.** (dagger)
  The toggle changes the field's classification in place, which re-ran the
  effect that clears panel state on a new field.

### Changed
- The digit hold strip no longer leads with the shifted symbol. That character is
  one shift-tap away, where the whole row becomes real shifted-symbol keys, so
  the cell was a second route to something already reachable and it pushed the
  strip's actual content along the row. The corner hint still shows it as
  information. A hold-and-release now commits the superscript.
- Long-press alternates are attached to the key rather than looked up by the
  character it types. A table keyed on output cannot tell two keys apart that
  type the same thing, which had already given the symbols-page digits the number
  row's fraction strips and would have given the new currency key digit 4's.

- Symbols page 1 now matches the supplied reference: row 2 is
  `@ # $ & _ - ( ) = %`, row 3 is `{&= " * ' : / ! ? +` plus backspace. `£`
  replaces `$` in that slot per the reference; `$` is reached through the shifted
  number row instead.
- **One deliberate deviation from the reference:** the emoji key is kept on the
  symbols action row, where the reference draws none. Removing it would mean
  returning to the letters page before emoji could be reached at all, which works
  against the direct-emoji-access goal the backlog is built around.
- The `SYMBOLS_SHIFT` key is labelled `{&=`, per the same reference.
- `LayoutValidator` validates each page independently, so a comma on the letters
  page and a comma on a symbol page are no longer a duplicate-id error, and each
  page gets its own reachability rules.
- **New palette, "sticky6".** `Indigo #394053`, `Iris #4E4A59`, `Taupe #6E6362`,
  `Sage #839073` and `Fern #7CAE7A` on a true-black base, replacing the previous
  three-colour set. Roles were assigned by measuring contrast rather than by
  eye: against black, Fern is 8.19, Sage 6.20, Taupe 3.62, Iris 2.45, Indigo
  2.03. Two consequences are load-bearing rather than cosmetic. `onPrimary` is
  near-black, because cream on Fern measures 2.15 and cannot be read while black
  on Fern is 8.19 -- any future accent has to be re-measured against its own
  foreground rather than inheriting this pairing. And because all five swatches
  are mid-tones, legible text comes from outside the palette: cream in dark mode,
  Indigo in light, with `FernDeep #3F6B3D` existing only because the light theme
  puts white on the accent. Remember that a Kotlin colour token is not the whole
  story: the JSON presets in `assets/themes/` carry their own hex values and
  override it.

---

## [v0.1.2-ALPHA] - untagged, on `development`

Three commits after the `v0.1.1-ALPHA` tag. A fix pass against findings from a
Nothing Phone 2a.

### Fixed

- **Blank-canvas render.** `ThemeManager` left `activeTheme` null forever when
  the stored `active_theme_id` resolved to nothing. Resolution now falls back and
  repairs the stored pointer, unparseable theme files are quarantined, and
  `KeyboardTheme.sanitized()` drops any override combination that would paint
  invisible keys. **Not confirmed fixed** -- the defences went in without a
  reproduction; see the roadmap.
- **Focus escape.** `sendKeyEvent(KEYCODE_DPAD_*)` replaced with a `CursorMove`
  API over the InputConnection. A DPAD event is focus navigation first, so an
  arrow the editor could not consume fell through to the host window's focus
  search and moved focus out of the text field entirely.
- **Sticker `commitContent`.** `.sticker` is an extension `MimeTypeMap` cannot
  resolve, so the stock `FileProvider` answered null for `getType()` on every
  sticker this app has ever produced. Apps that resolve the URI's own type before
  accepting it refused the content. `StickerFileProvider` now reads the type from
  the file header. **Not confirmed on WhatsApp or Discord** -- the test device has
  neither installed.
- Auto-capitalize: `onSpacePressed` cleared `atSentenceStart` unconditionally, so
  a period set the flag and the space that always follows immediately cleared it.
- Enter dispatch: `IME_ACTION_UNSPECIFIED` (0) was treated as a real action, so
  `performEditorAction(0)` silently did nothing in plain text fields. Multi-line
  fields, a null `EditorInfo` and a declined action are all handled now.
- Autocorrect gained proximity-weighted edit costs derived from the shipped key
  geometry, case restoration, and a run-on splitter for space-bar mispresses.
- Keys regained press feedback, lost when `keyGestures` replaced `clickable`.
- The theme editor could not be scrolled: its panels sat below a `weight(1f)`
  `LazyColumn` in a non-scrolling `Column` and were clipped. Two full-screen
  scroll gestures moved the content by 0.00%.
- Device Pairing centred its content on a `fillMaxSize` column, leaving a screen
  of empty space under the app bar.
- Extract Screenshot did nothing on API 33+: the MediaStore query needed a
  gallery-read permission the app never declared, and returned an empty cursor
  that read as "no screenshots exist".

### Added

- Keyboard height and bottom padding as user settings; the number row brings its
  own height instead of taking it from the letter keys.
- `READ_MEDIA_IMAGES`, `READ_MEDIA_VISUAL_USER_SELECTED` and
  `READ_EXTERNAL_STORAGE` (capped at `maxSdkVersion=32`), requested at the button
  rather than at launch. Video import still uses the permission-free photo picker.
- An `ABC` control at the trailing edge of the emoji picker's tab strip. The
  leading chevron alone had already been added and was still reported as "no way
  back".
- A long-press affordance for keys whose alternates have no printable hint.

---

## [v0.1.1-ALPHA] - 2026-07-30

188 commits. Predominantly the keyboard: this is the release where the IME
stopped being a shell.

### Added

- Emoji picker with a shipped, offline category dataset built from Unicode's
  `emoji-test.txt`. No emoji artwork is bundled -- the platform font draws the
  glyphs, and `EmojiRepository` filters by `Paint.hasGlyph()` so an unsupported
  sequence is dropped rather than rendered as its parts.
- The quick-access toolbar, and a text-editing panel with caret movement,
  selection and clipboard actions.
- `KeyGestures`: one press/threshold/release state machine covering key repeat,
  long-press alternates and the space-bar caret scrub.
- `KeyStyle` as a fifth theme token type -- per-key fill, text, border and haze
  overrides, every one of them nullable and off by default.
- `KeyGlyphs`, a single label/icon table. There had been four disagreeing ones;
  the theme preview rendered SHIFT, SPACE and SYMBOLS as three identical "S" keys.
- Haptics with `VibrationAttributes.USAGE_TOUCH`, without which an IME's
  vibrations are filtered out entirely from Android 12.
- A keyboard preview screen and a layout editor.
- Debug-only usage diagnostics, in their own source set so the class does not
  exist in a release build at all.

### Removed

- **ML Kit, and Google Play Services with it** (2026-07-27). ML Kit pulled
  `com.google.android.datatransport` into the release manifest and DEX, which is
  incompatible with this project's zero-telemetry constraint, and it does not
  work on the de-Googled devices this project's audience overlaps with. QR
  scanning moved to `com.journeyapps:zxing-android-embedded`. The segmentation
  package went with it: there is no automatic segmentation in v1, and extraction
  is crop plus the manual eraser.

---

## [v0.1.0-alpha] - 2026-07-24

127 commits from the initial commit on 2026-07-18. First assembled alpha:
multi-module Gradle structure (`app`, `sticker-core`, `keyboard-core`,
`transfer`), Compose and MVVM with Hilt, Room-indexed sticker storage with bytes
on disk, the manual sticker creation and editing flow, video import and
GIF/WebP conversion, the IME service and typing core, the JSON theming engine,
clipboard history, and device pairing.

Segmentation shipped in this release as an ML Kit engine and was removed three
days later; see v0.1.1.
