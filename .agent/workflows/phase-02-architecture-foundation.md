---
description: "Phase 2: Architecture Foundation"
---

# Phase 2 — Architecture Foundation

**Owner:** Joel
**Type:** Implementation
**Depends on:** Phase 1

## Goal

Establish the MVVM/UDF + Hilt + Navigation skeleton every feature phase
builds on, before any real feature exists.

## Tasks

1. Hilt setup: `Application` class, base app-level modules.
2. Compose navigation graph with placeholder screens for each top-level
   destination (Stickers library, Keyboard settings, Transfer/Share,
   Settings).
3. Base `ViewModel` convention: a sealed `UiState` per screen, no Android
   framework references inside ViewModels.
4. Shared loading/error-state UI conventions.
5. Root theme wrapper, left as a stub until Phase 3's tokens exist to fill
   it in.

## Definition of done

- [ ] App launches and navigates between placeholder screens using
      Hilt-injected (empty) ViewModels.
- [ ] The `UiState` / ViewModel convention is written down in a short
      `ARCHITECTURE.md` so later phases follow the same pattern instead of
      improvising a new one each time.
