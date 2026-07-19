---
description: "Phase 14: GIF/WebP Size Optimization Pass"
---

# Phase 14 — GIF/WebP Size Optimization Pass

**Owner:** Joel
**Type:** Implementation
**Depends on:** Phase 13, Phase 15

## Goal

Get output file sizes down to something reasonable to store and share,
without visibly trashing quality — and against real target numbers, not a
guess.

## Tasks

1. Palette reduction / color quantization tuning.
2. Frame-skipping for near-duplicate consecutive frames.
3. A resolution ceiling appropriate for sticker-sized output.
4. Expose a quality/size tradeoff control to the user for larger exports.
5. Validate against the size targets Phase 15 documents per platform
   (e.g. WhatsApp's ≤500 KB animated-sticker limit).

## Definition of done

- [ ] A representative 5-second clip produces output under the relevant
      platform's target size (per Phase 15) at acceptable visual quality,
      confirmed by side-by-side comparison against the unoptimized output.
