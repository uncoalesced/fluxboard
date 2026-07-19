# Agent Prompts

What to actually type into Antigravity's Agent panel, phase by phase — and
this time, detailed enough that the agent shouldn't need to stop and ask
about anything we can already answer. Phase 1 surfaced two real questions
in practice (license, targetSdk/AGP/JDK) that a more detailed prompt would
have pre-empted — every entry below now carries that lesson forward:
state the settled decisions explicitly, flag the genuinely open ones with
a recommended default, and tell the agent exactly when to stop and ask
instead of guessing.

## How this maps onto Antigravity

- `AGENTS.md` and everything in `.agent/rules/` load automatically, every
  session, at every phase — including the new "verify current dependency
  versions, don't trust training data" rule added after the AGP incident.
  You never type a prompt for those.
- Each phase lives at `.agent/workflows/phase-XX-name.md`. Typing
  `/phase-XX-name` makes Antigravity read that file and work through its
  Tasks and Definition of Done.
- Everything below the slash command in each block is *additional*,
  typed-out context for that specific run — redundant with the workflow
  file on purpose, so the agent gets the full picture even if it doesn't
  re-read every referenced doc closely.

## Operating rules — read before Prompt 1

1. **One phase at a time.** Run it, review the diff, then move to the
   next. Don't chain several unsupervised — mistakes compound downstream,
   and it's far cheaper to catch one right after the phase that caused it.
2. **Versions are checked, not remembered.** Any time a prompt below
   mentions a library or tool version, that's either a number already
   confirmed for this project (license, SDK, AGP, JDK — settled in
   Phase 1) or an explicit instruction to verify current rather than
   assume. If the agent proposes a specific version not covered by either
   case, don't wave it through — ask it to confirm the number is actually
   current first.
3. **Some questions are genuinely Joel's to answer, not the agent's.**
   A few prompts below include a recommended default for exactly this
   reason (package name, database encryption, keyboard language scope,
   relay hosting) — read those call-outs and either confirm the default
   or override it before that phase finishes, rather than letting it slide
   past unconfirmed the way targetSdk almost did.

Research phases are marked **(R)** — expect a written recommendation or
spec document from these, not a code diff.

---

## Prompt 0 — Orientation (run first, either person)

```
Read AGENTS.md, both files in .agent/rules/, and docs/repo-reference.md in
full. Then summarize back: the tech stack, the hard constraints (size
budget, privacy, language rules, the version-verification rule), and the
full phase list in order with owners. Flag anything that looks
contradictory, outdated, or unclear before phase 1 starts — including
anything in docs/repo-reference.md that seems like it might have changed
since it was written.
```

*Why:* confirms the agent has actually ingested the project context
before writing anything, and gives it an explicit opening to flag stale
information in the repo-reference doc itself, not just in code it writes.

---

## Foundation (Phases 1-4)

**Prompt 1 — Phase 1: Project Scaffolding & Repository Setup (Joel)**
```
/phase-01-project-scaffolding

Settled already, do not re-ask about these:
- License: MIT, already in the repo, copyright "uncoalesced". Keep it —
  do not switch to Apache-2.0.
- targetSdk: 36 (Android 16) — current stable, and Google Play requires
  all app updates to target 36 starting August 2026 regardless.
- AGP: 9.3.0 or newer stable (verify — do not default to anything in the
  8.x line). Use a Gradle version compatible with whichever AGP you land
  on.
- JDK: 17+ is required to run the Gradle daemon at all, locally and in
  CI. Set org.gradle.java.home in gradle.properties explicitly — don't
  rely on JAVA_HOME being correct in every shell that might invoke Gradle.

Not yet settled — confirm with me before finishing this phase:
- applicationId / package namespace. Default if I don't say otherwise:
  com.uncoalesced.stickerkeyboard. This is expensive to change later, so
  flag it explicitly rather than just picking the default silently.

If anything in the repo already contradicts one of the "settled" items
above (e.g. a different license file turns up somewhere), stop and tell
me rather than resolving the conflict yourself.
```
*Why:* this phase generated the actual questions that prompted this whole
rewrite — every one of them is now answered inline rather than left for
the agent to raise mid-run.

