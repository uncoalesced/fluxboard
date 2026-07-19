---
description: "Phase 22: Keyboard Layout Customization Engine"
---

# Phase 22 — Keyboard Layout Customization Engine

**Owner:** Joel
**Type:** Implementation
**Depends on:** Phase 17

## Goal

Custom layouts and key remapping/repositioning — a separate concern from
Phase 21's visual theming.

## Tasks

1. A layout data model distinct from the theme model: key positions,
   sizes, and which keys exist where.
2. A layout editor UI: drag to reposition/resize keys, remap a key's
   output.
3. Persistence and switching between multiple saved layouts.
4. Validation so a user can't save a broken layout (e.g. no space key, a
   key positioned off-screen).

## Definition of done

- [ ] A user can move or resize at least one key from the default layout,
      and that change persists and applies live.
- [ ] Invalid layouts (per the validation rules) can't be saved.
