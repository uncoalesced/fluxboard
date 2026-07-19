---
description: "Phase 7: Sticker Organization — Categories & Favourites"
---

# Phase 7 — Sticker Organization: Categories & Favourites

**Owner:** Rahul
**Type:** Implementation (light — UI over Phase 4's existing data layer)
**Depends on:** Phase 4

## Goal

Users need to sort their library by category and have a dedicated
favourites tab — this is UI and wiring on top of a data layer Phase 4
already built, not new data-layer work.

## Tasks

1. Category CRUD UI: create, rename, delete, reorder.
2. Assign-to-category flow directly from the library grid.
3. A Favourites tab as a first-class destination (not a filter buried in a
   menu).
4. A clear favourite/unfavourite gesture (long-press or a visible star) on
   any sticker or pack.

## Definition of done

- [ ] A sticker can belong to a category and be favourited independently
      of that.
- [ ] The Favourites tab reflects changes immediately via Phase 4's `Flow`
      queries — no manual refresh needed.
