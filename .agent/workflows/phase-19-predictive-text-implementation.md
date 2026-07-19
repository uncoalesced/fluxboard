---
description: "Phase 19: Predictive Text Engine Implementation"
---

# Phase 19 — Predictive Text Engine Implementation

**Owner:** Joel
**Type:** Implementation
**Depends on:** Phase 17, Phase 18

## Goal

Build the predictive text bar per Phase 18's recommendation: word
suggestions that get more accurate to this specific user over time. This
is the single most important phase in the whole plan — it's the feature
the flagship claim rests on.

## Tasks

1. Integrate or adapt the chosen n-gram + dictionary approach from
   Phase 18 into the keyboard's suggestion strip.
2. Implement the "learns over time" half explicitly: a per-user layer that
   boosts words/phrases the user actually types, stored locally only,
   never uploaded anywhere (this is a hard privacy requirement, not just a
   nice property).
3. Decide on decay/weighting for that personal layer so it adapts without
   going stale or overfitting to a single typo or one-off word.
4. Be realistic about scope: this should target the accuracy level of a
   solid classic predictive keyboard (old SwiftKey/Gboard-era, n-gram plus
   personalization), not a from-scratch neural language model — that's a
   different, much larger project.

## Definition of done

- [ ] After a representative typing session, words the user typed
      repeatedly rank measurably higher in suggestions than they did at
      first use — demonstrate with an explicit before/after comparison,
      not just "it compiles and shows some suggestions."
- [ ] No personalization data leaves the device.
