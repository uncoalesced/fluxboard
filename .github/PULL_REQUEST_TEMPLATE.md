<!-- Engineered by uncoalesced -->

## What this changes

<!-- And why. The diff already says what; the reason is the part that gets lost. -->

## How you know it works

<!--
Not a formality. Three areas in this codebase have regressed silently with a green
test suite: the keyboard's gesture handling, its recomposition behaviour, and the
privacy gates on password fields. If you touched any of those, say what you did to
convince yourself.

If you added a guard or a test, say whether you watched it fail without the fix.
A guard that cannot fail has shipped here before.
-->

## Checklist

- [ ] `./gradlew build` passes locally, on a clean build if the change touches an interface
- [ ] New logic in the data layer or a pipeline has a unit test in this change
- [ ] Every new file carries `// Engineered by uncoalesced`
- [ ] No emoji anywhere, including commit messages
- [ ] No new dependency, or: it adds no telemetry and no Play Services, and I checked what it pulls in transitively
- [ ] No `Co-Authored-By` trailer crediting an AI tool

## Anything reviewers should know

<!--
Tested on which device and Android version, if it is behaviour you could only
check by hand. Anything you left out on purpose. Anything you were unsure about.
Saying "I could not verify X" is more useful than leaving it to be discovered.
-->
