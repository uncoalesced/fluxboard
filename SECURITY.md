# Security policy

FluxBoard is a keyboard. It sees every character you type, including passwords and
one-time codes. That is the threat model, and it is why this project's position is
zero telemetry rather than minimal telemetry.

## Reporting something

Use GitHub's private vulnerability reporting on this repository, under Security,
then "Report a vulnerability". Please do not open a public issue for anything that
would expose typed input, stored credentials, or the personal dictionary. A public
issue describing how to read someone's keystrokes is a working exploit with
instructions.

Include the version string from the app's settings screen, your Android version,
and the shortest set of steps that shows the problem. A screenshot of the keyboard
leaking something is useful. A screenshot containing your own credentials is not,
so redact it.

Expect an acknowledgement within about a week. Two people work on this in their
spare time, so that is a realistic number rather than an ambitious one.

## Supported versions

Pre-1.0, only the newest release gets fixes. That is `v0.1.5-BETA`. Older alpha
builds are not patched; update instead.

## In scope

Anything that lets typed input escape the device, or reach an app that should not
see it. Each of these has a real mechanism behind it in this codebase, so a report
against one of them is actionable:

- Typed input reaching the network. The app makes no network call while typing at
  all.
- Anything written to the personal dictionary or the clipboard history from a
  password field, a PIN field, or a field the host marked as no-personalized-learning.
- Suggestions, autocorrect, or glide decoding running on a credential field. These
  are read paths and are gated separately from learning, on purpose.
- The clipboard capturing something Android flagged as sensitive.
- Data leaving the device through cloud backup. The backup scopes are include-only
  and `keyboard_database` is in neither, which is the whole protection.
- Sticker files being reachable by another app through the content provider.
- Anything that would let an APK signed with a different key install over a real
  one.

## Out of scope

- Physical access to an unlocked phone.
- What the app you are typing into does with the text after it receives it.
- A keyboard being able to see typed input. That is what a keyboard is. The
  guarantee is about where that input goes, not that it goes unseen.
- Scanner output with no demonstrated impact. Tell us what an attacker actually
  gets.

## What keeps this honest

These run in CI on every build, so they are properties of the artifact rather than
intentions:

The release APK is unpacked and scanned for Play Services, ML Kit, Firebase, and
Google's `datatransport` logging library, in both its DEX files and R8's mapping.
The mapping is the stronger half, because a release build is obfuscated and a
renamed dependency need not leave its original name in any DEX. The check carries a
positive control, so if the scan itself breaks it fails rather than reporting a
pass. This exists because ML Kit did once drag a telemetry transport into a release
build of this app.

The tester usage log is proven absent from release builds as a class. A runtime
flag cannot make a class stop existing, so the interface lives in the main source
set and the real implementation only in the debug one.

A secrets guard refuses a committed keystore, private key, or access token, and
fails if `.gitignore` stops covering the signing material. That last part catches
the edit before the mistake rather than after it.

Dependency review runs on every pull request and fails on moderate-or-worse
advisories. CodeQL runs on the Kotlin sources weekly and on every change to `main`.
Dependabot opens grouped weekly updates.

`deployment`, the branch a distributed build is cut from, must be an exact mirror
of `main`. A separate job says so out loud if it ever is not, because a build cut
from a diverged branch would ship a commit that never went through review.

## Known gaps

Written down rather than left for someone to find:

- No runtime traffic capture has been done on a physical device. The static and
  artifact-level checks above are not a substitute, and
  [docs/privacy-audit.md](docs/privacy-audit.md) says so too.
- Release signing depends on one key held outside the repository. Losing it breaks
  in-place updates for everyone on a release build.
- The instrumented test suite compiles but has never been executed, because it
  needs a physical device or emulator in CI.
