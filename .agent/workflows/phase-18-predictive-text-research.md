---
description: "Phase 18: Predictive Text Engine Research & Dictionary Pipeline"
---

# Phase 18 — Predictive Text Engine Research & Dictionary Pipeline

**Owner:** Rahul
**Type:** Research
**Depends on:** None (can start alongside Phase 17)

## Goal

This is the flagship feature's hardest part — "very accurate, learns over
time" prediction — and it deserves a real evaluation before Joel implements
it, not a guess. Get this decision right before Phase 19 starts building.

## Tasks

1. Study FlorisBoard's NLP core (`FlorisLanguageModel`) and its
   "Flictionary" binary dictionary format in depth (see
   `docs/repo-reference.md`) — this is the single most relevant prior art
   available for this feature.
2. Confirm FlorisBoard's current Apache-2.0 licensing terms specifically
   for the scope of adaptation being considered here (don't assume the
   license from the repo-reference doc is still accurate — re-check).
3. Source or build a base word-frequency corpus for the initial dictionary
   (English at minimum for v1 — see the language-scope note in Phase 17).
   **Don't ask which corpus to use — pick a clearly-licensed public source
   (e.g. a frequency list derived from an open corpus or dictionary
   project) and document exactly which one and its license**, rather than
   stalling on the choice. FlorisBoard's own `dictionary-tools` pipeline
   for this is Python — that's fine to use as-is, since it's offline
   preprocessing that produces a static binary asset for the APK, not code
   that runs on-device (see the language note in `AGENTS.md`).
4. Document FlorisBoard's own known pitfall: their first NLP attempt was
   pure Kotlin/JVM and hit real performance and memory problems before
   being reworked. Understand what changed and why, so it isn't repeated.
5. Produce a recommendation: adapt FlorisBoard's NLP core and dictionary
   pipeline directly, reimplement the same n-gram/Flictionary-style
   approach independently, or a different approach if this research
   surfaces one.

## Definition of done

- [ ] A written recommendation, with the licensing question explicitly
      answered (not assumed), handed to Joel before Phase 19 starts.
- [ ] A usable base dictionary/corpus exists for Phase 19 to build against.
