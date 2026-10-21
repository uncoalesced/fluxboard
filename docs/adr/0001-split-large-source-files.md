<!-- Engineered by uncoalesced -->

# ADR 0001: Split the large source files along their real seams

- Status: Proposed
- Date: 2026-10-21
- Applies to: `v0.1.7.5-BETA` (tag `pre-refactor`)

## Context

Ten Kotlin files are over 500 lines. Six are in `keyboard-core`, four are app screens:

| File | Lines | Module |
|---|---|---|
| `ime/TypingKeyboardView.kt` | 1086 | keyboard-core |
| `domain/engine/PredictionEngine.kt` | 1040 | keyboard-core |
| `ime/KeyGestures.kt` | 983 | keyboard-core |
| `ime/TypingViewModel.kt` | 967 | keyboard-core |
| `ime/StickyKeysIME.kt` | 793 | keyboard-core |
| `ime/EmojiPickerView.kt` | 634 | keyboard-core |
| `ui/screens/KeyboardSettingsScreen.kt` | 997 | app |
| `ui/screens/StickersLibraryScreen.kt` | 900 | app |
| `ui/screens/LayoutEditorScreen.kt` | 741 | app |
| `ui/screens/ThemeEditorScreen.kt` | 538 | app |

Length alone is not the problem. The problem is that a newcomer cannot tell from a
file name where a piece of logic lives. The glide decoder sits inside the class that
also learns words and ranks completions. The press-versus-glide gate shares a file
with the alternates tables and the space-bar scrub. The key-press handler is defined
in `KeyboardRows.kt`. Every app screen file also holds its ViewModel.

Three pieces of planned work shape what is worth doing now:

1. The glide and autocorrect work needs to replay recorded traces into the decoder on
   the JVM, fast and without Android. Today `decodeGlide` is a `suspend` member of a
   `@Singleton` that needs a `Context`, a Room DAO and a mapped asset to construct.
2. The same work will change the gesture code (`GlideTracker`, the press-versus-glide
   gate in `KeyGestures.kt`) and the scoring constants.
3. The app screens and the emoji panel are due to be rebuilt in a new visual style.
   Polishing their composables now would be thrown away.

## Decision

Split where a file holds two things a reader would look for separately, and where
the split makes the next piece of work cheaper. Leave a file alone where the size
comes from one coherent thing or where a rebuild is coming. Every step moves code
verbatim: no renamed behaviour, no changed constants, no reordered logic.

### Per-file decisions

**`PredictionEngine.kt`: split.** Three readers share one dictionary format.

- `domain/engine/Flictionary.kt`: the trie format. The header offset, the node read
  (frequency, terminal flag, children), and `frequencyOf` as a function over a
  `ByteBuffer`, copied verbatim from the engine.
- `domain/engine/GlideDecoder.kt`: `GlideBeam`, the glide constants, the beam search,
  `pivotCreditAt`, `glideContextCredit`, `nextGlideMatch`. A plain class constructed
  with a `LanguageModel`, with a non-suspending
  `decode(dictionary: ByteBuffer, stroke: GlideStroke, previousWord: String?)`. No
  `Context`, no DAO, no coroutine. `PredictionEngine.decodeGlide` keeps its signature
  and becomes `withContext(IO) { glideDecoder.decode(reader, stroke, previousWord) }`.
  This is the seam the trace replay needs: a JVM test can wrap
  `src/main/assets/base_dict.bin` in a `ByteBuffer`, build a `GlideStroke` from
  recorded points with `buildGlideStroke`, and call `decode` directly.
- `domain/engine/SpellingCorrector.kt`: `nearestWord`, `dfsEditDistance`,
  `penaltyFor`, `restoreApostrophe`, `apostropheVariantsOf`, `displayContraction`,
  `splitOnMispressedSpace`, `matchCase`, and the edit-cost constants. Same shape as
  the decoder: plain class, `ByteBuffer` in, no Android.
- `PredictionEngine.kt` keeps what is actually its own: mapping the asset, the
  `reader()` invariant, `learnWord`, `getSuggestions`, `getNextWordSuggestions`,
  `getBaseSuggestions`, and the personal-dictionary checks around
  `getAutoCorrection`. Expected size: about 350 lines.

