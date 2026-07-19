---
description: "Phase 32: App Size Budget Tracking & Optimization"
---

# Phase 32 — App Size Budget Tracking & Optimization

**Owner:** Joel
**Type:** Implementation
**Depends on:** Phase 10, Phase 19 (needs the segmentation model and
dictionary data in place to size them)

## Goal

Keep the whole thing under 100 MB installed, actively tracked, not an
afterthought at the end.

## Tasks

1. Enable R8/ProGuard with resource shrinking.
2. Track APK size per build in CI; flag (or fail) the build if it crosses
   a defined threshold.
3. Audit whichever segmentation model (Phase 8/10) and dictionary data
   (Phase 18/19) ended up bundled vs. delivered separately via Play
   services, and confirm the actual measured size, not the estimate from
   `docs/repo-reference.md`.
4. Trim unused resources/languages from any bundled library.

## Definition of done

- [ ] A release build is under 100 MB.
- [ ] The size breakdown (what's taking the space) is documented, so
      future growth can be tracked against budget rather than discovered
      too late.
