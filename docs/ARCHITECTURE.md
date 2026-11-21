<!-- Engineered by uncoalesced -->

# Architecture

What the code actually does, as of `v1.0.0-testing`. Where a decision looks odd, the
reason is usually that the obvious version was tried and broke something.

## Principles

Everything works offline. The only network code in the app is device-to-device
transfer, and nothing about typing touches it.

No telemetry, no analytics, no crash reporter, no Play Services. This is checked
against the built APK in CI rather than trusted.

State flows one way. ViewModels hold `StateFlow`, the UI collects it, events go
back up as function calls. ViewModels hold no Context and no View.

Room is the source of truth for anything queryable. Large binaries live on disk and
Room indexes them.

## Modules

```
app  ──────────► sticker-core
 │  ────────────► keyboard-core ──► sticker-core
 └  ────────────► transfer
```

`app` is the settings and sticker-management UI, and the only module with an
Activity. `keyboard-core` is the IME: prediction, autocorrect, glide, theming,
layouts, clipboard, haptics. `sticker-core` is the data layer and image pipeline.
`transfer` is pairing and migration.

`keyboard-core` depends on `sticker-core` because the keyboard renders the sticker
library inside its emoji picker.

## Tech stack

Kotlin, Jetpack Compose with Material 3, Hilt, Room, Coroutines and Flow. OkHttp
and Retrofit exist only in `transfer`. AGP 9 with its built-in Kotlin support,
JDK 17, min SDK 26, target SDK 36.

Preferences are `SharedPreferences`, not DataStore. Each setter publishes to its
own `StateFlow` directly. There is deliberately no
`OnSharedPreferenceChangeListener`: those are held weakly, R8 removes the field
that keeps one alive, and the result was a release build where no setting took
effect until the process restarted. A CI check rejects reintroducing one.

Image work uses `webp-android` and `gifkt-jvm`. There is no segmentation engine.
Extraction is crop plus a manual eraser; the ML Kit path was removed because it
pulls Google's CCT logging transport with it.

## The keyboard hosts Compose itself

An IME is a Service, not an Activity, so `StickyKeysIME` implements the Lifecycle,
ViewModelStore, and SavedStateRegistry owners itself so a `ComposeView` can live
inside it.

The owners have to be set on `window.window.decorView`, not on the `ComposeView`
that gets returned. `setInputView()` inserts that view into the framework's own
decor, and `AbstractComposeView` resolves its recomposer by walking up from the
decor root, so tags on our own view are never in the search path. Getting this
wrong produces a keyboard that never appears and a crash on the main thread that
reads like nothing happening.

The IME's ViewModels are built by a hand-written factory in the service rather than
by Hilt. Adding a constructor parameter to one means editing that factory.

## One height for every mode

`rememberImePanelHeight()` is the single source: a dimension resource with a
landscape override, plus the number row's own height when it is on, scaled by the
user's height preference, with a floor and a window cap.

Every mode uses it, so switching between typing, emoji, clipboard, and text editing
never resizes the window. The number row adds height rather than dividing the
existing space, because drawing it inside a fixed panel cost every key a fifth of
its height.

The quick-access toolbar lives outside that fixed panel, in an outer Column. It
grows the window upward. Putting it inside means its height can only come from the
key grid, which squashes every key and shifts the board under the user's thumbs.

## Gestures

`KeyGestures.kt` holds one press state machine, not per-key handlers. Its
thresholds and pure decisions are in `KeyGestureRules.kt`, the long-press
alternates in `KeyAlternates.kt`, and the space-bar scrub in `ScrubGesture.kt`. It replaces
`clickable` rather than sitting beside it, because two detectors both claim the
down event, which is why the button role and click action are restated in
`semantics` or the keys become unreachable in TalkBack.

Four outcomes: repeat for backspace, an alternates strip for long-press,
positional scrubbing on the space bar, and glide.

