---
description: "Phase 10: On-Device Segmentation Integration & Touch-Up UI"
---

# Phase 10 — On-Device Segmentation Integration & Touch-Up UI

**Owner:** Joel
**Type:** Implementation
**Depends on:** Phase 8, Phase 9

## Goal

Implement whichever approach Phase 8 recommended, plus a manual touch-up
step — automatic cutouts are never perfect, and the product needs a way to
fix them without falling back to fully manual erasing.

## Tasks

1. Integrate the chosen segmentation library behind an interface, so the
   other candidate could be swapped in later without touching call sites.
2. Run segmentation automatically on capture/import.
3. Present the auto-cutout with a touch-up UI (brush add/remove from the
   mask) before committing it as a sticker.
4. Save the approved result via Phase 4/5's existing save pipeline — don't
   build a second save path.
5. If the chosen approach depends on Google Play Services, say so visibly
   in this phase's output per `.agent/rules/privacy-and-scope.md`.

## Definition of done

- [ ] An image goes from capture to a segmented, user-approved sticker in
      the library.
- [ ] The touch-up step demonstrably fixes a deliberately-bad auto-cutout
      in a test case.
