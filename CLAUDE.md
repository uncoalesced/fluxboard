@AGENTS.md
@.agent/rules/privacy-and-scope.md
@.agent/rules/code-conventions.md
@.agent/rules/collaboration-workflow.md

## Claude Code

This project's primary agentic-coding setup was written for Antigravity
(`AGENTS.md` + `.agent/rules/` + `.agent/workflows/`). The imports above
mean this file, not a rewrite, is the bridge — Claude Code loads the same
source of truth rather than a duplicate copy that can drift out of sync.

Current focus: **fixing existing code, not building new phases.** A
static audit of Phases 1-17 found two fabricated implementations behind
plausible-looking names (see the "Substantive completion" rule in
`code-conventions.md` — it exists because of this incident specifically),
plus rule violations and zero test coverage. The remediation prompts are
in `docs/agent-prompts.md` under "Remediation Prompts." Work through
those; don't start new `.agent/workflows/` phases unless told to.

For the two hardest fixes (the fake segmentation engine, the missing
WebP/MIME-type bug) — plan before editing, and don't mark either done
without independently confirming the specific claim (the real dependency
is actually declared and called, the output file is actually a valid
animated WebP) rather than trusting that a plausible diff means it works.
