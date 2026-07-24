---
trigger: always_on
description: How work moves between Joel and Rahul over Git — a single shared branch traded back and forth, and who does which part of it
---

# Collaboration Workflow

This project is built by two people trading control of one shared branch,
not by working in parallel on separate branches. The cycle:

1. One person runs some number of phases in their own agent session.
2. They review the changes, commit, and push to the shared branch
   themselves.
3. The other person pulls, and continues from exactly where that left off.
4. Repeat, back and forth, for the life of the project.

**The agent's job is building, not committing or pushing.** Editing
files, running builds and tests, and reporting results are the agent's
responsibility. `git commit` and `git push` are not — those stay with
whichever person is driving the session, every time, with no exception
for convenience. Never run either without being explicitly told to for
that specific instance.

## At the start of every session

- Checking for incoming changes (e.g. `git status`, `git fetch`) is fine
  and encouraged — flag anything unexpected before starting new work. But
  don't assume you should also pull unless asked; pulling is the driving
  person's call, since they may be about to commit something of their own
  first.
- If the working tree isn't clean (uncommitted changes left over from a
  previous, possibly-interrupted session), stop and ask rather than
  building on top of an unclear base — don't clean it up yourself by
  committing or stashing it.

## At the end of every session

- Don't leave a phase half-done if it can be avoided — finish the current
  phase, or clearly flag exactly what's incomplete, before stopping. The
  person needs to know precisely what state things are in before they
  decide what to commit.
- Confirm the build is green locally and report the result. Do not commit
  and do not push, even if everything passes — say it's ready, and let the
  person handle git themselves.
- Summarize what changed in plain terms (which phase(s), which files) so
  the person can write their own commit message with full context, rather
  than having to reconstruct it from a diff.

## Conflicts

Because this is a serialized handoff rather than parallel branches, real
conflicts should be rare — but when one turns up, surface it plainly and
explain the conflicting changes in plain terms. Don't resolve it
yourself; that's a git operation too, and it's the person's call how to
reconcile it. Rahul is newer to Kotlin than Joel; don't assume either
person can eyeball a conflict and immediately spot the right resolution
without it being explained first.
