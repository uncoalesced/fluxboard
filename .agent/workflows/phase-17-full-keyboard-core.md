---
description: "Phase 17: Full Typing Keyboard Core"
---

# Phase 17 — Full Typing Keyboard Core

**Owner:** Joel
**Type:** Implementation
**Depends on:** Phase 16

## Goal

Build the actual typing keyboard — this is the flagship feature of the
whole app — as a superset of the sticker-only shell from Phase 16, not a
separate keyboard the user has to switch between.

## Tasks

1. Key layout rendering and input event handling.
2. Text commit logic; at minimum a QWERTY Latin layout with shift/caps/
   symbols states.
3. **Language scope for v1: English/Latin only, by default.** Additional
   language layouts (e.g. Hindi, Kannada) are a real, reasonable ask given
   where this project is based, but each one means redoing the Phase 18/19
   dictionary and prediction work per language — flag this back to Joel as
   an explicit scope question rather than silently building multi-language
   support or silently assuming it's out of scope.
4. Correct subtype registration so Android's keyboard switcher and
   language settings show this keyboard properly.
5. Fold Phase 16's sticker tray in as a mode/tab within this keyboard,
   reachable without switching to a different IME.
6. Study FlorisBoard's `InputMethodService` implementation as reference
   (see `docs/repo-reference.md`) — no need to reinvent input-handling
   edge cases (password fields, URL fields, etc.) that a mature open
   project has already solved.

## Definition of done

- [ ] This keyboard can fully replace a user's daily typing keyboard for
      basic typing.
- [ ] The sticker tray from Phase 16 is reachable from within it, not as a
      separate keyboard entry.
