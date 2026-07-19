---
description: "Phase 1: Project Scaffolding & Repository Setup"
---

# Phase 1 — Project Scaffolding & Repository Setup

**Owner:** Joel
**Type:** Implementation
**Depends on:** None (start here)

## Goal

Stand up the actual Android project and repo hygiene everything else builds
on top of.

## Tasks

1. Multi-module Gradle setup: an `app` module plus per-subsystem modules
   (e.g. `sticker-core`, `keyboard-core`, `transfer`) so ownership and
   build boundaries line up with how work is actually split.
2. Version catalog (`libs.versions.toml`) for dependency management.
3. `ktlint` + `.editorconfig` wired into the build.
4. CI (build + lint + unit tests on push/PR).
5. Choose and add an OSI-approved open-source license file (Apache-2.0 is a
   reasonable default for a forkable project — note the tradeoff vs.
   GPL-family copyleft if considering otherwise, and settle it here since
   it affects every later dependency choice).
6. `README.md` skeleton, `CONTRIBUTING.md` stub, issue templates.
7. `minSdk 26`, `targetSdk` latest stable, in the app module's build config.

## Definition of done

- [ ] A blank app builds and runs on a min-SDK (26) device/emulator.
- [ ] CI is green on a trivial commit.
- [ ] LICENSE file present and matches the chosen license.
- [ ] Module structure reflects the subsystem boundaries used throughout
      the rest of this plan.
