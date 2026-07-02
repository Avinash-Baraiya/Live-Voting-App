# Agent Guide

This repository uses a small context packet so new agents do not need to rediscover the full project each time.

Before making changes, read these files in order:
1. .ai/project-brief.md
2. .ai/decision-log.md
3. README.md
4. RUNBOOK.md

Working rules:
- Treat .ai/project-brief.md as the stable summary of the system.
- Record architecture or behavior decisions in .ai/decision-log.md.
- When any agent adds, changes, or removes a feature, it must update .ai/project-brief.md and .ai/decision-log.md in the same change.
- If the work is incomplete or creates follow-up work, update .ai/known-issues.md.
- New feature entries belong in the feature register section of .ai/project-brief.md so future agents can find the current state in one place.
- Do not change secrets or credentials in source files; prefer environment variables.
- Preserve the core voting flow: Redis first, duplicate vote prevention, rate limiting, and async persistence.
- Validate focused changes before expanding scope.

Hand-off standard:
- State the task, touched files, current risk, and next validation step.
- If the next agent needs context, point them to the .ai files instead of replaying the whole chat.