**Prompt 2 — Phase 2: Architecture Foundation (Joel)**
```
/phase-02-architecture-foundation

Use current stable versions of Hilt, the Compose BOM, and Navigation
Compose — verify each rather than assuming a remembered number, per the
version-verification rule. Navigation Compose is the intended navigation
solution; don't substitute a different router or hand-roll one.
```
*Why:* the only real ambiguity here is dependency versions — everything
else in the phase file is already concrete. Depends on Prompt 1.

**Prompt 3 — Phase 3: Design System & Theming Tokens (Rahul)**
```
/phase-03-design-system-tokens

This is a genuine design decision, not a technical one — propose a
palette and type scale rather than asking me to specify exact colors
up front, then bring it back for a quick look before Phase 21 depends on
it. Material 3 is a reasonable structural baseline; the actual color
choices are yours to propose.
```
*Why:* the one thing worth heading off here is the agent asking "what
colors do you want" before proposing anything — it should propose first,
get sign-off after. Depends on Prompt 2.

**Prompt 4 — Phase 4: Local Data Model, Room Schema & File Storage (Joel)**
```
/phase-04-data-model-storage

Settled: no encryption-at-rest on the Room database for v1 — Android's
per-app private storage already isolates it from other apps, and
something like SQLCipher costs real size budget for a mostly-redundant
benefit here. If you think this project's threat model actually needs
it, say so explicitly rather than adding or skipping it silently.
```
*Why:* pre-answers the one non-obvious architectural question in this
phase. Depends on Prompt 2.

---

## Sticker Core (Phases 5-7)

**Prompt 5 — Phase 5: Manual Sticker Creation Flow (Joel)**
```
/phase-05-manual-sticker-creation
```
*Why:* nothing ambiguous here — the workflow file is already concrete.
Depends on Prompt 4.

**Prompt 6 — Phase 6: Sticker Editing Suite (Joel)**
```
/phase-06-sticker-editing-suite

If the non-destructive-history-vs-overwrite decision isn't obvious once
you're in it, default to the simpler explicit overwrite choice for v1
and note history as a possible fast-follow, rather than spending a lot of
time on undo/redo infrastructure now.
```
*Why:* the workflow file left this as an open choice on purpose — this
gives the agent a tie-breaker default so it doesn't over-invest in the
harder option by default. Depends on Prompt 5.

**Prompt 7 — Phase 7: Sticker Organization: Categories & Favourites (Rahul)**
```
/phase-07-sticker-organization
```
*Why:* straightforward UI over the Phase 4 data layer, nothing to
pre-answer. Depends on Prompt 4.

---

## Extraction (Phases 8-11)

**Prompt 8 — Phase 8: Segmentation Approach Research & Library Evaluation (R) (Rahul)**
```
/phase-08-segmentation-research

Self-source the ~20 test images (roughly 5 people, 5 pets, 5 objects, 5
busy backgrounds) rather than asking which images to use — document what
you used so the comparison is reproducible. Re-verify U2NetP's license
directly rather than trusting docs/repo-reference.md's summary of it.
```
*Why:* removes the two questions most likely to stall a research phase
like this — which images, and whether to trust a possibly-dated license
note. Can run any time.

**Prompt 9 — Phase 9: Screenshot & Share-Intent Capture Pipeline (Joel)**
```
/phase-09-capture-pipeline

If background screenshot detection turns out to need a permission or
API level that conflicts with the min-SDK-26 target, fall back to the
manual "extract from last screenshot" action rather than lowering
min-SDK or skipping the feature — the workflow file already names this
as the fallback, so use it rather than stalling.
```
*Why:* gives the agent explicit permission to take the documented
fallback instead of getting stuck on a platform-fragmentation edge case.
Depends on Prompt 4.

**Prompt 10 — Phase 10: On-Device Segmentation Integration & Touch-Up UI (Joel)**
```
/phase-10-segmentation-integration

Implement whatever Phase 8 recommended. If that choice depends on Google
Play Services, say so explicitly in your output — that's a rule from
.agent/rules/privacy-and-scope.md, not optional, and it's the kind of
thing that's easy to bury inside implementation code where it won't be
noticed later.
```
*Why:* restates a rule that's easy to silently satisfy-in-letter-but-not-
in-spirit (technically flagging it in a code comment nobody reads isn't
the same as flagging it where a human will see it). Depends on
Prompts 8 and 9.

