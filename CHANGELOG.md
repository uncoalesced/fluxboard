<!-- Engineered by uncoalesced -->

# Changelog

All notable changes to FluxBoard. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning is
alpha-track and not yet semantic.

## How to read this

Dates are release dates. Anything marked as fixed but not yet confirmed on real
hardware says so, because this project has twice marked work complete on the
strength of code that read correctly and did not run.

Two version boundaries are worth knowing about. `v0.1.2-ALPHA` was never tagged,
so read that section as everything after the `v0.1.1-ALPHA` tag. `v0.1.3` and
`v0.1.5` were skipped as alpha numbers; `v0.1.5` was held back for the first beta.

## [Unreleased]

Build-verified only: `clean :app:packageReleaseArtifact` succeeds and
`KeyboardRecompositionTest`/`GlideGateTest` both pass under `--rerun`. Not yet
confirmed on a device, so nothing below is claimed as more than that.

### Fixed

- **Stock Material components fell back to M3's default purple the moment they
  touched a slot `StickyKeysTheme` didn't forward.** `Theme.kt` mapped only 8 of
  `StickyKeysColors`' 13 fields into Material3's `colorScheme` -- `secondary`,
  any container role, `surfaceVariant` and `outline` were never set, so a
  dialog, a ripple, or the theme/layout dropdown menus were the one place on
  screen where sticky6 was invisible.

  No new colors needed: `secondary`, `onSecondary`, `primaryContainer`,
  `onPrimaryContainer`, `secondaryContainer`, `onSecondaryContainer`,
  `surfaceVariant`, `onSurfaceVariant` and `surfaceContainer` all now forward
  existing `StickyKeysColors` fields (`primaryContainer` reuses `primaryVariant`
  -- Sage, the existing "latched" tone -- rather than inventing a new one).
  `outline` has no `StickyKeysColors` equivalent, so it reads `Taupe` directly;
  outline and secondary are never adjacent on screen, so sharing the hex is
  safe.

### Added

- **A glide trail.** A tapered, fading stroke now traces the finger's path
  while swipe-typing, drawn in `TypingKeyboardView`'s own `Canvas` pass over
  the key grid. Colour follows the active theme's accent, so a custom theme
  stays on-brand rather than showing a hardcoded colour; the stroke clears on
  lift or commit instead of decaying on its own.

  It reads `GlideTracker`'s new `trailPoints` -- a list separate from the one
  the decoder uses, so the two can never be confused -- only inside the
  `Canvas` draw lambda, which runs in the draw phase rather than composition.
  The key grid never reads `trailPoints`, so a moving finger repaints the
  overlay and nothing else; `KeyboardRecompositionTest`'s zero-rebuild
  assertion still holds with the trail in place.

### Changed

- **Keyboard Settings regrouped into five labelled cards** (Typing, Size &
  Feel, Appearance, Privacy, If Something Breaks) instead of one flat list of
  roughly fifteen rows, plus a search field that filters rows by label text and
  hides a group entirely once nothing in it matches. Same preferences, same
  `KeyboardSettingsViewModel` calls -- this changes where a control is, not
  what it does.

---

## [v0.1.5-BETA] - 2026-08-10

Re-released the same day the beta was cut, because a full device pass found a
defect serious enough that the first build should not be the one people install.
Same versionName and versionCode; if you have an earlier `v0.1.5-BETA` build,
reinstall rather than update.

### Fixed

- **Every preference silently stopped propagating in release builds.** Settings
  appeared to do nothing: a toggle wrote to disk and nothing in the app or the
  keyboard changed until the process was restarted. The privacy switch in the
  quick-access row was the worst of it -- it could be turned on and never off,
  and because it suppresses suggestions, autocorrect and glide, that left the
  keyboard's headline feature dead with the only control for it inert.

  `KeyboardPreferences` and `AppPreferences` published changes through a
  `SharedPreferences.OnSharedPreferenceChangeListener`. SharedPreferences holds
  its listeners in a `WeakHashMap`, so the `private val listener` field was the
  only strong reference to ours -- and R8, seeing a field written once and read
  once, removed the field. The listener was collected at the first GC and no
  preference change reached any `StateFlow` again for the life of the process.

  Debug builds are not minified, so it worked perfectly everywhere except the
  artifact that ships. Confirmed against `outputs/mapping/release/mapping.txt`,
  which lists `prefs` and all sixteen flow fields for the class and no `listener`
  field at all.

  Both classes are `@Singleton` and every write already goes through their own
  setters, so the listener was never doing anything a setter could not. Each
  setter now publishes to its own flow directly, which R8 cannot remove because
  the flow is read elsewhere. Clamped setters compute the clamped value once and
  use it for both the write and the flow, so the stored number and the published
  one cannot drift.

  `PreferencePublishTest` pins the setter contract (verified to fail when a
  publish is removed), and `scripts/check-source-rules.sh` now refuses to let a
  SharedPreferences listener back into the codebase.

### Changed

- `KeyboardLayouts.symbolsPrimaryRows` KDoc said `$` was not on symbols page 1.
  It has been since B4 put the currency key there, twelve lines below the
  sentence saying otherwise.

---

## [v0.1.5-BETA] - 2026-08-09, first cut

**The first beta.** versionCode 6, versionName `v0.1.5-BETA`. The app says so
itself: a BETA badge sits beside the name in Settings and a short notice under
it explains what that means for a tester. Both are derived from the version
string rather than a separate flag, so a stable build cannot announce itself as
a beta and a beta cannot stay silent.

