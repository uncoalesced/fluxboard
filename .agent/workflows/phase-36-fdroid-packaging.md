---
description: "Phase 36: F-Droid / Open-Source Distribution Packaging"
---

# Phase 36 — F-Droid / Open-Source Distribution Packaging

**Owner:** Rahul
**Type:** Research + light implementation
**Depends on:** Phase 32

## Goal

Get this shippable through F-Droid (or an equivalent reproducible,
non-Play channel), matching how FlorisBoard and EweSticker are already
distributed.

## Tasks

1. Verify the build is reproducible from a clean checkout.
2. Prepare F-Droid metadata: description, categories, anti-features
   declaration if any dependency requires one.
3. Confirm every bundled dependency's license is compatible with
   F-Droid's inclusion criteria (re-check licenses at this point — don't
   rely solely on `docs/repo-reference.md`, which may be out of date by
   now).
4. Decide whether a Play Store listing happens in addition, and note what
   that would require differently (Play's own review of an IME app and its
   permissions is a distinct process from F-Droid's).

## Definition of done

- [ ] An F-Droid-ready metadata set exists.
- [ ] A build is verified reproducible from a clean checkout.