The shared-buffer rule survives because the decoder and corrector never hold the
buffer: they receive a fresh cursor from `reader()` per call.
`PredictionEngineConcurrencyTest` keeps guarding it.

**`KeyGestures.kt`: split, but the gate stays in one place.**

- `ime/KeyGestureRules.kt`: the pure decisions and thresholds. `LONG_PRESS_MS`,
  `GLIDE_WATCH_MS`, `GLIDE_STIR_DP`, `GLIDE_ESCAPE_DP`, `isGlideCandidate`,
  `glideEscaped`, `commitsOnPress`, `repeatIntervalAt`, `wordDeleteActivated` and the
  word-delete constants. These are what the gesture work will tune, and they already
  have tests that call them directly.
- `ime/KeyAlternates.kt`: `LongPress`, `PUNCTUATION_ALTERNATES`,
  `CURRENCY_ALTERNATES`, `DIGIT_ALTERNATES` and its default-index map, `longPressFor`,
  `alternateCellWidthPx`, `KeyAlternatesState`.
- `ime/ScrubGesture.kt`: `runScrub`, `scrubStepDp` and the scrub constants.
- `ime/KeyGestures.kt` keeps `Modifier.keyGestures` only, about 400 lines. The
  press-versus-glide-versus-hold state machine is deliberately not broken up further:
  it is one `awaitEachGesture` block whose branches share local state, and it is the
  code the gesture work will change next. Splitting it now would be churn that work
  undoes.

**`TypingKeyboardView.kt`: split along the files it already implies.**

- `ime/KeyboardKey.kt`: `KeyboardKey`, `KeyGlyphContent` and the key sizing constants.
- `ime/SuggestionStrip.kt`: `StripChip` and the strip-only constants.
- `ime/GlideTrail.kt`: `GlideTrailOverlay` and its width constants.
- The remembered handlers (`onDeleteWord`, `onScrub`, `onGlide`, `onSuggestionTap`)
  move into `KeyPressHandling.kt` as `remember...Handler` functions, beside
  `handleKeyPress`. They stay `remember`ed with the same keys, which is what
  `KeyboardRecompositionTest` measures.
- `TypingKeyboardView` keeps the layout of the board and the state wiring, about
  450 lines.

**`KeyboardRows.kt`: naming fix.** `rememberKeyPressHandler` and
`rememberKeyRevokeHandler` move to `KeyPressHandling.kt`, so the file named after the
rows holds the rows. Not one of the ten, but it is where a reader looks first and
does not find the handler.

**`TypingViewModel.kt`: small split only.** The class is one state machine over one
set of fields (`currentWord`, `generation`, `atSentenceStart`, the privacy flags).
Splitting it would mean either passing that state around or inventing a second
owner of it, and both make the privacy gates harder to audit. Only the pure
functions at the bottom move: `wordUnderCaret`, `wordDeleteLength`, `precedingWord`,
`startsNewSentence`, `doubleSpaceReplacement` and their constants go to
`ime/TextBeforeCursor.kt`. About 830 lines remain, and that is accepted.

**`StickyKeysIME.kt`: two extractions.**

- `ime/ImeViewModelFactory.kt`: the hand-written `ViewModelProvider.Factory`. This is
  the place a contributor must edit when an IME ViewModel gains a constructor
  parameter, and today it is hidden inside the service.
- `ime/StickerCommit.kt`: `commitStickerContent`, its rate limit and MIME handling.
- The Enter, cursor and selection code stays. It reads and writes the service's own
  caret mirror (`selStart`, `selEnd`, the predicted caret), and moving it out would
  mean exposing that mirror.

**`EmojiPickerView.kt`: leave.** The emoji and sticker panel is being redesigned.

**App screens: move the ViewModel out, leave the composables.** Each of
`KeyboardSettingsScreen.kt`, `StickersLibraryScreen.kt`, `LayoutEditorScreen.kt`,
`ThemeEditorScreen.kt` defines its ViewModel and UI state types in the same file.
They move to `<Name>ViewModel.kt` beside the screen. The ViewModels outlive a visual
rebuild; the composables do not, so nothing else in these files changes.

### Conventions for new code

- One file per concept a reader would search for, named after that concept, not
  after the file it was cut from. A file named after a type holds that type.
