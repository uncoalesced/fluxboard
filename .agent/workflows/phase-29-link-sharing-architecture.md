---
description: "Phase 29: Ephemeral Link-Sharing Architecture"
---

# Phase 29 — Ephemeral Link-Sharing Architecture

**Owner:** Joel
**Type:** Design/spec (not yet implementation — that's Phase 30)
**Depends on:** None (can run alongside Phase 27/28)

## Goal

Design the "send someone a sticker via a link that expires in 5–10
minutes" feature, following the Magic Wormhole pattern rather than QR — QR
is the wrong UX for this specific use case (asking someone mid-conversation
to scan a code to get one sticker is exactly the friction this is meant to
avoid). See `docs/repo-reference.md` for the reference pattern.

## Tasks

1. Define the relay's responsibilities precisely and minimally: broker a
   key exchange and connection attempt only; relay encrypted bytes only if
   a direct connection fails; hold nothing beyond the active transfer; log
   nothing.
2. Define the link/token format: short-lived, single-use.
3. Define the LAN-first optimization: if sender and receiver are reachable
   on the same network (the same detection Phase 28 uses), skip the relay
   entirely and transfer directly.
4. Decide who runs the relay — a minimal service Joel hosts, vs. a
   community-run option — and write that decision down explicitly. This is
   a real infrastructure and cost commitment, not a hidden one, and it's
   the one part of this whole app that isn't fully local-first.
   **Recommended default if there's no strong preference already:** a
   single small always-on instance on a cheap or free-tier VPS, since the
   relay is deliberately tiny and stateless — this is cheap to run, easy to
   move later, and doesn't block Phase 30 on finding a community operator
   first. Confirm with Joel before Phase 30 builds against it either way.
5. Note explicitly for WhatsApp specifically: this feature matters less
   there, since a received sticker can just be favourited directly in
   WhatsApp without needing a new link at all. It matters most for
   platforms without that native save/favourite behavior.

## Definition of done

- [ ] A written spec covering the token format, the relay's exact
      responsibilities, the direct-vs-relay fallback logic, and the
      hosting decision — reviewed before Phase 30 writes any code against
      it.
