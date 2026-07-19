---
description: "Phase 15: Sticker/GIF Platform-Compatibility Research"
---

# Phase 15 — Sticker/GIF Platform-Compatibility Research

**Owner:** Rahul
**Type:** Research
**Depends on:** None (can run in parallel with Phases 12–14)

## Goal

Pin down the exact format/size requirements for every platform this app
should export to or integrate with, before Phase 14 optimizes against a
guess.

## Tasks

1. Confirm WhatsApp's current requirements directly against the
   `WhatsApp/stickers` repo (512×512 px, WebP, ≤100 KB static / ≤500 KB
   animated, 3–30 stickers per pack) — verify this hasn't changed since
   `docs/repo-reference.md` was written.
2. Research Telegram's sticker/Bot API requirements (static WebP and the
   Lottie-based `.tgs` animated format) — not pinned down in the repo
   reference doc; this phase is where that gets resolved.
3. Research Signal's sticker-pack manifest format — likewise not yet
   pinned down.
4. Write up a compatibility table: platform → format → size limit → count
   limit → integration method (content-provider vs. generic share/Commit
   Content).

## Definition of done

- [ ] A compatibility table exists and is referenced by Phase 14's size
      targets and by whatever export flow ships.
