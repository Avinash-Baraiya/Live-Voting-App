# Agent Guide

This repository uses a small context packet so new agents do not need to rediscover the full project each time.

Before making changes, read these files in order:
1. .ai/project-brief.md
2. .ai/decision-log.md
3. README.md
4. RUNBOOK.md
5. resilience-tests/FINDINGS.md (measured throughput and failure behavior)
6. .ai/kafka-migration-plan.md (approved move to a Kafka-first design: decisions D1-D9, phases, environments)

Working rules:
- Treat .ai/project-brief.md as the stable summary of the system.
- Record architecture or behavior decisions in .ai/decision-log.md.
- When any agent adds, changes, or removes a feature, it must update .ai/project-brief.md and .ai/decision-log.md in the same change.
- If the work is incomplete or creates follow-up work, update .ai/known-issues.md.
- New feature entries belong in the feature register section of .ai/project-brief.md so future agents can find the current state in one place.
- Do not change secrets or credentials in source files; prefer environment variables.
- Preserve the core voting flow: Redis first, duplicate vote prevention, rate limiting, and async persistence. This is being replaced phase by phase by the Kafka-first design in .ai/kafka-migration-plan.md; follow that plan's decisions for any vote, queue or persistence change, and update this rule when the cut-over (phase 7) lands.
- Validate focused changes before expanding scope.
- Before changing the vote or persistence path, read the measured numbers in resilience-tests/FINDINGS.md instead of re-measuring; re-run the harness only to confirm a change.
- Never commit per-run test output (resilience-tests/results/, .run/, node_modules/); keep conclusions in FINDINGS.md.

Hand-off standard:
- State the task, touched files, current risk, and next validation step.
- If the next agent needs context, point them to the .ai files instead of replaying the whole chat.