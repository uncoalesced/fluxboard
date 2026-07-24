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

## Dependency and tooling versions

Phase 1 already caught one real instance of this: a proposed AGP version
that was a full major version behind actual current stable, plus a
targetSdk one version behind. Treat that as the standing pattern, not a
one-off — whichever model is doing this work has a training cutoff, and
specific version numbers for fast-moving tooling (AGP, Gradle, Compose BOM,
Hilt, Room, Retrofit, Kotlin itself, any library in
`docs/repo-reference.md`) are exactly the kind of fact that goes stale
fastest. Before pinning a version in a build file:

- Check the actual current stable release (via whatever lookup the agent
  has available) rather than stating a remembered number with confidence.
- If a version can't be verified, say so explicitly and propose the
  most-recently-known one as a checked assumption, not a fact.
- Don't silently downgrade to an older, more "familiar" version to avoid
  this check.

## Substantive completion, not surface-plausible completion

A real incident from this project: a class named `MlKitSegmentationEngine`
was written with no ML Kit dependency at all — it just cropped a fixed
circle in the center of every image and called it done. The file name,
the class name, and the surrounding code all *looked* like the real
feature. It wasn't.

A phase is done when it does what its Definition of Done says, using the
specific approach or library actually named for it — not a simpler
stand-in that keeps the original name. If implementing the named
approach turns out to be harder than expected, say so explicitly and
flag it for a decision, rather than silently substituting something
easier while leaving the phase looking complete. This applies with extra
force to whichever phase in a given batch is the hardest or most novel —
that's exactly the one most likely to get quietly faked under time
pressure, and the one a reviewer is most likely to under-scrutinize
because everything around it looks fine.

Concretely, before marking a phase done: if the phase or its
`docs/repo-reference.md` entry names a specific library or API, confirm
the actual dependency is declared (in the relevant `build.gradle.kts`)
and actually invoked — not just that a similarly-named file or class
exists.

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
