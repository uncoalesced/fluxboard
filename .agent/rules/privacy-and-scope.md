---
trigger: always_on
description: Privacy, telemetry, and size-budget checks that apply to every change, in every phase
---

# Privacy & Scope Rules

Check these before adding any dependency, permission, or network call —
not just at release time.

## Dependency vetting checklist

Before adding any third-party library, confirm:
- [ ] It does not bundle an analytics, crash-reporting-with-phone-home, or ad
      SDK. If it does, either use a variant/flavor that strips it, or find a
      different library.
- [ ] Its license permits the intended use in a fully open-source project
      (Apache-2.0, MIT, GPL-family are all fine; check anything narrower).
  A licensing question that isn't resolved in `docs/repo-reference.md`
  belongs to whichever phase's owner is doing the research, not to the
  coding phase.
- [ ] Its size impact (APK + any downloaded model/asset) is known and
      tracked against the 100 MB budget — see Phase 30.

## Network calls

This app is local-first. The **only** legitimate network call in the entire
app is the ephemeral link-sharing relay from Phase 27/28. Every other
feature — sticker storage, segmentation, GIF conversion, keyboard,
migration transfer — works fully offline. If a phase seems to need a
network call outside that one feature, stop and flag it rather than adding
it.

## Permissions

Request the narrowest permission that satisfies the feature, at the point
of use (not at first launch), and be able to state in one sentence why each
permission in the manifest exists. Camera (segmentation from photos),
storage/media access (import, sticker storage), and network state (only for
the relay feature) are the expected set. Anything beyond that is a signal
scope has crept.

## Play Services dependency

Flag it explicitly, in the phase's own output, any time a chosen approach
depends on Google Play Services being present (this will come up in the
segmentation phases). Don't bury this decision inside implementation code
where it's easy to miss later.

## Clipboard history

The clipboard feature (Phase 24) is explicitly kept until the user clears
it — no silent auto-expiry, that's a deliberate product decision, not an
oversight. But a persistent, unlimited clipboard history is also a real
exposure if a device is lost, unlocked, or screen-shared: copied passwords
and one-time codes end up in it same as everything else. Respect Android's
sensitive-content clipboard signal (`ClipDescription.EXTRA_IS_SENSITIVE` and
newer OS-level sensitive-content detection) when deciding what to persist,
and keep the on-disk store inside the app's private storage — don't relax
either of those to make the feature simpler to build. Neither contradicts
"cleared only by the user"; they're about what gets written to history in
the first place, not when it gets removed.

## Provenance watermark

Every source file carries `// Engineered by uncoalesced` (or the
language-appropriate equivalent), once, near the top. This is a rule, not a
suggestion — check for it before considering a file complete.