Glide belongs to the key that received the touch. `GlideTracker` holds only the
thing one key cannot know, which is where the other keys are, and each key
registers its own rectangle from the `onGloballyPositioned` it already had. A press
becomes a glide when it leaves its own key's bounds rather than after a fixed
pixel distance, because the same distance means different things on a narrow key
and a wide one.

Keys register the character they currently type, lowercased. Registering the
shifted form meant every glide decoded to nothing while shift was armed, silently.

The caret is moved through the InputConnection, never with `KEYCODE_DPAD_*`. A DPAD
event is a focus-navigation event that editors happen to interpret as caret
movement, and at the end of the text it falls through to the host window's focus
search and moves focus out of the field entirely.

## Prediction

The dictionary is one shared `MappedByteBuffer`. Every read takes its own cursor
via `duplicate()`, because navigating the shared instance's position corrupts
concurrent lookups.

`PredictionEngine` maps the compiled dictionaries (`base_dict.bin`,
`names_dict.bin`) and bigram model (`bigram_lm.bin`), coordinates
`PersonalLearningEngine` across in-memory L1 caches (`BloomPersonalLexicon`,
`BloomPersonalBigramCache`) and Room L2 storage (`PersonalWordEntity`,
`PersonalBigramEntity` in `keyboard_database`), and ranks completions.
`PersonalLearningEngine` isolates single-occurrence out-of-vocabulary typos via
OOV typo quarantine until repeated and applies 14-day smooth exponential decay
alongside capacity pruning.

On-device adaptive spatial, trajectory, and threshold modeling sits in
`BloomSpatialProfile` (`bloom_profile.bin` per-key glide centroid offsets with
snapshot/rollback on revert), `SmartKeyHitbox` (anisotropic 2D Gaussian tap
centroids and X/Y variances in `bloom_tap_profile.bin`, plus context-weighted
boundary hit-testing near key borders), `BloomMemoryVault` (`bloom_user_memory.bin`
SHA-256-verified unified snapshot across app updates, deleted atomically with the
other `.bin` stores via `BloomStateFiles` on clear), `BloomAdaptiveThreshold`
(local autocorrect threshold calibration tuned from accept/revert feedback),
`BloomTrajectoryAligner` and `BloomTypeDecoder` (48-point arc-length trajectory
resampling and DTW glide beam search), `BloomTriggerPredictor` (multi-gate
autocorrect commit scoring), and `BloomRevertHistory` (session-scoped undo
suppression). The glide decoder (`GlideDecoder`) and spelling correction
(`SpellingCorrector`) are plain Kotlin classes with no Android dependencies, built
from a function that hands out cursors so JVM tests can run against the shipped
dictionary binaries directly. `Flictionary.kt` describes the trie format and holds
the exact-word lookup they share.

Edit costs are weighted by key geometry (`SmartKeyHitbox` and `KeyProximity`)
rather than uniform, with branch-and-bound pruning inside `dfsEditDistance`.
Uniform costs make every one-edit candidate a tie, which hands the decision to raw
frequency and picks the commoner word over the one the user's finger explains.

Several repairs sit outside the edit-distance trie walk: case is restored from the
typed word, adjacent transpositions pair swapped touch coordinates, and compound
run-on space splitting separates missing-space joins (`alot`, `thankyou`,
`goodmorning`, `ofcourse`, `inorder`, and mispressed space-neighbour joins) with
full multi-word Backspace and suggestion-chip undo.

Key commits are synchronous. Space and word-ending punctuation commit first, then
apply any correction as a follow-up edit guarded by a generation token. A key
commit never waits on a suspending lookup.

## Privacy gates

`IncognitoState` is one singleton flag, read by the learning gate, clipboard
capture, and the lock indicator. There is deliberately not a second copy.

`fieldKindFor(inputType)` classifies the field. It exists because
`IME_FLAG_NO_PERSONALIZED_LEARNING` has to be set by the host app and Android's own
text fields do not set it for passwords.

