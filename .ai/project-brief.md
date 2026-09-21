# Project Brief

## What this app is
Real-time voting backend for high-concurrency polls. Redis handles low-latency vote processing and PostgreSQL stores durable vote records.

## Stack
- Java 25
- Spring Boot
- Redis
- PostgreSQL
- Maven

## Core APIs
- POST /poll creates a poll
- POST /poll/{pollId}/vote records a vote
- GET /poll/{pollId}/results returns poll results

## Feature register
- 2026-07-02 | Real-time poll creation | active | src/main/java/com/avi/voting/controller/PollController.java, src/main/java/com/avi/voting/service/PollService.java | Creates a poll with options and expiry.
- 2026-07-02 | Live vote acceptance | active | src/main/java/com/avi/voting/controller/PollController.java, src/main/java/com/avi/voting/service/VoteService.java, src/main/java/com/avi/voting/redis/RedisVoteService.java | Accepts votes through Redis first for low-latency processing.
- 2026-07-02 | Result retrieval | active | src/main/java/com/avi/voting/controller/PollController.java | Returns live poll results.
- 2026-09-21 | k6 load testing | removed | scripts/k6/vote_test.js, RUNBOOK.md | Load-test script and runbook references deleted; no longer used.

## Feature update rule
- When a new feature is added, extend the feature register with the date, status, touched files, and a one-line summary.
- If the feature changes behavior or architecture, record the reason in the decision log.
- If the feature is unfinished, capture the follow-up in known-issues.

## Main runtime flow
1. Vote request hits PollController.
2. VoteService checks rate limits, poll state, and expiry.
3. RedisVoteService prevents duplicate votes and increments the live counter.
4. Vote event is pushed to vote:queue.
5. VoteFlushWorker drains the queue and persists votes to PostgreSQL.

## Important invariants
- Redis is the first source for live vote acceptance and counting.
- Duplicate votes must stay blocked for a given poll and user.
- Rate limiting is enforced before vote processing continues.
- Polls can be rejected if inactive or expired.
- DB persistence is async and should not break the live vote count path.

## Key files
- Controller: src/main/java/com/avi/voting/controller/PollController.java
- Vote service: src/main/java/com/avi/voting/service/VoteService.java
- Redis vote helpers: src/main/java/com/avi/voting/redis/RedisVoteService.java
- Rate limiter: src/main/java/com/avi/voting/redis/RateLimiterService.java
- Queue worker: src/main/java/com/avi/voting/worker/VoteFlushWorker.java
- Poll expiry worker: src/main/java/com/avi/voting/worker/PollExpirationWorker.java
- Config: src/main/resources/application.yaml
- Runbook: RUNBOOK.md

## Current risk notes
- Keep secrets out of source-controlled defaults.
- Reconcile Redis and PostgreSQL behavior carefully when changing vote or queue logic.
- If persistence or Redis operations fail, confirm the failure mode before widening changes.

## Stable editing rule
When changing behavior, prefer a small fix in the owning service or worker over a broad refactor.