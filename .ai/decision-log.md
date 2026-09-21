# Decision Log

## 2026-07-02
- Standardized repo context around a small .ai folder plus AGENTS.md.
- Kept the project brief focused on Redis-first voting, duplicate prevention, rate limiting, and async persistence.
- Captured that secret values should not be treated as stable project knowledge and should be moved to environment-based configuration.
- Added a feature register to the project brief so future agents have a single append-only place for feature changes.
- Required any feature change to be mirrored into the brief, current task, decision log, and future-improvement notes when relevant.

## 2026-09-21
- Removed k6 load testing (scripts/k6/vote_test.js) and its RUNBOOK references; it is no longer part of the project's tooling.
