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

## [Unreleased] - v0.1.7 work in progress

`.\gradlew.bat build` is green (compile, ktlint, lint, 389 unit tests) and
`scripts/check-source-rules.sh` passes.

**Device-verified on 2026-08-16**, against the signed release installed in place on
the LineageOS phone, typing into an SMS compose field by tapping real key
coordinates. Five of the six fixes below were confirmed on the phone and say so
individually. The sixth is a race, where a correct outcome on the day proves
nothing, so it stays on the unit test. The one item that is *not* fixed -- glide
suggestions -- was narrowed by the pass rather than closed.

### Added -- next-word prediction after a space (GitHub issue #17)

"I type hello and then hit space, the next word isn't predicted, its just blank."

That was not a broken lookup, it was a scope boundary that had never been crossed.
`PredictionEngine.getSuggestions` returns early on an empty prefix, and
`TypingViewModel.updateSuggestions` short-circuited on a blank `currentWord` before
that -- so at the one moment the sentence context had the most to say, it was never
asked. The v0.1.6 bigram work was deliberately built to *re-rank* candidates that
already match what is being typed, and deliberately not to generate any.

That rule is unchanged, and is now asserted: the moment a letter is typed the strip
goes back to completing it. What is new is a separate question for the separate
case where there is no prefix to contradict.

- `LanguageModel.followersOf(previousWord, limit)` -- the one place context may
  *be* a candidate source. Defaulted to empty on the interface, implemented by
  `BigramLanguageModel` off the table it already holds.
- `PredictionEngine.getNextWordSuggestions(previousWord)`, called from
  `updateSuggestions` when the word is blank.
- Suppressed at a sentence boundary. The corpus counts "hello"/"there" because they
  were adjacent, and across a full stop that is two unrelated words -- the same
  judgement `onSentenceStarted` already makes by dropping the context outright.
- Suppressed in a sensitive field, like every other dictionary *read*. A strip of
  predicted words above a password field is a shoulder-surfing hazard before any of
  it reaches storage, and this is a new way of putting words there.
- Tapping a suggestion now refreshes the strip too. It previously went blank until
  the next keystroke, which reads as the tap having broken something.

`NextWordSuggestionTest` covers all of the above.

### Fixed -- a suggestion lookup could land on top of a word already being typed

Pre-existing and made reachable by the above: `updateSuggestions` dispatched a
coroutine and published whatever came back, with no check that the text had not
moved meanwhile. Two lookups are now routinely in flight at once -- a next-word
lookup fired when the space committed, and a completion lookup for the letter typed
straight after -- and the slower one won whichever it was. The lookup now carries
the generation token and publishes only if it is still current.

### Fixed -- picking a key text colour wiped the key fill (zap, v0.1.6)

"I tap on 1 color for text opacity and then it resets the field above it."

`KeyboardTheme.sanitized()` guards against a glyph that cannot be told apart from
the key under it, which two colour pickers defaulting to the same swatch reach in
two taps. The guard reset **both** overrides in one step, so a fill chosen and
accepted in an earlier edit vanished the moment a later edit picked a text colour
near it -- and because the editor reads the sanitized theme and saves it back, the
loss persisted.

Now dropped one at a time, glyph first, re-checked in between. The fill is still
dropped when it has to be: a fill set to the palette's own glyph colour cannot be
repaired by restoring the glyph, and one reset would not converge. This is the same
class of defect as the v0.1.6 "two sliders reset each other to 100%" fix, one layer
along.

**This reopens and answers `docs/roadmap.md` 4E.15.** The roadmap asked zap to
retry key haze on v0.1.6 and said that if it still reproduced it was a new defect.
It did reproduce -- but haze was never the mechanism. `sanitized()` does not inspect
haze at all, and the sequence zap described is the fill/text contrast reset above.

### Fixed -- the quick-access toolbar resized the IME window once per frame (zap, v0.1.6)

The expand animation stuttered badly enough that individual frames were visible.
Not a recomposition regression -- `KeyboardRecompositionTest` still passes.

The toolbar lives outside the fixed-height panel and grows the window upward, which
is what keeps it from stealing height from the keys. But an IME window is
WRAP_CONTENT: its height *is* that content's height, measured by the framework and
pushed to WindowManager and into the host app's own layout pass. `expandVertically`
was therefore not animating a view inside a stable window, it was resizing the IME
window and relaying out the app being typed into, roughly fifteen times per chevron
tap. Replaced with a fade over a single resize.

