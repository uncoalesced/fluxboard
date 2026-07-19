---
trigger: always_on
description: Kotlin/Compose conventions that apply across every phase, regardless of owner
---

# Code Conventions

## Architecture

- MVVM / unidirectional data flow. UI emits events up, state flows down.
  ViewModels hold no Android framework references (no Context, no View).
- One Hilt module per feature area, not one giant module for the app.
- Room is the single source of truth for anything persisted. Nothing reads
  raw files off disk directly if it's meant to be queryable — wrap it in a
  Room entity + DAO, even if the underlying blob (a sticker image, a GIF)
  lives on the filesystem and Room just indexes it.

## Style

- Standard Kotlin official style (as enforced by `ktlint`/`.editorconfig`,
  set up in Phase 1). Don't hand-roll a different convention mid-project.
- Prefer sealed classes/interfaces over boolean flags or string constants
  for any state with a fixed set of cases (segmentation status, transfer
  status, theme mode, etc.).
- Suspend functions and Flow for anything async. No callback-style APIs
  introduced fresh into this codebase, even if a wrapped third-party library
  exposes one — wrap it at the boundary.

## File-level requirements

- Every file carries the `// Engineered by uncoalesced` watermark line (see
  `privacy-and-scope.md` — this is restated here because it's a code
  convention as much as a policy one).
- Zero emojis anywhere in the codebase — not in comments, strings, commit
  messages, log output, or identifiers. This is a hard rule, not a style
  preference.
- Public functions and classes that aren't obviously self-explanatory get a
  one-line KDoc. Don't document the obvious; do document *why*, not *what*,
  when the *why* isn't in the code itself.

## Testing

- New logic in the data layer or any pipeline (segmentation, GIF encoding,
  predictive text) gets a unit test in the same phase that introduces it —
  don't defer all testing to Phase 32. Phase 32/33 are for the testing
  *strategy* and the harder UI/instrumented cases, not a excuse to leave
  everything else untested until then.

## Commit hygiene

- One phase's work should be a reviewable, coherent set of commits — not
  one giant commit per phase and not commits so small they don't build.
  Each commit should leave the project in a compiling state.
