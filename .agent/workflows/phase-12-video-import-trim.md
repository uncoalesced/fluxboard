---
description: "Phase 12: Video Import & Trim UI"
---

# Phase 12 — Video Import & Trim UI

**Owner:** Rahul
**Type:** Implementation (light — picker + scrubber UI, no encoding logic)
**Depends on:** Phase 2

## Goal

Let a user pick a video and select the exact sub-range they want turned
into a GIF/WebP, before any conversion happens.

## Tasks

1. Video picker (Photo Picker's video support, or a dedicated video
   picker).
2. Scrubber/trim UI with start/end handles and a live preview frame.
3. Enforce a duration cap on the selectable range — a 10-second ceiling is
   a reasonable default even outside WhatsApp's specific animated-sticker
   limit (see Phase 15), since it keeps output size manageable regardless
   of export target.

## Definition of done

- [ ] A user selects a video, trims to a sub-range, and only that range —
      not the whole video — is what gets handed to Phase 13.
