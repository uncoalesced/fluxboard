<!-- Engineered by uncoalesced -->

# Numeric keypad: investigation and plan

Planning only. No code was written. No device was used.

## 1. Investigation result

**FluxBoard has no numeric mode. Nothing branches layout on field type.**

`inputType` appears exactly once in the whole codebase:
`StickyKeysIME.kt:441-446`, inside `sendEnter()`, testing `TYPE_CLASS_TEXT` and
the two multi-line flags so Enter can decide between a newline and the field's
action. That is its only use.

- `KeyboardMode` = `LETTERS_LOWER`, `LETTERS_UPPER`, `LETTERS_CAPS_LOCK`,
  `SYMBOLS`, `SYMBOLS_SHIFTED`. No numeric member.
- `AppMode` = `TYPING`, `EMOJI_PICKER`, `STICKERS`, `CLIPBOARD`, `TEXT_EDIT`.
  No numeric member.
- `onStartInputView` reads `initialCapsMode`, the initial selection and
  `imeOptions` (for incognito). It does not read `inputType` at all.

So a PIN field, an OTP field, a phone field and a payment amount field all get
the full alphabetic keyboard today. **This is a missing feature, not a styling
problem**, and there is no existing visual treatment to restyle.

## 2. What the reference actually is

Reference grid: `1 2 3 −` / `4 5 6 space` / `7 8 9 backspace` /
`, 0 . check`.

**It is not the PIN keypad, and the space key is the tell.**

| Field class | Expected keys | Matches reference |
|---|---|---|
| `TYPE_CLASS_NUMBER \| TYPE_NUMBER_VARIATION_PASSWORD` | digits only, no separators, no space | no |
| `TYPE_CLASS_PHONE` | digits plus `+ * #` and pause/wait | no |
| `TYPE_CLASS_DATETIME` | digits plus `/ : -` | no |
| `TYPE_CLASS_NUMBER`, with `TYPE_NUMBER_FLAG_DECIMAL` and/or `TYPE_NUMBER_FLAG_SIGNED` | digits, `.`, `,`, `-`, space | **yes** |

Two details confirm it:

- **Both `,` and `.` are present.** An IME cannot know whether a given numeric
  field treats comma or period as its decimal separator, so the general number
  layout offers both. A layout that had resolved the separator would show one.
- **Space is legitimate here.** Number fields routinely accept grouped input --
  a card number entered as `4111 1111 1111 1111` -- and payment card fields
  commonly declare `TYPE_CLASS_NUMBER`. It would be wrong on a PIN and is not
  wrong here.

**Needs a device to settle:** which exact flag combination the screenshotted
field declared. The general number layout varies little with `DECIMAL` and
`SIGNED`, so only dumping the real `EditorInfo` while that field is focused
answers it. Until then, "a `TYPE_CLASS_NUMBER` field" is as precise as this can
honestly be.

## 3. The locale question has a trap in it

The brief asks that decimal and grouping separators respect the field's locale
conventions. Implemented naively, that **breaks input**:

- `TYPE_NUMBER_FLAG_DECIMAL` fields are typically backed by `DigitsKeyListener`,
  which accepts only `.` regardless of locale.
- `DigitsKeyListener.getInstance(Locale)` (API 26+) does accept the locale's
  separator, but the host app has to have opted into it, and most have not.
- **The IME cannot tell which of the two it is facing.** Nothing in `EditorInfo`
  reports the field's accepted character set.

So resolving the separator from the user's locale and showing only that key
produces, in a large share of apps, a key the field silently swallows. The
reference's answer -- show both -- is the correct hedge rather than an oversight,
and this plan recommends matching it.

Safe refinement: order the two by `DecimalFormatSymbols.getInstance().decimalSeparator`
so the locally conventional one sits where the thumb expects it. That is
cosmetic and cannot break input.

## 4. Proposed shape

The important claim: **the keypad is a layout, not a renderer.** Everything the
brief asks for visually already exists and should be reused rather than
reproduced.

- `KeyboardRowsView` already renders any `KeyboardRows`. A 4x4 grid is one.
- `KeyboardKey` already supplies the corner radius (`KEY_CORNER_RADIUS = 6.dp`),
  the 2dp inter-key padding, the press blend (`PRESS_BLEND = 0.3f`), the colour
  transition (`KEY_COLOR_ANIM_MS`), haze, border, and the semantics that make a
  key reachable in TalkBack.
- `resolveKeyColors()` already tints `weight > 1f` keys with `surfaceVariant`
  rather than `surface`, which is exactly the darker right-hand column the
  reference draws -- for free, if those keys carry a weight above 1.
