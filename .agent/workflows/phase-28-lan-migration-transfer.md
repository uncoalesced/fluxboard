---
description: "Phase 28: LAN Device-to-Device Migration Transfer"
---

# Phase 28 — LAN Device-to-Device Migration Transfer

**Owner:** Joel
**Type:** Implementation
**Depends on:** Phase 27

## Goal

Move a user's entire sticker/keyboard-settings library from an old device
to a new one, with no server involved.

## Tasks

1. LAN discovery, following LocalSend's model (see
   `docs/repo-reference.md`).
2. An encrypted transfer of the full local data set (Phase 4's DB plus
   associated files, and Phase 24's clipboard history if the user opts in
   — this should be a distinct, explicit choice, not bundled silently)
   using the shared secret from Phase 27.
3. Progress/resume handling for large libraries.
4. Checksum verification that the receiving device's library matches the
   sender's afterward.

## Definition of done

- [ ] A full library migrates from one device to another over the same
      Wi-Fi network with no data loss, verified by checksum.
- [ ] Clipboard history is only included if explicitly opted into.
