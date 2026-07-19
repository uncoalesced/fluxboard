---
description: "Phase 34: Unit Testing Strategy for Core Logic"
---

# Phase 34 — Unit Testing Strategy for Core Logic

**Owner:** Joel
**Type:** Implementation
**Depends on:** Phase 10, Phase 13, Phase 19, Phase 24

## Goal

Real test coverage on the pipelines where a silent regression would be
worst: segmentation, GIF/WebP encoding, predictive text, clipboard, and the
data layer.

## Tasks

1. Define the testing strategy: what's unit-tested vs. instrumented vs.
   manual-only, and why.
2. Backfill tests for anything Phases 1–30 didn't already cover per
   `.agent/rules/code-conventions.md`'s per-phase testing rule.
3. Set a coverage floor for new code in CI.

## Definition of done

- [ ] CI enforces the agreed coverage floor on the core pipelines.
- [ ] A documented list exists of what's deliberately out of scope for
      automated testing, and why.
