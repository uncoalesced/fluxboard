---
description: "Phase 16: Minimal Sticker-Only IME Shell"
---

# Phase 16 — Minimal Sticker-Only IME Shell

**Owner:** Joel
**Type:** Implementation
**Depends on:** Phase 4, Phase 7

## Goal

Get a sticker into WhatsApp, Telegram, or any other app from *outside* that
app — which on Android means being a keyboard, even before the full typing
keyboard exists. This unlocks the app's actual value (sending a sticker
anywhere) well before Phase 17's larger typing-keyboard build is done.

## Tasks

1. `InputMethodService` scaffold, registered as a selectable keyboard in
   Android's IME list.
2. Commit Content API integration — the only Android-sanctioned way to
   send rich content directly into another app's text field (see
   `docs/repo-reference.md`).
3. A sticker grid as this minimal IME's entire UI for now.
4. Wire the grid to Phase 4's data layer and Phase 7's categories/
   favourites — not a flat unsorted list.

## Definition of done

- [ ] With this IME selected in a separate test messaging app, tapping a
      sticker delivers it into that app's text field successfully.
