---
description: "Phase 30: Ephemeral Link-Sharing Implementation & Received-Sticker Import"
---

# Phase 30 — Ephemeral Link-Sharing Implementation & Received-Sticker Import

**Owner:** Joel
**Type:** Implementation
**Depends on:** Phase 29

## Goal

Build Phase 29's spec, plus the receiving side. This is the one feature in
the app with a real network/server component, so treat the relay code with
the same scrutiny as anything security-relevant elsewhere in the plan.

## Tasks

1. Relay client: attempt direct connection first, fall back to the relay
   per the Phase 29 spec.
2. Link generation with the expiry window enforced both client-side and
   relay-side (don't trust the client alone to enforce expiry).
3. Receiving flow: open link, download within the window, import straight
   into Phase 4's library (landing in an "Imported" or similarly labeled
   category from Phase 7).
4. Link invalidation after first successful download or window expiry,
   whichever comes first.
5. The relay service itself does not have to be Kotlin — see the language
   note in `AGENTS.md`; Node.js or Python are both reasonable choices for a
   small, isolated service like this one.

## Definition of done

- [ ] A link generated on one device successfully delivers a sticker to
      another device with no shared network required.
- [ ] The link is confirmed unusable after either a completed download or
      the time window, whichever happens first — tested explicitly, not
      assumed.
