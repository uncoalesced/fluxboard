# AGENTS.md — Project Context

This file is read first, before any workflow. It defines what this project is,
the constraints that apply to every phase, and how work is split between the
two people on this project. Do not violate anything in this file even if a
later, more specific instruction seems to conflict with it — flag the
conflict instead of silently picking one.

## What this app is

An Android app that brings an iOS-level sticker/emoji experience to Android —
more customizable and more private than any existing option. It is fully
open source, has zero telemetry, is completely free, and is built so anyone
can fork, fix, break, or extend it. The **flagship feature is the keyboard**:
a fully customizable IME with an accurate, on-device, self-learning
predictive text bar, toggles for auto-capitalize and auto-correct, a
persistent clipboard history, and haptic feedback throughout. Stickers,
extraction, GIF conversion, and cross-device sharing are the supporting
feature set around that core.

## Hard constraints (apply to every phase, no exceptions)

- **The Android app itself is Kotlin.** No Java, no cross-platform
  framework, inside the app module. Python or JS are fine for anything
  that isn't the Android app — the link-sharing relay (Phase 29/30) and
  offline tooling like the dictionary-preprocessing pipeline (Phase 18)
  are reasonable places for either. Don't pull a second language into the
  app module itself without a specific reason, and never via something
  like Chaquopy just to run Python on-device — that costs size budget for
  no benefit here.
- **Min SDK 26 (Android 8.0)**, target latest stable.
- **Total installed app size must stay under 100 MB.** Before adding any
  dependency, model, or asset bundle, check its size impact against this
  budget. Prefer APIs whose payload is delivered separately by the OS/Play
  services over bundling equivalents directly in the APK — but see the
  privacy note below before assuming that's free.
- **Zero telemetry, ever.** No analytics SDKs, no crash reporters that phone
  home by default, no third-party ad or tracking libraries. If a library
  under consideration bundles any of these, either strip it at build time or
  reject it.
- **Google Play Services dependency is a judgment call, not a default.** Some
  of the best available APIs (e.g. ML Kit) only work with Play Services
  present, which breaks on de-Googled devices (GrapheneOS, LineageOS without
  gapps) that this project's audience overlaps with. Where a fully-open,
  self-contained alternative exists at reasonable engineering cost, prefer
  it. Where it doesn't, use the Play Services–dependent option but say so
  explicitly in that phase's output, so it's a visible tradeoff, not a
  silent one.
- **Every source file this project produces carries the line
  `// Engineered by uncoalesced`** (or the idiomatic equivalent for the file
  type — e.g. `<!-- Engineered by uncoalesced -->` in XML) as a provenance
  watermark. Present once per file, not repeated per function.
- **Model-agnostic instructions.** Nothing in this project's rules or
  workflows should assume a specific coding model or vendor. Write what
  needs to be built, not who or what builds it.

## Tech stack

- **Architecture:** MVVM / unidirectional data flow.
- **UI:** Jetpack Compose.
- **DI:** Hilt.
- **Networking:** Retrofit (used narrowly — this app is local-first; network
  calls exist only for the ephemeral link-sharing relay described in
  Phase 27).
- **Concurrency:** Kotlin Coroutines / Flow.
- **Persistence:** Room.

This mirrors the stack already used on this team's other Android projects,
kept consistent on purpose so both contributors' prior experience transfers
directly.

## Who's building this

Two people, working roughly a 60/40 split by effort (not by phase count —
see `docs/repo-reference.md` intro and the work-allocation document for why
those aren't the same number here). Every phase file below states an owner.
Owners are named by person, never by which AI model or tool executed the
work — describe the system component, not the executor.

- **Joel** — the core engineering: architecture, data layer, the
  segmentation/extraction pipeline, the GIF/WebP pipeline, the full keyboard
  and its theming/prediction/clipboard engines, device pairing, transfer,
  and sharing. Joel is fluent in Kotlin.
- **Rahul** — mostly research phases (library/format evaluation, licensing
  checks, compatibility research, privacy/accessibility audits, packaging
  and documentation), plus a smaller number of lower-risk implementation
  phases (list/filter UI over an existing data layer, settings screens,
  haptics wiring, test-writing). Rahul is newer to Kotlin, and is
  explicitly allowed to use AI assistance on his phases — see the note at
  the top of the work-allocation document.

## How this project is organized

- `.agent/rules/` — standing constraints, auto-loaded every session
  (privacy/scope rules, code conventions). These apply regardless of which
  phase is active.
- `.agent/workflows/` — one file per build phase (`phase-01-...` through
  `phase-37-...`), meant to be worked roughly in numeric order since later
  phases depend on earlier ones. Each is self-contained: goal, owner,
  dependencies, reference repos/libraries, concrete tasks, and a definition
  of done.
- `docs/repo-reference.md` — the full list of existing open-source repos,
  libraries, and prior art worth cloning, forking, or studying, organized by
  subsystem. Read the relevant section before starting a phase in that area
  — several phases assume you've seen it.
- `docs/agent-prompts.md` — the exact prompts to type into Antigravity to
  run every phase in order, with a one-line explanation and dependency note
  for each. Start there, not here, if the question is "what do I actually
  type."

## Sequencing note

Phases are numbered in a reasonable dependency order, but "at once" doesn't
mean "in any order" — Phase 17 (full keyboard core) can't start before
Phase 2 (architecture foundation), and Phase 19 (predictive engine
implementation) can't start before Phase 18 (predictive engine research).
Where two phases have no dependency on each other, they can run in parallel
across both contributors.