`MediaRow`'s `isMusicActive` binder call also moved off the main thread; it fired on
the exact frame the toolbar opened.

### Fixed -- multi-character key labels clipped instead of shrinking

`123`, `ABC` and `{&=` sit on 1.5-weight keys, and `maxLines = 1` defaults to
clipping. Three independent multipliers decide the label's width -- the theme's type
scale, the system font-size setting, and the key's share of the row -- and none of
them is aware of the others, so on a large font scale the label was silently cut
short. Labels now shrink to fit, capped at the size they were, so anything that
already fitted is drawn identically.

Reported by zap as "the number button got fucked some how its not scaling" and
confirmed as this rather than a row-height problem.

### Added -- currencies on the number row (roadmap 3.7)

Holding digit 4 offers `€ ¥ $ ¢ ₹` and commits `$` on a straight release, which is
what the key's corner hint already promises. Its superscript and fraction were
dropped to make room -- a decision, and the only digit where the shifted symbol
stays in the hold strip. Both currency keys read one list, so the set cannot drift
between the number row and the symbols page.

The digit fraction strips for `0` and `3`-`9`, which shipped in v0.1.6 as an
unconfirmed proposal, are confirmed as they are.

### Unresolved -- glide typing produced no suggestion strip (zap, v0.1.6)

Not fixed, but the device pass moved it a long way.

**The strip works.** Two glides driven along straight key runs both committed a word
and both populated the strip with the readings that lost -- `w`-to-`t` gave `At `
with `Wet`/`Set`/`Art` offered, `a`-to-`l` gave `All ` with `Asp`/`Ail`/`Awl`. Tapping
an alternate replaced the committed word rather than appending to it. So the
commit-to-strip path is not where zap's report lives, and `GlideSuggestionsTest` now
pins the ordering it depends on.

**What did reproduce the exact symptom** was resting on the first key before
dragging: a 700ms hold then a drag committed `@`, the corner-hint alternate, and left
the strip empty -- no glide, and nothing on screen to say why. That is the trap this
project already documented and had never actually watched happen.

So the leading account is that the glide is never recognised, rather than that the
strip fails to fill, which points at the activation threshold instead of
`onGlideCommitted`. It is an account, not a diagnosis: nobody has watched zap do it,
and `adb` cannot draw a cornered path, so whether he pauses before swiping and
whether accuracy collapses on real multi-corner strokes both still need a hand.

## [v0.1.6.1-BETA] - 2026-08-16

versionCode 9. A point release with one job: **v0.1.6-BETA could not be installed.**

### Fixed -- Play Protect blocked the install of v0.1.6-BETA

Sideloading the v0.1.6 APK produced "App blocked to protect your device -- this app
can request access to sensitive data", and the install did not proceed. Nothing was
wrong with the build; Play Protect's sideloading protection blocks any app that
**declares** `BIND_NOTIFICATION_LISTENER_SERVICE`, and it reads the manifest rather
than the class.

v0.1.6 declared one for a single feature: the media row showing the current track
name. `MediaSessionManager.getActiveSessions()` will not answer without a bound
`NotificationListenerService`, and Android offers no narrower permission for it.
That service was scoped as tightly as the platform allows -- off by default, granted
only from system Settings behind a consent screen that named what the grant really
covers, and overriding no notification callback at all, with a source-rule check
enforcing it. None of that was visible to Play Protect.

FluxBoard is distributed only by sideloading, so this was not a close call: a
permission that makes the app uninstallable for the entire audience cannot buy one
line of track metadata. The service, its reader, the preference, the consent screen
and its strings are all removed.

- **Media transport controls are unaffected.** Previous, play/pause and next never
  needed the permission and go on working exactly as before -- `MediaTransport` was
  always the permission-free path and is now the only one.
- **What is lost:** the track and artist caption beside those buttons. Nothing else
  from v0.1.6 changes.
- `scripts/check-source-rules.sh` now fails the build if the declaration ever comes
  back, matched on the quoted permission and on the base class rather than on a
  class name, so a differently-named service cannot slip past. Verified to fail by
  reintroducing the declaration on purpose, per this project's rule about guards
  that pass either way.

Also bumped versionCode, which is what `assetBackedFile` keys asset re-extraction
on, so the v0.1.6 dictionary and bigram table refresh on update.

## [v0.1.6-BETA] - 2026-08-16

