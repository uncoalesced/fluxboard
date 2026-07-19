---
description: "Phase 24: Clipboard History Manager"
---

# Phase 24 — Clipboard History Manager

**Owner:** Joel
**Type:** Implementation
**Depends on:** Phase 4, Phase 17

## Goal

A full clipboard history within the keyboard: every copy is kept until the
user explicitly clears it, and history survives app/keyboard restarts. This
is a deliberate product decision (no silent auto-expiry), not a default to
relax for convenience.

## Tasks

1. Listen for clipboard changes via Android's `ClipboardManager` while the
   keyboard (or app) is active.
2. Persist each clipboard entry using the same Room-backed pattern as
   Phase 4 — a `ClipboardEntry` table, not an in-memory list, so it
   survives restarts.
3. A clipboard tab/tray within the keyboard (alongside the sticker tray
   from Phase 16), with tap-to-paste.
4. Pin/delete-one actions, plus a single explicit "clear all" action —
   this is the *only* way history should ever be removed.
5. Respect `ClipDescription.EXTRA_IS_SENSITIVE` and any OS-level
   sensitive-content detection when deciding whether to persist a given
   copy — see `.agent/rules/privacy-and-scope.md`. This governs what gets
   written to history, not when it's removed, so it doesn't conflict with
   "cleared only by the user."
6. Keep the store in the app's private storage; no cloud sync, no
   inclusion in the migration/sharing features (Phases 27–30) unless a
   later phase explicitly decides otherwise.

## Definition of done

- [ ] Copying text anywhere on the device adds it to a visible, persistent
      history inside the keyboard.
- [ ] History survives a full app/device restart.
- [ ] History is only ever removed by an explicit user action (clear-all
      or delete-one) — never by time or count-based eviction.
- [ ] Content flagged sensitive by the OS is not written to persistent
      history.
