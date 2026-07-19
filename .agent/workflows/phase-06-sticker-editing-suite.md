---
description: "Phase 6: Sticker Editing Suite"
---

# Phase 6 — Sticker Editing Suite

**Owner:** Joel
**Type:** Implementation
**Depends on:** Phase 5

## Goal

Let existing stickers be re-opened and edited instead of recreated from
scratch — this is a core, explicitly stated product requirement, not a
nice-to-have.

## Tasks

1. Re-open a saved sticker into the same tool surface as Phase 5 (crop,
   erase, redo background).
2. Basic filters (brightness/contrast/saturation).
3. Text overlay (font, size, color — pulling from Phase 3's tokens).
4. Decide and implement: non-destructive edit history, or a simpler
   explicit "save as new / overwrite" choice, if history is out of scope
   for v1.

## Definition of done

- [ ] A sticker's pixels can change after creation without deleting and
      recreating its library entry — same sticker ID, updated file.
- [ ] The overwrite-vs-history decision is documented, not left implicit.
