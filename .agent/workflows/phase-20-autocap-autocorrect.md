---
description: "Phase 20: Auto-Capitalize & Auto-Correct Logic + Toggles"
---

# Phase 20 — Auto-Capitalize & Auto-Correct Logic + Toggles

**Owner:** Joel
**Type:** Implementation
**Depends on:** Phase 19

## Goal

The two explicitly-requested keyboard behaviors, each independently
toggleable by the user.

## Tasks

1. Auto-capitalize logic: start of sentence, start of a text field,
   respecting proper-noun exceptions where the Phase 18/19 dictionary
   supports that distinction.
2. Auto-correct logic: edit-distance-based correction against the same
   dictionary, with an easy, immediate one-tap undo — unwanted
   autocorrects are the single most-hated keyboard failure mode, so the
   undo path matters as much as the correction itself.
3. Wire both to independent settings toggles (surfaced in Phase 24) that
   take effect immediately, without restarting the keyboard.

## Definition of done

- [ ] Auto-capitalize and auto-correct can each be switched on/off
      independently from settings.
- [ ] Toggling either takes effect on the very next keystroke, no restart
      required.
- [ ] A bad autocorrect can be undone in one tap.
