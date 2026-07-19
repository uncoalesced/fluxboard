---
description: "Phase 13: Video-to-GIF / Animated WebP Conversion Pipeline"
---

# Phase 13 — Video-to-GIF / Animated WebP Conversion Pipeline

**Owner:** Joel
**Type:** Implementation
**Depends on:** Phase 12

## Goal

Turn a trimmed video range into an output sticker, in both GIF and
animated WebP, without pulling in FFmpeg — FFmpeg's native binaries commonly
run tens of MB per ABI, which threatens the 100 MB budget on their own.

## Tasks

1. Frame extraction via Android's built-in `MediaCodec` /
   `MediaMetadataRetriever` — no external library needed for this step.
2. Encode to GIF using `gif.kt` (see `docs/repo-reference.md`).
3. Encode to animated WebP using Android's native encoding path.
4. Frame-rate and resolution downsampling controls (stickers don't need
   1080p).
5. Progress reporting for the encode step, which can be slow on longer
   clips.
6. Pick a default internal/storage format between the two (recommendation:
   WebP internally, exporting to GIF only where a target platform requires
   it) and document the choice.

## Definition of done

- [ ] A trimmed video range produces both a working GIF and a working
      animated WebP, played back correctly in-app.
