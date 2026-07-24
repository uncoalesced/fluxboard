---
description: "Phase 4: Local Data Model, Room Schema & File Storage"
---

# Phase 4 — Local Data Model, Room Schema & File Storage

**Owner:** Joel
**Type:** Implementation
**Depends on:** Phase 2

## Goal

Single source of truth for stickers, packs, categories, and favourites, and
where their actual image/GIF/WebP bytes live on disk. Everything downstream
(creation, editing, organization, transfer, sharing) depends on this being
right.

## Tasks

1. Room entities: `Sticker`, `Pack`, `Category`, a favourite flag or join
   table, tags if scoped in.
2. DAOs exposing `Flow`-returning queries (UI needs to react live, not poll).
3. On-disk file layout: content-addressed or UUID-named files plus a
   thumbnails directory, kept out of the Room DB itself (Room indexes it,
   doesn't store blobs directly).
4. A repository layer wrapping Room + file access behind one interface —
   nothing above this layer should touch Room or the filesystem directly.
5. Migration strategy and test harness set up now, even though v1 has
   nothing to migrate from yet.
6. Encryption-at-rest for the Room database: **default is no, for v1.**
   Android's per-app private storage is already inaccessible to other apps
   without root, and something like SQLCipher adds real size and
   complexity cost against the 100 MB budget for a benefit that's mostly
   redundant with that sandboxing. This is a real tradeoff, not an
   oversight — flag it back to Joel if there's a specific threat model in
   mind that changes the calculus (e.g. targeting rooted-device users
   explicitly), rather than silently adding or skipping encryption.

## Definition of done

- [ ] Insert/query/delete a sticker end-to-end (DB row + file) passes an
      instrumented test.
- [ ] Favourites and categories are queryable independently of packs.
- [ ] No code outside the repository layer touches Room or the filesystem
      directly (checked in review, not just by convention).
