---
description: "Phase 9: Screenshot & Share-Intent Capture Pipeline"
---

# Phase 9 — Screenshot & Share-Intent Capture Pipeline

**Owner:** Joel
**Type:** Implementation
**Depends on:** Phase 4

## Goal

Get an image into the extraction pipeline via a screenshot or a
share-from-another-app action, not only via the gallery picker.

## Tasks

1. Screenshot detection: a content observer on the screenshots media-store
   bucket, with a manual "extract from last screenshot" action as a
   fallback if background detection proves too fragile or
   permission-heavy in practice.
2. `ACTION_SEND` share-intent handling for images shared in from other
   apps.
3. Hand off captured images into the segmentation entry point (Phase 10).

## Definition of done

- [ ] A screenshot taken outside the app reaches the segmentation entry
      point without a manual file-picker step.
- [ ] An image shared in from another app via the system share sheet does
      the same.