versionCode 8. Signed release APK is 8,112,436 bytes (7.74 MB). Device-verified on
the LineageOS test phone (Redmi Note 11, Android 15) against the signed artifact,
installed in place so no app data was lost. Items a phone could not settle are
listed as such at the end rather than claimed.

### Fixed -- contractions were missing from the dictionary entirely

Reported as "punctuation in a word is not detected". The word-boundary code was
never the problem: `wordUnderCaret` has always kept an apostrophe inside a word.
**Not one contraction was in the shipped dictionary.** `you're`, `don't`, `it's`,
`can't`, `i'm` were all absent, and so were their apostrophe-less spellings.

Both halves of the pipeline behaved exactly as designed and their intersection was
empty: the corpus that builds `base_dict.bin` carries no punctuation at all, so
"you're" was never in it, and the form the corpus *did* count -- "youre" -- was then
dropped by the SCOWL spelling filter because "youre" is not a word either. That is
roughly one word in twenty of running English typed with no suggestion behind it,
and an open invitation for autocorrect to reach for something else.

- `dictionary-tools/build_flictionary.py` now emits every English contraction,
  weighted by the corpus frequency of its stripped form. 63 of 71 landed;
  `base_dict.bin` grew 1,260 bytes. The list is written out rather than sourced
  because contractions are a closed grammatical class, not an open-ended set.
- `build_bigrams.py` rewrites a stripped contraction the corpus counted into the
  spelling users type, so `don't know`, `i'm not` and `that's what` are real
  context now. Deliberately not applied where the stripped form is a word in its
  own right -- "its", "cant", "were" -- because there the count belongs to the
  plain word too; `boostFor` retries an apostrophe-stripped lookup for those.
- New `PredictionEngine.restoreApostrophe`: "youre" to "you're", "dont" to "don't".
  A targeted repair, tried ahead of the general search for the same reason
  `splitOnMispressedSpace` is. Edit distance cannot get this right at any cost
  setting -- inserting an apostrophe and deleting a letter are both one gap, so
  "youre" reaches "you're" and "your" for the same price and frequency decides,
  which "your" wins by three orders of magnitude. Cheapening the apostrophe until
  "you're" won would have let "were" reach "we're" for nothing, which is real-word
  correction. Fires only for a word not already in the dictionary, which is what
  leaves every contraction homograph alone.
- **Device-verified:** `dont` commits `Don't `, `youre` commits `You're `, and
  `its` stays `Its `.

### Fixed -- a bundled asset was never refreshed after an app update

Found on the phone, and findable nowhere else. Both engines extracted their data
file to private storage under `if (!file.exists())`, which is right on a first
launch and wrong on every update after one: the previous version's copy survives
and the new asset shipped in the APK is never read. The first v0.1.6 build
installed over v0.1.5.1 still typed `Font ` for "dont", because the rebuilt
dictionary was sitting unread inside the APK.

New `assetBackedFile` stamps each extracted copy with the app's version code and
re-extracts when it changes, so an asset update happens once per install and never
on an ordinary launch. `AssetCacheTest` pins it. A unit test could not have caught
this: it gets a clean app directory every run, so the stale branch cannot occur.

### Fixed -- the suggestion pool was chosen by word length, not frequency

Recorded as a known limitation during the v0.1.5.1 device pass and fixed here.
`getBaseSuggestions` bounded its walk by *terminals found*, and because the walk is
breadth-first that means it stopped near the top of the subtree, where the short
words are. Prefix `ma` offered `map` and `mar` while `many` -- more frequent than
both -- never entered the pool at all.

The budget now counts nodes visited (20,000), sized against the real dictionary:
the widest single-letter subtree is `s` at 15,716 nodes, so every prefix a user
actually types is walked in full. The queue was also a list popped with
`removeAt(0)`, harmless at the old budget and quadratic at this one; it is an
`ArrayDeque` now. This is what limited sentence context, which re-ranks the pool
and never adds to it.

### Added

- **Word-wise backspace, two ways.** A backspace straight after a glide takes the
  whole word back, because one gesture put it there; the state this needs already
  existed in `consumeGlideCommit`. Holding backspace and dragging left switches
  from character repeat to word repeat, on its own 24dp activation distance --
  deliberately further than the space-bar scrub's 18dp, because the repeat path has
  never had an activation distance and a resting thumb drifts. **Device-verified:**
  a drag turned `alpha bravo charlie delta echo` into `alpha bravo `, while a plain
  450ms hold removed exactly three characters.
