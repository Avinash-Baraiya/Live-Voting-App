# AI Context Guide

This document is the visible handoff guide for anyone working on the repository later.

## What this project is
Real-time voting backend for high-concurrency polls. Redis handles low-latency vote processing and PostgreSQL stores durable vote records.

## Stable behavior
- Redis is the first source for live vote acceptance and counting.
- Duplicate votes must stay blocked for a given poll and user.
- Rate limiting is enforced before vote processing continues.
- Polls can be rejected if inactive or expired.
- DB persistence is async and should not break the live vote count path.

## Main flow
1. PollController receives the vote request.
2. VoteService checks rate limits, poll state, and expiry.
3. RedisVoteService prevents duplicate votes and increments the live counter.
4. The vote is pushed to `vote:queue`.
5. VoteFlushWorker drains the queue and persists votes to PostgreSQL.

## Where to look first
- [AGENTS.md](AGENTS.md) for the repository-wide agent rules.
- [.ai/project-brief.md](.ai/project-brief.md) for the canonical project summary and feature register.
- [.ai/decision-log.md](.ai/decision-log.md) for architecture and behavior decisions.
- [.ai/known-issues.md](.ai/known-issues.md) for follow-up work and recurring risks.
- [README.md](README.md) for setup and runtime notes.
- [RUNBOOK.md](RUNBOOK.md) for operational guidance.

## Feature workflow
- Add new features to the feature register in `.ai/project-brief.md`.
- Record behavior or architecture changes in `.ai/decision-log.md`.
- Put follow-up work in `.ai/known-issues.md`.

## Future improvements already identified
- Use Kafka for durable, replayable event streaming.
- Add WebSocket or SSE for live client updates.
- Add observability such as metrics, tracing, and alerts.

## Quick start
1. Start Redis.
2. Copy `.env.example` to `.env` and fill in PostgreSQL/Redis values (never commit `.env`).
3. Run `source .env && ./mvnw spring-boot:run`.

## If you are adding a feature
- Keep the change narrow and update the owning service or worker first.
- Preserve the Redis-first vote flow.
- Update the feature register and decision log in the same change.
- Add any follow-up work to known issues so the next person can continue quickly.