**Prompt 11 — Phase 11: Gallery/Photo Picker Import Flow (Rahul)**
```
/phase-11-gallery-import
```
*Why:* standard platform picker integration, nothing to pre-answer.
Depends on Prompts 5 and 10.

---

## Video & GIF (Phases 12-15)

**Prompt 12 — Phase 12: Video Import & Trim UI (Rahul)**
```
/phase-12-video-import-trim

Default the maximum trim length to 10 seconds unless Phase 15's research
(once it exists) says a specific target platform needs something
shorter — don't leave the cap unset or ask before picking a starting
number.
```
*Why:* gives a concrete starting number instead of leaving "duration cap"
undefined. Depends on Prompt 2.

**Prompt 13 — Phase 13: Video-to-GIF / Animated WebP Conversion Pipeline (Joel)**
```
/phase-13-video-to-gif-pipeline

Do not introduce FFmpeg or any FFmpeg wrapper — use MediaCodec/
MediaMetadataRetriever for frame extraction and gif.kt (verify its
current version on Maven Central) for GIF encoding, per
docs/repo-reference.md. Default to WebP as the internal storage format,
exporting to GIF only where a target platform requires it.
```
*Why:* the FFmpeg-size tradeoff was already decided during planning —
this makes sure the agent doesn't reach for the "obvious" library instead
of the one chosen deliberately. Depends on Prompt 12.

**Prompt 14 — Phase 14: GIF/WebP Size Optimization Pass (Joel)**
```
/phase-14-gif-size-optimization

Wait for Phase 15's compatibility table before treating any specific
file-size number as a hard target — optimize for general smallness now
if Phase 15 hasn't run yet, then re-tune against real numbers once it
has.
```
*Why:* makes the Phase 14/15 dependency explicit as an instruction, not
just a note in the workflow file. Depends on Prompts 13 and 15.

**Prompt 15 — Phase 15: Sticker/GIF Platform-Compatibility Research (R) (Rahul)**
```
/phase-15-platform-compatibility-research

Verify WhatsApp's requirements directly against the current
WhatsApp/stickers repo rather than trusting docs/repo-reference.md's
summary — it may be out of date by the time this runs. Telegram's and
Signal's specs aren't pinned down anywhere yet; that's this phase's job.
```
*Why:* explicit instruction to re-verify rather than copy a possibly-
stale summary — same pattern as the version-checking rule, applied to a
spec instead of a software version. Can run any time.

---

## Keyboard — the flagship (Phases 16-26)

**Prompt 16 — Phase 16: Minimal Sticker-Only IME Shell (Joel)**
```
/phase-16-minimal-sticker-ime

This has to be a real InputMethodService using the Commit Content API —
that's the only Android-sanctioned way to send an image into another
app's text field, not a workaround. Wire the grid to the categories and
favourites from Phase 7, not a flat list.
```
*Why:* forecloses the tempting-but-wrong shortcut of trying to solve this
via a share-sheet action instead of a real IME. Depends on Prompts 4
and 7.

**Prompt 17 — Phase 17: Full Typing Keyboard Core (Joel)**
```
/phase-17-full-keyboard-core

Language scope for v1: English/Latin only. Additional layouts (Hindi,
Kannada, or others) are a real, reasonable ask given where this project
is based, but each one means redoing the Phase 18/19 dictionary work per
language — don't build multi-language support without me explicitly
asking for it, and don't assume it's permanently out of scope either;
just treat it as not-yet-decided and flag it back to me.
```
*Why:* this is the one scope question in the whole plan I can't
responsibly pre-answer on Joel's behalf — flagging it explicitly here
beats either silently scoping it in or silently scoping it out. Depends
on Prompt 16.

**Prompt 18 — Phase 18: Predictive Text Engine Research & Dictionary Pipeline (R) (Rahul)**
```
/phase-18-predictive-text-research

Re-verify FlorisBoard's current Apache-2.0 terms directly rather than
trusting docs/repo-reference.md's note on it. For the base corpus, pick
a clearly-licensed public source yourself and document exactly which one
and its license — don't ask which corpus to use. Using FlorisBoard's
Python dictionary-tools pipeline for offline preprocessing is fine and
expected; that's not the same as running Python on-device, which is
still off the table.
```
*Why:* this is the hardest research phase in the plan and has the most
foreseeable stalling points (license doubt, corpus choice, confusion
about the Python rule) — all three answered directly. Can start
alongside Prompt 17.

