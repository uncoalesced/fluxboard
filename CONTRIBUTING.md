# Contributing

Bug reports are worth more to this project right now than patches. It is a beta, it
runs on one test phone, and most of what is still wrong will be found by someone
using it on hardware we do not have.

## Reporting a bug

Use the [issue templates](https://github.com/uncoalesced/fluxboard/issues/new/choose).
There is a general one, one specifically for keyboard behaviour, and one for ideas.

The keyboard template asks which app you were typing into, and that is not filler.
An IME's behaviour depends heavily on what the host app declares about its text
field, and "autocorrect did not fire" has a completely different cause in a browser
than in a chat app.

Two things make a report immediately useful: the exact version string from the app's
settings screen, and what you expected instead of what happened. Screenshots are
welcome; redact anything you would not post publicly, since a keyboard screenshot
tends to contain whatever you were typing.

Anything that could expose typed input goes through
[SECURITY.md](SECURITY.md) privately instead.

## Building

JDK 17 or newer, Android SDK for API 36.

```bash
./gradlew assembleDebug     # debug APK
./gradlew build             # the full gate: ktlint, all unit tests, lint, release build
```

`./gradlew build` is what CI runs. If it passes locally it will almost certainly
pass there.

Some notes that will save you time:

Build clean before believing a green build. Incremental compilation caches
`compileDebugKotlin` as up to date and will hide a broken interface implementation
until a clean build catches it. Adding a method to `KeyboardController` in
particular compiles fine incrementally and then fails, because that interface has
five implementors and two of them are in tests.

Unit test tasks go up to date and leave stale XML behind, which reads as a pass. Use
`--rerun` and check that results are freshly dated before believing them.

ktlint baselines are per module, not one file at the root, and entries are matched by
line number. Inserting anything above a baselined violation re-triggers it, so
regenerating a baseline after a large change is routine rather than suspicious. Look
at the violation before baselining it, though. A fully-qualified reference to a class
the file already imports is a real fix that removes the entry permanently.

## House rules

Three of these are not negotiable, because the project's whole pitch depends on
them.

**Nothing may add telemetry, and nothing may add a Play Services dependency.**
Including transitively, which is the case that actually bites. ML Kit was removed
from this project for exactly this reason: it pulls Google's CCT logging transport
with it. CI unpacks the release APK and fails on any Play Services, ML Kit, Firebase,
or `datatransport` class, so a dependency that drags one in will not merge. Check what
a library brings before adding it.

**Every source file carries `// Engineered by uncoalesced`** near the top, or the
equivalent comment syntax for the file type. `scripts/check-source-rules.sh` enforces
it and runs in CI.

**No emoji anywhere in the codebase.** Not in comments, strings, commit messages, log
output, or identifiers. Same script enforces it.

Beyond those:

- Kotlin only inside the Android modules. Python is fine for the offline dictionary,
  emoji, and icon tooling.
- Standard Kotlin style as ktlint enforces it. Do not introduce a second convention.
- Sealed classes over string constants or boolean pairs for anything with a fixed set
  of states.
- Suspend functions and Flow for async work. If you wrap a callback-based library,
  wrap it at the boundary.
- New logic in the data layer or any pipeline gets a unit test in the same change
  that introduces it.
- Do not register a `SharedPreferences.OnSharedPreferenceChangeListener`. There is a
  CI check that rejects it, with the reason in the failure message: they are held
  weakly, R8 deletes the field keeping them alive, and the result is a release build
  where no setting takes effect. Preference setters publish to their own flow instead.

## Commits

Keep each commit compiling. Write messages that explain why, not what, because the
diff already says what. Where a change is not obviously correct, say what you checked
and what you could not.

If a change fixes something a test could have caught, add the test in the same commit
and confirm it fails without the fix. That last part matters more than it sounds: this
project has shipped guards that could not fail.

Do not add `Co-Authored-By` trailers for AI tools. If you used one, that is fine and
[AUTHORS.md](AUTHORS.md) says so, but authorship on commits stays human. A
`commit-msg` hook in `.githooks/` strips those trailers; enable it with:

```bash
git config core.hooksPath .githooks
```

## Pull requests

Target `main`. CI must be green: ktlint, unit tests, lint, the release build, the
telemetry gate, the secrets guard, CodeQL, and dependency review.

Small and focused beats large and sweeping. If a change touches the IME's gesture
handling, the recomposition behaviour, or the privacy gates, say in the description
what you did to convince yourself it still works, because those three areas have all
regressed silently before and unit tests did not catch any of them.

## What is deliberately out of scope

So that a rejection is not a surprise:

- Automatic subject segmentation, unless it comes with a genuinely open on-device
  model in the repository. A stand-in that keeps the name of the real feature has been
  written here once already and will not be again.
- Anything requiring `READ_SMS`, `BIND_NOTIFICATION_LISTENER_SERVICE`, or a
  package-name heuristic to decide how to behave. The first two grant far more than
  the feature needs; the third was explicitly rejected when the privacy model was
  designed.
- A second copy of state that already has a single source of truth. Incognito, the
  panel height, and the key glyph table each exist in exactly one place, and each of
  them got there by consolidating duplicates that had drifted apart.