- Pure logic (no Compose, no Android) lives in its own file and gets a unit test
  that calls it directly. Thresholds and constants sit with the logic that reads them.
- A screen's ViewModel and UI state live in `<Name>ViewModel.kt`, the composables in
  `<Name>Screen.kt`.
- Every source file starts with `// Engineered by uncoalesced` (or
  `<!-- Engineered by uncoalesced -->`), once.
- A move keeps visibility as narrow as it was. `private` becomes `internal` only when
  the new file needs it, and never `public`.

### Module structure

No module moves in this round. The natural next one is a plain JVM module for
`domain/engine` (dictionary, decoder, corrector, language model) so the trace replay
and its tests run without Robolectric. It touches Gradle, Hilt bindings and the R8
keep rules at once, so it is listed for the next round as its own step rather than
mixed into these.

## Steps

Each step is one pull request, moves code without changing behaviour, and has to
pass the same gate: `clean build` green, unit test count at least 531 with zero
failures (from fresh result files), `scripts/check-source-rules.sh`, ktlint, a
release build with `verifyRoomKeepRules` and `verifyNoUsageLoggingInRelease` passing,
and the release DEX scan reporting `datatransport=0 mlkit=0 gms=0`.

| # | Step | Extra check |
|---|---|---|
| 1 | Move handler factories out of `KeyboardRows.kt` and `TypingKeyboardView.kt` into `KeyPressHandling.kt` | `KeyboardRecompositionTest` (0 key rebuilds) |
| 2 | Split `TypingKeyboardView.kt`: `KeyboardKey.kt`, `SuggestionStrip.kt`, `GlideTrail.kt` | `KeyboardRecompositionTest`, `KeyboardKeySemanticsTest`, `KeyAccessibilityTest` |
| 3 | Split `KeyGestures.kt`: `KeyGestureRules.kt`, `KeyAlternates.kt`, `ScrubGesture.kt` | `GlideGateTest`, `CommitOnPressTest`, `AlternatesDefaultTest`, `ScrubGestureTest`, `NumberRowTest`, `WordDeleteTest` |
| 4 | Extract `Flictionary.kt` from `PredictionEngine.kt` | `PredictionEngineTest`, `PredictionEngineConcurrencyTest` |
| 5 | Extract `GlideDecoder.kt`, plus one new JVM test that decodes a synthetic straight stroke over the real `base_dict.bin` through `GlideDecoder` directly | `GlideBeamTest`, `GlideStrokeTest`, `GlideSuggestionsTest` |
| 6 | Extract `SpellingCorrector.kt` | `AutoCorrectCoverageTest`, `PredictionEngineTest` |
| 7 | Move the pure helpers out of `TypingViewModel.kt` into `TextBeforeCursor.kt` | `WordContextTest`, `DoubleSpaceTest`, `PrecedingWordTest`, `WordDeleteTest` |
| 8 | Extract `ImeViewModelFactory.kt` and `StickerCommit.kt` from `StickyKeysIME.kt` | `MediaTransportTest`; release mapping still keeps the Room `_Impl` constructor |
| 9 | Move each app screen's ViewModel and UI state into `<Name>ViewModel.kt` | app unit tests; lint (`:app:lintDebug`) |
| 10 | Update `docs/ARCHITECTURE.md` with the new file map and the conventions above | none |

Steps 1 to 3 touch the keyboard UI layer, 4 to 6 the engine, 7 and 8 the IME glue,
9 the app. The riskiest Compose change (step 2) comes early, while there is time to
back it out, and nothing in the UI steps blocks the engine steps the glide replay
needs.

After step 10, the device smoke pass from before the refactor is repeated and the
results compared.

## Consequences

- The glide replay can construct the decoder from a byte buffer and a fake language
  model, with no Android in the loop.
- The gesture thresholds live in a file of their own that tests already exercise.
- `TypingViewModel` stays large. That is a choice: its size is one state machine, and
  the privacy gates are easier to verify in one place.
- `git log --follow` tracks whole-file moves only. Partial moves lose line history,
  so each PR description names the source file and line range it moved, and
  `git log -L` on the old file still finds the code's past.
- The emoji panel and the app screen composables are untouched and will be replaced
  wholesale by the redesign.