**Prompt 19 — Phase 19: Predictive Text Engine Implementation (Joel)**
```
/phase-19-predictive-text-implementation

Target the accuracy level of a solid classic predictive keyboard
(n-gram plus per-user personalization) — not a from-scratch neural
language model, which is a much larger and different project. No
personalization data leaves the device, ever; this isn't a performance
tradeoff to weigh, it's a hard constraint.
```
*Why:* sets an explicit, realistic bar for "very accurate" so the agent
doesn't either under-deliver (a static, non-adaptive suggestion list) or
massively over-scope (attempting a neural model). Depends on Prompts 17
and 18.

**Prompt 20 — Phase 20: Auto-Capitalize & Auto-Correct Logic + Toggles (Joel)**
```
/phase-20-autocap-autocorrect

The one-tap undo on a bad autocorrect is not optional polish — treat it
as part of the feature, not a follow-up task.
```
*Why:* keeps the undo path from getting deprioritized as a "nice to have"
separate from the core correction logic. Depends on Prompt 19.

**Prompt 21 — Phase 21: Keyboard Theming Engine (Joel)**
```
/phase-21-keyboard-theming-engine

Use the tokens Phase 3 already produced rather than defining a second,
parallel set of color/type values for the keyboard specifically.
```
*Why:* the most likely mistake here is duplicating design decisions
instead of reusing Phase 3's output. Depends on Prompts 3 and 17.

**Prompt 22 — Phase 22: Keyboard Layout Customization Engine (Joel)**
```
/phase-22-keyboard-layout-customization
```
*Why:* concrete already, nothing to pre-answer. Depends on Prompt 17.

**Prompt 23 — Phase 23: Keyboard Image/Background Customization (Joel)**
```
/phase-23-keyboard-image-customization
```
*Why:* concrete already, nothing to pre-answer. Depends on Prompt 21.

**Prompt 24 — Phase 24: Clipboard History Manager (Joel)**
```
/phase-24-clipboard-history-manager

History is cleared only by an explicit user action — never by time,
count, or any other automatic eviction, even as a "reasonable default."
Respect ClipDescription.EXTRA_IS_SENSITIVE and OS-level sensitive-content
detection when deciding what to persist in the first place; that governs
what gets written, not when it's removed, so it doesn't loosen the
clear-only-by-user rule.
```
*Why:* the persistence rule here is unusual enough (most clipboard
managers do auto-expire) that it's worth stating as a hard rule rather
than trusting the workflow file's phrasing alone. Depends on Prompts 4
and 17.

**Prompt 25 — Phase 25: Haptics & Vibration Feedback (Rahul)**
```
/phase-25-haptics-vibration

Use VibrationEffect.createOneShot with amplitude control (API 26+,
matching this project's min SDK) rather than pulling in a third-party
haptics library — none is needed here.
```
*Why:* heads off the agent reaching for an external dependency for
something the platform already does natively. Depends on Prompts 16
and 17.

**Prompt 26 — Phase 26: Keyboard & App Settings UI (Rahul)**
```
/phase-26-settings-ui

This is UI wiring over ViewModels and repositories that already exist
from Phases 20-25 — don't introduce new business logic here. Clearing
clipboard history needs an explicit confirmation step since it's
irreversible.
```
*Why:* keeps this phase scoped to UI, and restates the one genuinely
easy-to-miss detail (confirm before an irreversible clear). Depends on
Phases 20-25.

---

## Cross-device (Phases 27-30)

**Prompt 27 — Phase 27: Device Pairing & Trust Establishment for Migration (Joel)**
```
/phase-27-device-pairing

Verify zxing-android-embedded's current maintenance status before
committing to it — it was in maintenance mode as of the repo-reference
research, so confirm nothing better has appeared since, rather than
assuming that note is still current.
```
*Why:* applies the version/status-verification rule to a library
maintenance status, not just a version number. Depends on Prompt 4.

**Prompt 28 — Phase 28: LAN Device-to-Device Migration Transfer (Joel)**
```
/phase-28-lan-migration-transfer

Clipboard history (Phase 24) only transfers if the user explicitly opts
in for that specific migration — never bundle it into the default
transfer silently, even though it's technically part of the same local
data set.
```
*Why:* this is exactly the kind of thing that's easy to include by
default because it's convenient, not because it was decided — worth
restating directly. Depends on Prompt 27.

