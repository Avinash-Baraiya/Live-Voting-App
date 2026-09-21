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
