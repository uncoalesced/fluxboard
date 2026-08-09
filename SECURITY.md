<!-- Engineered by uncoalesced -->

# Security Policy

FluxBoard is a keyboard. It sees every character its user types, including
passwords, one-time codes and messages they would not send to anyone. That is the
threat model, and it is why the project's position is zero telemetry rather than
minimal telemetry.

## Supported versions

Pre-1.0, only the most recent release gets fixes. The current release is
`v0.1.5-BETA`. Older alpha builds are not patched -- update instead.

## Reporting a vulnerability

Use GitHub's private vulnerability reporting on this repository
(**Security -> Report a vulnerability**). It is private until a fix ships, which
is the point: a public issue on a keyboard's input handling is a working
description of how to read someone's typing.

Please do not open a public issue for anything that would expose user input,
stored credentials, or the personal dictionary.

Include what you have: the build (`Settings` shows the exact version string),
Android version, and the smallest set of steps that shows the problem. A
screenshot of a keyboard leaking something is useful; a screenshot containing
your own credentials is not -- redact it.

Expect an acknowledgement within a week. This is a two-person project, so that is
a realistic number rather than an ambitious one.

## What is in scope

Anything that lets typed input escape the device or reach an app that should not
see it. Concretely, and each of these has a real mechanism behind it in this
codebase:

- Input reaching the network. The app makes exactly one category of network call,
  the ephemeral link-sharing relay, and nothing typed goes near it.
- Anything written to the personal dictionary or the clipboard history from a
  password, PIN, or a field the host marked no-personalized-learning.
  `FieldKind.isSensitive` and `IncognitoState` are the gates.
- Suggestions or glide decoding running on a credential field. These are read
  paths and are gated separately from learning, deliberately.
- The clipboard history persisting something Android marked sensitive.
- Data leaving the device through cloud backup. The backup scopes are
  include-only and `keyboard_database` appears in neither, which is what keeps
  the dictionary and clipboard off Google's servers.
- Sticker files reachable by another app through `StickerFileProvider`.
- Anything that would let an installed APK be replaced by one signed with a
  different key.

## What is not in scope

- Physical access to an unlocked device.
- Behaviour of the *host* app that FluxBoard is typing into.
- A keyboard being able to see typed input. That is what a keyboard is; the
  guarantee is about where that input goes, not that it is unseen.
- Findings from a scanner with no demonstrated impact. Say what an attacker gets.

## What the project does to keep this true

These run in CI, so they are properties rather than intentions:

- **Telemetry gate.** The release artifact is unpacked and scanned; any Play
  Services, ML Kit, Firebase or `datatransport` class in a DEX fails the build.
  ML Kit did once pull CCT logging into a release, which is why this is checked
  against the built APK rather than the dependency list.
- **Secrets guard.** Refuses a committed keystore, private key or access token,
  and fails if `.gitignore` stops covering the signing material.
- **Dependency review** on every pull request, failing on moderate-or-worse
  advisories, plus weekly Dependabot updates.
- **CodeQL** on the Kotlin sources, weekly and on every change to `main`.
- **`verifyNoUsageLoggingInRelease`** proves the tester usage log is absent from
  the release build -- absent as a class, not merely disabled by a flag.
- **Deployment integrity.** `deployment` must be an exact mirror of `main`, so a
  distributed build cannot contain a commit that never went through review.