- **Glide typing obeys shift.** A glide at a sentence start capitalizes and one with
  caps lock engaged uppercases. A rendering fix, not a decoder one: `GlideTracker`
  lowercases every key it records and has to, so case is applied at commit the same
  way `matchCase` applies it to an autocorrection.
- **The Enter key shows what it will do** -- send, search, go, next or done, read
  from the field's own `EditorInfo`. Behaviour is unchanged; only the artwork was
  ever static. **Device-verified** in an SMS compose field. UNSPECIFIED (0) and NONE
  (1) both fall back to the return arrow, which is the trap this pairing always sets.
- **Emoji search.** Filters the grid on the catalogue's own names. Typed on a pad the
  picker owns rather than a text field: this *is* the keyboard, so a focusable field
  has nothing to type into it. That also keeps the whole feature off the typing path
  -- no autocorrect, no glide, no learning, and nothing that can reach the host
  editor. **Device-verified:** `cat` filters the grid and the message field stays
  untouched.
- **The Recents grid holds still during a burst of emoji taps** for three seconds.
  The write is *not* delayed, only the displayed order: an IME is killed freely and
  the usual flow is tap-emoji-then-send, so a deferred write would have dropped most
  emoji out of Recents.
- **The layout editor reaches the symbol pages**, which have been part of the saved
  layout since v0.1.4 with nothing able to edit them. The remap dialog is a preset
  picker with free text kept as an escape hatch, and it now refuses a control token:
  mapping a key to the literal string "SPACE" produced a key that drew blank and
  typed nothing. The number row is deliberately not offered -- it is generated at
  render time, not stored, so a tab for it would discard what the user did.
- **An expanding pill dock** replaces the app's bottom navigation bar: icon-only at
  rest, the active one growing to show its name. Four destinations, not the
  reference image's five. Its spring lives in `theme/MotionTokens.kt` with the rest
  of the motion language.
- **Media metadata, opt-in and off by default.** Reopens a permission this project
  refused on its merits -- `BIND_NOTIFICATION_LISTENER_SERVICE` grants the text of
  every notification on the device -- so the trade is visible at every layer. The
  consent screen states what the *permission* grants, not what the feature does.
  The listener service overrides no notification-content callback, and
  `check-source-rules.sh` now fails the build if one is ever added; that rule was
  proven to bite by crossing the boundary on purpose. `MediaTransport`'s
  permission-free path is untouched, so declining costs nothing that exists today.
- **The editing panel's chips answer a press** with the same scale and fill as a key,
  read from `MotionTokens` rather than redefined.

### Not settled by this pass

- **Media metadata against a real session** is unverified: granting notification
  access is a system permission change on the owner's phone and was not taken during
  a test pass. `NEEDS DEVICE`.
- **Glide gestures** were not driven on device. `adb` cannot produce one faithfully
  -- each `input motionevent` costs about 100ms, so a simulated finger is correctly
  read as a hold. Covered by unit tests.
- **Grid-level hit testing** (`n`/`b` near the space bar) ships as a design document
  in the project's internal planning notes, not a diff. A materially smaller path
  than the last investigation found is recorded there, gated on a device measurement.
- **Profanity filtering of the dictionary** is not implemented, by decision rather
  than deferral: no words are removed.

## [v0.1.6-BETA] - 2026-08-16, detail

Left titled "Unreleased" through the release and renamed here, because a second
section by that name above it now describes work that genuinely is unreleased.
Everything below shipped in v0.1.6-BETA; the text is otherwise untouched.

Build-verified only: `clean :app:packageReleaseArtifact` succeeds and
`KeyboardRecompositionTest`/`GlideGateTest` both pass under `--rerun`. Not yet
confirmed on a device, so nothing below is claimed as more than that -- except
the v0.1.6 block immediately following, which records what a device pass did and
did not cover.

### Added -- v0.1.6 work: sentence context, beam-search glide, visual pass

Implemented from `V0.1.6-AUTOCORRECT-GLIDE-UPGRADE.md` (a Cowork-written spec).
Device-verified on the LineageOS test phone against the signed release artifact,
installed in place so no app data was lost.

