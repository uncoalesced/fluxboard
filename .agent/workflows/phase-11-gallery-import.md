---
description: "Phase 11: Gallery/Photo Picker Import Flow"
---

# Phase 11 — Gallery/Photo Picker Import Flow

**Owner:** Rahul
**Type:** Implementation (light — standard platform picker integration)
**Depends on:** Phase 5, Phase 10

## Goal

The standard "pick from gallery" import path, feeding the same
manual-creation and auto-segmentation entry points Phases 5 and 10 already
built.

## Tasks

1. Integrate Android's Photo Picker (privacy-friendly — no broad storage
   permission needed on modern Android).
2. Multi-select support.
3. Route each selected image into either manual creation (Phase 5) or
   auto-segmentation (Phase 10), based on user choice at import time.

## Definition of done

- [ ] A user can multi-select images from the photo picker and each one
      lands correctly in the creation flow they chose.
