---
description: "Phase 33: Accessibility & Localization Pass"
---

# Phase 33 — Accessibility & Localization Pass

**Owner:** Rahul
**Type:** Implementation (light — checklist-driven)
**Depends on:** Phase 21, Phase 23, Phase 26

## Goal

TalkBack support, adequate contrast, and string-extraction/RTL readiness —
this app is unusually icon-heavy (a whole keyboard's worth of controls), so
this matters more here than in a typical app.

## Tasks

1. Content descriptions on all interactive elements, especially the
   keyboard.
2. Contrast check on default and any bundled theme presets (Phases 21/23).
3. Extract all user-facing strings to resource files — nothing hardcoded.
4. Test at least one RTL locale's layout mirroring.

## Definition of done

- [ ] TalkBack can navigate the core flows (create a sticker, switch
      keyboards, change a setting) without dead ends.
- [ ] Default themes pass a standard contrast check.
- [ ] Strings are fully externalized.