- **Autocorrect and suggestions now see the previous word.** New asset
  `keyboard-core/src/main/assets/bigram_lm.bin` -- **485,367 bytes (474 KB) raw,
  223,284 bytes in the APK** -- built offline by the new
  `dictionary-tools/build_bigrams.py` from Norvig's `count_2w.txt` (5.31 MB,
  286,358 lines, **tab**-separated unlike `count_1w.txt`), filtered through the
  same SCOWL intersection that keeps `base_dict.bin` typo-free. Caps chosen:
  `N_PREV_WORDS = 60_000` (59,637 filled), `MAX_FOLLOWERS = 8`; 266,455 pairs
  survive, giving 15,471 previous-words with a record. Signed release APK is
  8,106,996 bytes (7.73 MB) with it included.
  - Weights are normalized **per previous-word**, not globally. The spec called
    for `build_trie`'s global log normalization; against a global maximum of
    2.77e9 that compresses every surviving pair into roughly 100-214, so the
    stored weight would have degenerated into a flag meaning "this pair exists".
  - **Device-verified:** typing `we` alone offers `we, web, were`; typing
    `Very we` offers `well, we, web` -- `well` promoted from fourth to first.
- **Glide decoding is a bounded beam search** rather than unbounded recursion,
  `MAX_GLIDE_BEAM = 40`. Partial readings are ranked by `cost - pivotCredit`,
  an estimate of the final score, **not** by cost alone: a glide crosses its keys
  exactly, so nearly every branch sits at cost 0 and a stable sort by cost keeps
  whichever subtries the trie lists first. Measured with cost-only ranking, the
  h-e-l-o path decoded to `[ho, go, hi]` -- "hello" was gone.
  - **Device-verified:** a synthetic h-e-l-o path commits `hello`, leaving
    `ho, hero, no` in the strip as losing readings.
- **Corners are graded rather than counted.** `GlideStroke` carries `dwell` and
  `pivotStrength`; the 60-degree boolean cutoff becomes a continuous confidence
  combining turn sharpness with how long the finger lingered, and an unexplained
  corner now costs in proportion to how sure it was. Both new fields default to
  empty, and an empty stroke reproduces the old arithmetic exactly -- which is
  what let every existing `GlideStrokeTest` assertion pass unmodified.
- **`LanguageModel` seam.** `PredictionEngine` depends on the interface;
  `LanguageModelModule` binds it to `BigramLanguageModel`. No model is bundled
  and no TFLite dependency was added.
- **Press physicality, theme shapes, rounded system font.** Keys gained a spring
  scale and an elevation that compresses under the finger, driven off the theme's
  own `hazeRadius` rather than a constant. `KEY_CORNER_RADIUS` is gone in favour
  of `StickyKeysTheme.shapes.medium`; typography moved to the platform's
  `sans-serif-rounded` alias (no bundled font, falls back to the system sans);
  `default_dark`/`default_light` now ship a subtle `keyStyle` haze while
  `amoled_dark` deliberately stays flat. New `theme/MotionTokens.kt` is the one
  definition of the press constants, shared by the key grid and the strip.
- **Suggestion-strip chips.** Previously a bare `Text` with a hit box; now pill
  shaped with the same press scale and fill as a key, no ripple.

### Known limitation found during the v0.1.6 device pass

- **`getBaseSuggestions` truncates its candidate search by terminal count during
  a breadth-first walk**, so the 30-terminal budget is spent on short words and
  common longer ones never enter the pool at all. Verified against the shipped
  dictionary: prefix `ma` offers `may, make, map, made, mail, main, man, mar,
  male, ma` and **not** `many`, whose frequency (209) is higher than both `map`
  and `mar`. Pre-existing, unrelated to the bigram work, and not fixed here --
  but it is what limits how often sentence context can visibly change the strip,
  because context re-ranks the pool and never adds to it. This is why `How m`
  produces the same suggestions as a bare `m` on device.

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

## [v0.1.5.1-BETA] - 2026-08-13

Point release on top of v0.1.5-BETA. Two tester-reported defects, both of them
data loss rather than cosmetic, plus the ktlint break that was keeping
`gradlew build` red. `versionCode` 7.

### Fixed

