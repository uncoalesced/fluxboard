---
description: "Phase 35: UI/Instrumented Testing for Sticker & Keyboard Flows"
---

# Phase 35 — UI/Instrumented Testing for Sticker & Keyboard Flows

**Owner:** Rahul
**Type:** Implementation (light) + research (test-case design)
**Depends on:** Phase 34

## Goal

Cover the flows a unit test can't reach — actual IME behavior, actual
Compose screens.

## Tasks

1. Instrumented tests for: sticker creation through to appearing in the
   library; keyboard-switch through typing, prediction, and commit;
   settings-toggle through to observable behavior change.
2. Write these as real test cases even where full automation is hard, so
   there's at least a documented manual test script where an instrumented
   test isn't practical.

## Definition of done

- [ ] Each core user journey has either a passing instrumented test or a
      documented manual test script, executed and signed off before
      release.
