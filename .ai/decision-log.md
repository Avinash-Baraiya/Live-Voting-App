# Decision Log

## 2026-07-02
- Standardized repo context around a small .ai folder plus AGENTS.md.
- Kept the project brief focused on Redis-first voting, duplicate prevention, rate limiting, and async persistence.
- Captured that secret values should not be treated as stable project knowledge and should be moved to environment-based configuration.
- Added a feature register to the project brief so future agents have a single append-only place for feature changes.
- Required any feature change to be mirrored into the brief, current task, decision log, and future-improvement notes when relevant.

## 2026-09-21
- Removed k6 load testing (scripts/k6/vote_test.js) and its RUNBOOK references; it is no longer part of the project's tooling.
- Removed hardcoded datasource/Redis credential fallbacks from application.yaml; all secrets now come from environment variables (template in .env.example) and startup fails fast if datasource values are missing.
- Moved Redis settings to spring.data.redis.* because Spring Boot 3+ no longer binds spring.redis.*; existing SPRING_REDIS_* env var names are kept.
- Added vote option validation backed by a Redis set per poll (poll:{pollId}:options) so the check adds one Redis call, not a DB query, per vote.
- Added Spring Boot Actuator health checks (db, redis, voteQueue); details are hidden unless HEALTH_SHOW_DETAILS=always so public servers do not expose them.
- Work is committed directly to main; no pull requests.

## 2026-10-03
- Load and failure testing lives in resilience-tests/: scripts and findings are committed, per-run output (results/, .run/, node_modules/) is git-ignored because every run regenerates it.
- Measured numbers are recorded in .ai/project-brief.md and resilience-tests/FINDINGS.md so agents do not re-measure before changing the vote path.
- Approved moving to a Kafka-first design (Kafka as the record of truth; Redis and PostgreSQL derived by idempotent consumers). Decisions D1-D9, environments (local Docker or cloud per service) and phases are in .ai/kafka-migration-plan.md.

## 2026-10-07
- Development and testing run locally only, in Docker (docker-compose.yml profiles: redis, postgres, kafka, monitoring), started through run.sh with settings from .env.local. The hybrid/cloud presets (Supabase, Redis Cloud) were tried and removed: a vote took ~1.7 s on cloud backends (Supabase in Sydney ~410 ms round trip from India) and free tiers cannot be load-tested. Their data was wiped. Configuration stays environment-variable driven, so a managed service can be added later without code changes.
- Added Prometheus metrics (micrometer-registry-prometheus, /actuator/prometheus, request latency histograms, vote.queue.size gauge, votes.persisted counters) and a provisioned Grafana dashboard, so throughput and the persistence gap are visible live.
- Redis database number is configurable (SPRING_REDIS_DATABASE, default 0).
