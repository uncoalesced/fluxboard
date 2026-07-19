---
description: "Phase 27: Device Pairing & Trust Establishment for Migration"
---

# Phase 27 — Device Pairing & Trust Establishment for Migration

**Owner:** Joel
**Type:** Implementation
**Depends on:** Phase 4

## Goal

Establish trust between a user's own two devices before a migration
transfer, reusing the QR-based pairing pattern already used on this team's
Impart project. This is for migrating between your own devices only — not
for sharing a single sticker with someone else, which is a different
problem solved in Phase 29/30.

## Tasks

1. QR generation (ZXing — see `docs/repo-reference.md`) encoding a
   one-time pairing token.
2. QR scanning on the receiving device.
3. Key exchange to derive a shared secret for the actual transfer's
   encryption in Phase 28.
4. Expiry on unused pairing codes.

## Definition of done

- [ ] Two devices complete a QR-based pairing and derive a shared secret
      neither device had before scanning, verified by a successful
      subsequent transfer in Phase 28.
