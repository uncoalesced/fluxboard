---
description: "Phase 23: Keyboard Image/Background Customization"
---

# Phase 23 — Keyboard Image/Background Customization

**Owner:** Joel
**Type:** Implementation
**Depends on:** Phase 21

## Goal

Image-based customization — custom key backgrounds and full keyboard
background images — layered on top of Phase 21's color theming.

## Tasks

1. Image picker for a background image.
2. Cropping/positioning to fit the keyboard surface.
3. Opacity/overlay controls so text stays legible over an arbitrary image
   (coordinate with Phase 31's contrast/accessibility pass).
4. Store the chosen image alongside the theme it belongs to (one theme can
   have one background).

## Definition of done

- [ ] A user can set a custom background image on a saved theme, and text
      remains legible against it.