- **Two Key Styling sliders reset each other to 100%.** Setting Fill opacity to
  5% and then dragging Text opacity down snapped *both* back to full, wiping a
  value the user had set in an earlier, separately-accepted edit and had not
  touched since.

  `KeyboardTheme.sanitized()` captured the resolved fill and text once, then ran
  two checks against those same captured values: one repairing an invisible
  glyph, and a second resetting the fill when fill *and* text were both below
  12%. The second still saw the pre-repair text, so any later edit that pushed
  the text under the threshold also destroyed an unrelated low fill. Every
  slider tick round-trips through save, reload and `sanitized()` before the
  control redraws, so it looked like the sliders were fighting the user.

  The second reset is gone rather than corrected. Repairing the glyph is the
  whole job: an invisible *fill* alone was always allowed -- the panel shows
  through and translucent keys are a deliberate look -- and `resolveText` sets
  alpha from `textOpacity`, so a repaired glyph is opaque by construction and
  the second condition could never have fired again anyway. The contrast check
  still catches a glyph indistinguishable from its key.

  The Text opacity slider now also stops at 12% instead of running to zero. A
  control that reaches a value the save path immediately rewrites is
  indistinguishable from a broken one; the fill slider keeps its full range,
  because a transparent key is legitimate. `ThemeFallbackTest` gains the
  reported sequence (fill 5%, then text dragged below the threshold, fill must
  survive) and was verified to fail when the old reset is put back.

  Device-verified: with fill at 1%, dragging Text opacity to the far left leaves
  fill at 1% and stops text at its floor, and adjusting Haze afterwards moves
  nothing else -- which is the same mechanism behind the earlier "messing with
  the key haze deleted my theme" report.

- **Backspace took several presses to delete a single letter.** Reported by a
  tester: correcting one mistyped character was "really stubborn, takes a few
  tries."

  `sendDelete()` was the last edit primitive in `StickyKeysIME` still sending a
  raw synthetic `KeyEvent` -- a `KEYCODE_DEL` down/up pair built with the
  two-argument constructor, which leaves `downTime`/`eventTime` at zero. Some
  host editors treat a zero-timestamp event as stale and drop it, which is
  exactly why `sendRawEnter` was rewritten to build real timestamps and why
  `moveCursor` stopped sending `KEYCODE_DPAD_*` entirely: a raw key event's
  effect belongs to the host, not to this keyboard.

  Nothing here could see the drop. The caret prediction assumed the delete
  landed regardless, so the keyboard's tracked position drifted by one and the
  press looked dead; `onUpdateSelection` noticed the mismatch and resynced a
  moment later, which is why it recovered after a few tries instead of staying
  broken.

  Backspace is now `deleteSurroundingText`, with `commitText("", 1)` for a
  selection (`deleteSurroundingText` ignores an active selection by contract, so
  it is the wrong call there). The character count comes from the new pure
  `backspaceLengthFor`, which returns 2 for a UTF-16 surrogate pair so pressing
  backspace on an emoji removes the whole emoji rather than stranding half of
  it. `BackspaceTest` pins that arithmetic. Both backspace paths -- the typing
  keyboard's DEL key and the emoji picker's own backspace button -- route
  through this one method, so both are fixed together.

  Verified on a device (LineageOS, 1080x2400) against the signed release
  artifact, in two hosts -- the Settings search field and the AOSP Messaging
  compose box: 32 single presses each removed exactly one character on the first
  press, at the end of the text and in the middle of it; five-press bursts
  removed exactly five; an emoji disappeared whole from both the DEL key and the
  picker's own button, never half; and a selection (both select-all and a
  double-tap word) went as a unit rather than one character outside it.

  **What that pass does not show:** a control build carrying the old
  `KeyEvent` body passed the same trials in the same two hosts, so neither of
  them was dropping the zero-timestamp event. The tester's report came from
  Discord, which is not installed on this phone, and the drop is host-specific by
  nature. Treat this as "the new primitive is correct and nothing regressed",
  not as a reproduction of the original symptom.

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

  Build-verified only (clean release build, `KeyboardRecompositionTest` and
  `GlideGateTest` pass under `--rerun`); not yet confirmed on a device.

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
  assertion still holds with the trail in place. Build-verified only, not yet
  confirmed on a device.

### Changed

- `KeyboardLayouts.symbolsPrimaryRows` KDoc said `$` was not on symbols page 1.
  It has been since B4 put the currency key there, twelve lines below the
  sentence saying otherwise.

- **Keyboard Settings regrouped into five labelled cards** (Typing, Size &
  Feel, Appearance, Privacy, If Something Breaks) instead of one flat list of
  roughly fifteen rows, plus a search field that filters rows by label text and
  hides a group entirely once nothing in it matches. Same preferences, same
  `KeyboardSettingsViewModel` calls -- this changes where a control is, not
  what it does. Build-verified only, not yet confirmed on a device.

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
