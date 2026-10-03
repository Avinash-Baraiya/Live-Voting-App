# Resilience test progress

Goal: measure how the **current version** (commit `dbb82dc`) behaves under load and failure,
confirm or reject the predicted weaknesses (W1–W12 in REPORT.md), then fix and re-run for a before/after comparison.

Test machine: Apple M4, 10 cores, 16 GB, macOS. Local PostgreSQL 14 (`voting_test` DB), local Redis 8.
Heavy load runs against local PostgreSQL, not Supabase (free-tier limits, clean numbers).

## Status

| Phase | Item | Status |
|---|---|---|
| 0 | Folder, scripts (lib.sh, load.js, consistency_check.py), tools | done |
| 1 | A1 baseline | done |
| 1 | A2 ramp | done (10–500 conns) |
| 1 | A3 sustained (5 min) | skipped: A2 already shows unbounded queue growth |
| 1 | A4 hot poll / A5 read-heavy | todo |
| 1 | A6 Supabase comparison | todo |
| 2 | B1–B6 consistency | interrupted 2026-09-22 01:45, re-run fresh |
| 3 | C Redis failures | interrupted 2026-09-22 01:45, re-run fresh |
| 3 | D PostgreSQL failures | interrupted 2026-09-22 01:45, re-run fresh |
| 3 | E app crash / downtime | interrupted 2026-09-22 01:45, re-run fresh |
| 4 | REPORT.md | todo |

## Stopped for the day (2026-09-22 01:45)
All test stacks, the app, Redis, PostgreSQL and toxiproxy were shut down. Partial output from the
interrupted B–E runs in `results/B`–`results/E` is incomplete: delete it and re-run those groups.

## How to resume
1. `brew services start postgresql@14` (Redis test instances are started by the scripts themselves)
2. `cd resilience-tests && rm -rf results/B results/C results/D results/E .run`
3. Run groups in parallel: `for g in b_consistency c_redis_failures d_postgres_failures e_app_crash; do nohup bash scripts/$g.sh > results/$g.log 2>&1 & done`
4. Then A4/A5/A6, then write REPORT.md from FINDINGS.md.
Scripts must be run with `bash` (not zsh).
Raw results are in `results/`; each file name starts with the test ID.
