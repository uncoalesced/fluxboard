---
description: "Phase 5: Manual Sticker Creation Flow"
---

# Phase 5 — Manual Sticker Creation Flow

**Owner:** Joel
**Type:** Implementation
**Depends on:** Phase 4

## Goal

Let a user create a sticker from an existing image using manual tools,
independent of the automatic segmentation pipeline (Phases 8–10) — this
should work even before segmentation exists.

## Tasks

1. Image picker entry point into the creation flow.
2. Crop tool.
3. Manual background erase (brush/lasso eraser) as a fallback to
   auto-segmentation, and as the only option until Phase 10 lands.
4. Save-to-library, wired to Phase 4's repository.
5. Assign a pack/category at save time (light integration point with
   Phase 7 once it exists).

## Definition of done

- [ ] A user can go from "pick an image" to "sticker saved and visible in
      the library" using only manual tools.
- [ ] This flow has zero dependency on the segmentation phases.
