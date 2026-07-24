---
description: "Phase 8: Segmentation Approach Research & Library Evaluation"
---

# Phase 8 — Segmentation Approach Research & Library Evaluation

**Owner:** Rahul
**Type:** Research
**Depends on:** None (can run in parallel with early phases)

## Goal

Decide, with evidence rather than a guess, between ML Kit Subject
Segmentation and U2NetP (see `docs/repo-reference.md`) before Joel builds
the integration in Phase 10.

## Tasks

1. Prototype both against a shared set of ~20 test images with varied
   subjects and backgrounds. **Don't ask which specific images to use —
   self-source or generate a reasonable spread (roughly 5 people, 5 pets,
   5 single objects, 5 busy/complex backgrounds) and just document what
   was used**, so the comparison is reproducible later even though the
   exact image choice isn't a decision that needs Joel's input.
2. Measure: latency, output quality (edge cleanliness, fine-detail/hair
   handling), on-device size impact.
3. Re-confirm U2NetP's current license terms directly (don't just trust
   `docs/repo-reference.md` — verify at the time of this research, since
   licenses and repo states can change).
4. Write up a recommendation that states the tradeoff explicitly: ML Kit's
   Play Services dependency vs. U2NetP's extra integration work.

## Definition of done

- [ ] A written recommendation, with sample output images from both
      candidates, handed to Joel before Phase 10 begins.
