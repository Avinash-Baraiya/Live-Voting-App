# AI Context Management

This folder is the lightweight memory layer for the project.

Use these files as the shared context packet:
- project-brief.md: stable summary of the app, architecture, and invariants.
- decision-log.md: important architecture or behavior decisions.
- known-issues.md: recurring problems, risks, and follow-up items.

How to use it:
1. Read project-brief.md first.
2. Review decision-log.md before changing behavior.
3. Update project-brief.md and decision-log.md when behavior changes.

Good handoff payload for a new agent:
- goal
- files touched
- important behavior rules
- validation performed
- open risks

Keep entries short. The goal is stable context, not long chat transcripts.