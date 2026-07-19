---
description: "Phase 3: Design System & Theming Tokens"
---

# Phase 3 — Design System & Theming Tokens

**Owner:** Rahul
**Type:** Research + light implementation
**Depends on:** Phase 2

## Goal

Define the base design tokens that both the app's own UI and the keyboard
theming engine (Phase 21) will draw from later — get this right once
rather than fixing it in two places later.

## Tasks

1. Propose a light + dark color palette with accessible contrast ratios
   (coordinate with Phase 31's accessibility pass so this doesn't need
   redoing).
2. Define a type scale (small/medium/large presets — this is what "font
   size" customization means concretely later).
3. Define a spacing scale and corner-radius scale.
4. Package tokens two ways: as a Compose theme object (for the app UI) and
   as a plain Kotlin data class (the keyboard's IME process needs these
   values without a standard Activity Compose context).

## Definition of done

- [ ] Tokens compile and are consumed by both the Compose theme and the
      plain-object version.
- [ ] A one-page style sheet (palette + type samples, rendered) is produced
      and reviewed by Joel before Phase 21 depends on these tokens.