**Prompt 29 — Phase 29: Ephemeral Link-Sharing Architecture (Joel)**
```
/phase-29-link-sharing-architecture

This phase produces a spec/design document, not code — don't start
implementing the relay yet. On the hosting decision: if there's no
strong preference already, propose a single small cheap-or-free-tier VPS
instance as the default, since the relay is deliberately tiny and
stateless, but bring that recommendation back for explicit confirmation
before Phase 30 builds anything against it.
```
*Why:* keeps this phase from accidentally sliding into implementation,
and gives the agent a default to propose rather than stalling on the one
real infrastructure decision in the whole plan. Can run alongside
Prompts 27/28.

**Prompt 30 — Phase 30: Ephemeral Link-Sharing Implementation & Received-Sticker Import (Joel)**
```
/phase-30-link-sharing-implementation

Only proceed once Phase 29's spec has been explicitly reviewed and
approved — if that approval hasn't happened yet, stop and say so rather
than building against your own unreviewed spec.
```
*Why:* this is the one phase in the whole plan with a real security/
infra surface — worth an explicit gate rather than assuming the previous
phase's output was automatically approved. Depends on Prompt 29 being
reviewed, not just written.

---

## Quality, Size & Release (Phases 31-37)

**Prompt 31 — Phase 31: Privacy & Permissions Audit (R) (Rahul)**
```
/phase-31-privacy-audit

Do an actual network traffic capture during normal use, not just a
dependency-docs review — confirm empirically that the only outbound call
in the entire app is the Phase 29/30 relay.
```
*Why:* the workflow file already says this, but it's worth restating
since "read the docs and confirm" is a much easier (and weaker) task to
default to than "actually capture traffic." Depends on Phases 24, 28,
and 30.

**Prompt 32 — Phase 32: App Size Budget Tracking & Optimization (Joel)**
```
/phase-32-size-budget

Measure the actual current size of whichever segmentation model and
dictionary data ended up bundled — don't reuse the estimates from
docs/repo-reference.md, which were written before implementation and may
not match what actually shipped.
```
*Why:* forecloses the shortcut of citing planning-stage estimates as if
they were measured facts. Depends on Prompts 10 and 19.

**Prompt 33 — Phase 33: Accessibility & Localization Pass (Rahul)**
```
/phase-33-accessibility-localization
```
*Why:* concrete already, nothing to pre-answer. Depends on Prompts 21,
23, and 26.

**Prompt 34 — Phase 34: Unit Testing Strategy for Core Logic (Joel)**
```
/phase-34-unit-testing-strategy

Backfill tests for anything from Phases 1-30 that skipped the
per-phase testing convention in .agent/rules/code-conventions.md — check
for gaps rather than assuming coverage exists just because a phase is
marked done.
```
*Why:* explicitly asks the agent to check rather than assume prior
phases followed the testing rule. Depends on Prompts 10, 13, 19, and 24.

**Prompt 35 — Phase 35: UI/Instrumented Testing for Sticker & Keyboard Flows (R) (Rahul)**
```
/phase-35-ui-instrumented-testing

Where full instrumentation genuinely isn't practical, a documented
manual test script is an acceptable substitute — don't leave a journey
completely uncovered just because automating it is hard.
```
*Why:* gives explicit permission to fall back to a documented manual
script instead of stalling on hard-to-automate cases. Depends on
Prompt 34.

**Prompt 36 — Phase 36: F-Droid / Open-Source Distribution Packaging (R) (Rahul)**
```
/phase-36-fdroid-packaging

Re-check every bundled dependency's license against F-Droid's current
inclusion criteria directly — don't rely solely on
docs/repo-reference.md, which may be out of date by now. A Play Store
listing is optional and Joel's call, not a default to assume either way.
```
*Why:* another instance of the verify-don't-trust-the-planning-doc
pattern, plus an explicit non-decision on Play Store rather than a
silent assumption. Depends on Prompt 32.

**Prompt 37 — Phase 37: Documentation Pass (R) (Rahul)**
```
/phase-37-documentation

Write the architecture doc as a living summary of what was actually
built across all 37 phases — not a restatement of this planning
document, which describes what was intended before anything existed.
```
*Why:* keeps the final documentation honest about what shipped, rather
than a copy of the plan that may have diverged from reality by now.
Depends on Prompt 36 — run this last.