Write gating and read gating are separate on purpose. Incognito suspends writes and
leaves reads working, which is right for a field the host merely asked not to learn
from and wrong for a password. All privacy-gated states (password, PIN, and
credential fields, `IME_FLAG_NO_PERSONALIZED_LEARNING`, and manual Private Mode)
disable word/bigram learning, hitbox calibration, adaptive threshold updates, and
context-weighted hit-testing; sensitive fields and manual Private Mode suppress
suggestions, autocorrect, and glide decoding as well.

## Themes and layouts

Both are JSON: shipped presets in assets, user copies in `filesDir`, with the active
one named by an id in preferences.

Resolution never leaves the active value unset and repairs the stored id when it
points at nothing, or every launch rediscovers the same dangling pointer. Files that
cannot be parsed, or that parse but fail validation, are renamed rather than re-read
and re-skipped forever.

`KeyboardTheme.sanitized()` drops override combinations that paint invisible keys,
such as a glyph colour equal to the key fill, which two colour pickers defaulting to
the same swatch reach in two taps. It drops only the offending override.

A bad theme survives force-stop, cache clearing, and reboot, because the id is in
preferences and the file is in `filesDir`. That is why there is also a reset button.

## Content provider

`.sticker` is an extension `MimeTypeMap` cannot resolve, so the stock `FileProvider`
answered null for `getType()` on every sticker this app has produced. Apps that
resolve a URI's own type before accepting content refuse that.

`StickerFileProvider` reads the type from the file's magic bytes rather than from
Room, because a content provider can be queried without the rest of the app being
alive. It is registered in `keyboard-core`'s manifest, which is where the provider
authority lives.

## Generated assets

The dictionary, emoji data, and launcher icons are generated offline by the Python
tooling in `dictionary-tools/`, `emoji-tools/`, and `icon-tools/`, and committed.
Nothing is fetched at runtime.

Emoji ship as categorisation data only. Android draws the glyphs from its own font,
and `EmojiRepository` filters by `Paint.hasGlyph()` rather than an SDK version table,
because font coverage tracks the system font and OEMs update that on their own
schedule.

## Where things are

The keyboard's own code is in `keyboard-core/.../ime/`, the prediction engine in
`keyboard-core/.../domain/engine/`, and the settings app in `app/.../ui/screens/`.

| Looking for | File |
|---|---|
| The keyboard board and its state wiring | `ime/TypingKeyboardView.kt` |
| One key, its glyph and press feedback | `ime/KeyboardKey.kt` |
| The suggestion strip's chips | `ime/SuggestionStrip.kt` |
| The glide trail drawn over the keys | `ime/GlideTrail.kt` |
| The handlers every key is wired with | `ime/KeyHandlers.kt` |
| What a key press does | `ime/KeyPressHandling.kt` |
| Press, hold and glide detection | `ime/KeyGestures.kt`, thresholds in `ime/KeyGestureRules.kt` |
| Long-press alternates | `ime/KeyAlternates.kt` |
| Word boundaries and sentence starts | `ime/TextBeforeCursor.kt` |
| Building the IME's ViewModels | `ime/ImeViewModelFactory.kt` |
| Sending a sticker into the editor | `ime/StickerCommit.kt` |
| Glide decoding | `domain/engine/GlideDecoder.kt` |
| Autocorrect | `domain/engine/SpellingCorrector.kt` |
| A settings screen's state | `ui/screens/<Name>ViewModel.kt`, beside `<Name>Screen.kt` |

Conventions for new code, from ADR 0001 (`docs/adr/`):

- One file per thing a reader would search for, named after it.
- Pure logic, with no Compose and no Android, goes in its own file with a test that
  calls it directly. Thresholds live beside the logic that reads them.
- A screen's ViewModel and UI state go in `<Name>ViewModel.kt`; the composables in
  `<Name>Screen.kt`.
- Moving code never widens visibility past what the new file needs.
