---
description: "Phase 26: Keyboard & App Settings UI"
---

# Phase 26 — Keyboard & App Settings UI

**Owner:** Rahul
**Type:** Implementation (light — UI over existing ViewModels/repositories)
**Depends on:** Phase 20, Phase 21, Phase 22, Phase 23, Phase 24, Phase 25

## Goal

Surface every toggle and customization option from Phases 20–25 in one
coherent settings UI, plus app-level settings — this is UI wiring over
logic that already exists, not new logic.

## Tasks

1. Settings screens wired to the ViewModels/repositories the earlier
   phases already built.
2. Auto-capitalize / auto-correct toggles (Phase 20).
3. Theme, layout, and background pickers (Phases 21–23).
4. Clipboard settings: the explicit clear-all action from Phase 24 (and
   confirm it's not reachable by accident — a confirmation step is
   appropriate given it's irreversible).
5. Haptics on/off and intensity control (Phase 25).
6. App-level settings: default export format (Phase 13/14), category
   management entry point (Phase 7).

## Definition of done

- [ ] Every setting introduced in earlier phases is reachable and
      functional from a single settings entry point.
- [ ] No feature is only reachable via debug code or a developer menu.
- [ ] Clearing clipboard history requires an explicit confirmation step.
