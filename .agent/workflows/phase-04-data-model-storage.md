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

## Definition of done

- [ ] Insert/query/delete a sticker end-to-end (DB row + file) passes an
      instrumented test.
- [ ] Favourites and categories are queryable independently of packs.
- [ ] No code outside the repository layer touches Room or the filesystem
      directly (checked in review, not just by convention).