Work in progress toward the first BETA. **Device-verified 2026-08-09** on a
Redmi Note 11 (Android 15) with real touch events -- glides drawn as
`motionevent` polylines across the real key rectangles, not `input swipe`, which
emits too few samples to record the corners a glide is decoded from.

The pass found three defects that unit tests could not have caught, all fixed
and re-verified: glides decoded to nothing whenever shift was armed, doubled
words lost to their single-letter twins, and long-press stopped working on every
letter. Each is described below.

### Added

- **Glide typing.** Swipe across the letters to type a word. On by default,
  with a switch in Keyboard settings.

  The decoder is the substance. A glide crosses every key between the letters
  the user meant, so the path for "hello" also contains "ho", "hell" and "hilo"
  as valid readings -- the question is never "is this word spelled like the
  path" but "of the many words the path could be, which one was drawn". Word
  letters must appear along the path in order; skipping keys the path crossed is
  free; and the corners the finger turned are what separate the survivors.

  Two cases carry it, and both are tested because both are where a plausible
  implementation quietly fails. A finger cannot cross the same key twice in
  succession, so a doubled letter matches without advancing -- otherwise
  "hello", "coffee" and "letter" would be unglideable and the feature would read
  as broken rather than imperfect. And at speed the finger rounds a turn and
  lands on a neighbour, which is accepted at a cost so fast glides work while a
  clean read still wins.

  The gesture belongs to the key that received the touch rather than to a
  detector layered over the grid: two detectors claiming the same down event is
  the problem `keyGestures` replaced `clickable` to avoid. A press becomes a
  glide when it leaves its own key's bounds -- not after some number of pixels,
  which would mean a different gesture on a small key than on a wide one.

  Suppressed entirely on password and PIN fields, through the same gate as the
  suggestion strip. A new way of producing words is a new way of leaking them,
  and it arrived after the privacy work rather than alongside it. Confirmed on
  device: gliding into a password field types nothing and learns nothing.

  The readings that lost stay in the suggestion strip, and tapping one replaces
  the committed word rather than appending after it. This matters more for glide
  than for tapping: several real words are usually valid readings of the same
  path, so being wrong is the normal case rather than the exceptional one.
  Gliding t-o-o commits "to" and offers "too" first, because those two paths are
  *identical* and no decoder can separate them -- only the user can.

  Three device findings, each invisible to the tests that existed:

    - Every glide decoded to nothing whenever shift was armed. Keys register the
      character they currently *type*, so with auto-capitalize on an empty field
      they registered as 'H', 'E', 'L' while the dictionary is lowercase.
      Nothing errored; the feature simply did nothing, which is the hardest kind
      of failure to attribute. Registration now lowercases.
    - Doubled words lost to their single-letter twins: g-o-o-d produced "god",
      t-o-o produced "to". A doubled letter carried a one-point cost, on the
      reasoning that doubling is slightly unusual. The reasoning was wrong
      rather than mistuned -- a glide *cannot show* a doubled letter, so the path
      carries no evidence either way and charging for it invents a preference
      against every doubled word in English. Now free, so frequency decides.
    - Long-press stopped producing corner symbols on all 26 letters. The glide
      watch had replaced the long-press window rather than running inside it, so
      holding a letter committed it as a tap. It now resolves only three
      outcomes -- left the key, lifted, or the window elapsed -- and hands a hold
      to the existing machinery untouched.

- **Symbols on keys can be turned off.** Settings > Keyboard, under the number
  row. On by default. Off gives a plainer board.

  Presentation only, and that separation is load-bearing rather than tidy.
  `longPressFor` falls back to the corner hint for any key with no alternates of
  its own, which is every letter -- so the obvious implementation, withholding
  the hint, would have silently removed long-press access to the entire
  punctuation set. A user turning off a *visual* setting would have lost a way
  of typing with nothing to say so. A hidden hint also does not fall through to
  the hold-available dot, or turning the setting off would swap one mark for
  another on every letter.

- **Backspace in the emoji picker**, bottom right, where the letter keyboard
  puts it, repeating on hold through the same gesture machine. Correcting a
  mistyped emoji previously meant leaving the picker, deleting, and coming back.

### Changed

- **Caps lock is a double tap rather than the third step of a cycle.** The shift
  key was lower, upper, caps lock, back to lower, which put caps lock one tap
  from lower case so it latched by accident -- and escaping it took a *third*
  tap, so overshooting meant going the rest of the way round rather than
  pressing the key again to undo what you had just done.

  Now: a tap arms shift; a second tap within a second latches caps lock; a
  second tap later turns shift back off, because by then it is a separate
  decision about a shift you can see is already on. From caps lock a tap always
  releases, whatever the timing -- that is the one case where getting it wrong
  strands somebody in capitals.

- **Space on a symbols page returns to the letters.** A space ends a word and the
  next word is almost never more symbols, so the board was left on the wrong
  plane and the user had to notice and press ABC. Enter already reset the page
  for the same reason.

### Closed without a fix

- **"Typing does not feel smooth"** does not reproduce and is closed on that
  basis rather than on a change. Nothing was done to address it, so if it
  returns it is a new observation rather than a regression of this one. The
  things originally suspected -- no baseline profile, debug-build overhead, the
  unguarded suggestion strip -- are all still true and still unaddressed; none
  of them is a defect on its own.

---

## [v0.1.4-ALPHA] - 2026-08-06

Tagged `v0.1.4-ALPHA`, versionCode 4. Planning for this release is in
the release plan for that version.

**Device status:** everything below marked with a dagger was exercised on a
Redmi Note 11 (Android 15) on 2026-08-06 -- real key taps and real gestures, not
`adb shell input text`, which bypasses the IME entirely and measures nothing.
The rest is unit-tested only. The device pass notes carry
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
