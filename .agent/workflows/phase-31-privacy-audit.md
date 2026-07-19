---
description: "Phase 31: Privacy & Permissions Audit"
---

# Phase 31 — Privacy & Permissions Audit

**Owner:** Rahul
**Type:** Research/audit
**Depends on:** Phase 24, Phase 28, Phase 30 (needs the clipboard, transfer,
and sharing features in place to audit them)

## Goal

Verify, near the end of the build, that the app actually holds to the
zero-telemetry/minimal-permission principle it was designed around — not
just that it was designed that way.

## Tasks

1. Full manifest permission review against
   `.agent/rules/privacy-and-scope.md`'s checklist.
2. Dependency audit for anything that slipped in with hidden analytics.
3. Confirm the only network call in the entire app is the Phase 29/30
   relay.
4. Empirically check for phone-home behavior — a network traffic capture
   during normal use is a better check than reading a dependency's docs
   and trusting them.
5. Specifically re-check the clipboard feature (Phase 24) against the
   sensitive-content handling it was built with, and the migration
   transfer (Phase 28) against the "clipboard is opt-in" requirement.

## Definition of done

- [ ] A written audit report with a pass/fail per checklist item.
- [ ] Any failures filed as fix-it tasks before release.
