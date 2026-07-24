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
5. License: **MIT, already in place — keep it.** Copyright should credit
   both project authors per `AUTHORS.md`: `uncoalesced` and
   `ZapCannonYT`. If the LICENSE file's copyright line still only names
   `uncoalesced`, update it to cover both rather than leaving it
   unreconciled — don't re-litigate MIT vs. Apache-2.0 per the workflow's
   original suggestion to consider it.
6. `README.md` skeleton, `CONTRIBUTING.md` stub, issue templates.
7. `minSdk 26`; `targetSdk` pinned to **API 36 (Android 16)** as of
   July 2026 — this is current stable, and Google Play requires all app
   updates to target 36 starting August 2026 regardless, so there's no
   reason to start lower. Use **AGP 9.3.0** (or newer stable, re-check if
   this is read much later) and a matching current Gradle version — not
   AGP 8.x, which is now a full major version behind. Requires **JDK 17+**
   to run the Gradle daemon at all; set `org.gradle.java.home` in
   `gradle.properties` explicitly rather than relying on `JAVA_HOME`, and
   make sure this is fixed locally, not only in CI.
8. `applicationId` / package namespace: **not yet fixed — default to
   `com.uncoalesced.stickerkeyboard` unless Joel says otherwise.** This is
   worth getting Joel's explicit sign-off on before finishing this phase
   rather than treating the default as final — an `applicationId` is
   painful to change once anything (a Play listing, an F-Droid entry,
   installed users' data) depends on it, unlike almost everything else in
   this phase.

## Definition of done

- [ ] A blank app builds and runs on a min-SDK (26) device/emulator.
- [ ] CI is green on a trivial commit, using JDK 17+.
- [ ] LICENSE file present (MIT), copyright line covers both authors in
      `AUTHORS.md`.
- [ ] Local Gradle daemon runs on JDK 17+, confirmed explicitly — not
      assumed from a system default.
- [ ] `applicationId` explicitly confirmed with Joel, not left on the
      default unconfirmed.
- [ ] Module structure reflects the subsystem boundaries used throughout
      the rest of this plan.