- `KeyStyle`, themes and haptics are inherited by construction.

Consequence: the reference's near-pill radius is **not** something to decide
about. Reusing `KeyboardKey` yields FluxBoard's 6dp automatically, which is what
the brief asks for. No new visual constant is needed anywhere.

New code required is small and structural rather than visual:

1. A pure `fieldKindFor(inputType: Int): FieldKind` classifier. Pure and
   unit-tested on purpose -- `EditorInfo` constants are exactly the class of
   thing that fails silently, and this file already carries the scar of
   `IME_ACTION_UNSPECIFIED` being 0 while `IME_ACTION_NONE` is 1.
2. `StickyKeysIME.onStartInputView` publishes the field kind; `TypingViewModel`
   exposes it as a `StateFlow` in the same shape as `shouldAutoCapitalize`.
3. `KeyboardMode.NUMERIC`, and numeric rows in `KeyboardLayouts`.
4. One new key token for the done key, plus a checkmark vector. **The only new
   asset in the whole change.**

### The done key

`KeyboardController.handleEditorAction()` already exists and is already
documented as precisely this case: it runs the field's declared action while
ignoring multi-line and the no-action flag, deliberately separate from
`sendEnter()` because "a Send/Search affordance is an explicit request for the
action itself". The checkmark maps straight onto it.

`handleKeyPress` has no branch for it yet -- today only `"ENTER"` is handled, and
it routes to `sendEnter()`. A new `"ACTION"` token is needed, and that is the
whole of it. **No second implementation of the action logic.**

Known behaviour to accept: in a numeric field declaring `IME_ACTION_NONE` or
`IME_ACTION_UNSPECIFIED`, `handleEditorAction` falls back to a raw Enter, which
a single-line number field will usually ignore. That matches what the Enter key
already does everywhere else and is not worth a special case.

### The one real conflict

`TypingKeyboardView` has `LaunchedEffect(inputSession)`, which forces the mode to
`LETTERS_UPPER` or `LETTERS_LOWER` on every new field, and a second
`LaunchedEffect(shouldAutoCapitalize)` that does the same on autocap changes.
Both would immediately overwrite a numeric mode the moment a numeric field was
focused.

This is the part most likely to produce a keypad that appears to work in a
screenshot and flips back to QWERTY in the hand, so it needs the reset path to
consult the field kind rather than a fix bolted on beside it.

### Invariants that must not be broken

- **Panel height.** Every mode uses `rememberImePanelHeight()`. A four-row grid
  at the same height simply gives taller keys, which is what the reference shows.
  Do not add a per-mode height or a `fillMaxSize()`.
- **The suggestion strip stays present.** Word suggestions for digits are noise,
  but removing the strip changes the window height and shoves the host app's
  content around. Suppress the suggestions, keep the strip.
- **Recomposition.** The `keyRows` `remember` gains `fieldKind` as a key. The
  hoisted key-press handler and the `KeyboardRows` wrapper must stay as they are.

## 5. Decisions needed before implementation

1. **How many numeric layouts?** Recommendation: one `TYPE_CLASS_NUMBER` layout
   now; treat PIN (`VARIATION_PASSWORD`) as the same grid with separators and
   space hidden, since offering a space on a PIN invites input the field will
   reject; defer `TYPE_CLASS_PHONE` and `TYPE_CLASS_DATETIME` to their own item.
2. **Dead keys or hidden keys?** A field without `TYPE_NUMBER_FLAG_DECIMAL`
   rejects `.`, and without `TYPE_NUMBER_FLAG_SIGNED` rejects `-`. The reference
   shows them regardless. Hiding them is more honest; showing them is simpler and
   matches the reference. Recommendation: show them, because the flags are widely
   under-declared by apps and hiding a key the field would actually have accepted
   is the worse failure.
3. **Does the quick-access toolbar appear on the numeric pad?** The reference has
   a toolbar. FluxBoard's is a disclosure row that grows the window upward.
   Recommendation: keep it, unchanged.
4. **Is this v0.1.5 or later?** It is comparable in size to the symbol-page data
   model change, not a point-release afterthought.

## 6. What cannot be claimed without a device

- The reference field's actual `inputType`.
- Whether real payment apps on the target devices declare `TYPE_CLASS_NUMBER` or
  `TYPE_CLASS_PHONE` for card entry.
- Whether the done key does anything useful in any given field.
- Key proportions and thumb reach at the shared panel height.
