---
description: "Phase 21: Keyboard Theming Engine"
---

# Phase 21 — Keyboard Theming Engine

**Owner:** Joel
**Type:** Implementation
**Depends on:** Phase 3, Phase 17

## Goal

Colors, fonts, and sizes as fully user-customizable keyboard theme
properties, built on Phase 3's design tokens.

## Tasks

1. A theme data model: key background/text/accent colors, font family,
   font size scale, key height/spacing.
2. Live preview while editing a theme.
3. Persistence for more than one saved theme — not just a single active
   override.
4. A small set of built-in presets shipped by default.
5. Study FlorisBoard's "Snygg" theme engine (see `docs/repo-reference.md`)
   for how it structures theme definitions (JSON or Kotlin DSL) — worth
   borrowing the structure even if not the code directly.

## Definition of done

- [ ] A user can create, save, and switch between at least two custom
      themes, each visibly different in the live keyboard.
