# Findings log (raw, in the order found)

Each entry: what was run, what happened, which predicted weakness (W#) it confirms. REPORT.md is written from this.

## 2026-09-22 01:36 — first ramp stage (10 connections, 30 s), local PostgreSQL
- API accepted **346,636 votes in 30 s (~11,500/s)**, 0 non-2xx. Verified server-side:
  Redis voters set = 347,142 = PostgreSQL rows (26,496) + vote:queue (320,646). No votes lost in Redis.
- Persistence worker saved exactly 1,000 votes every ~5.1 s = **~195 votes/s**.
- Backlog after 30 s: **340,646** queued; projected time to reach PostgreSQL ≈ **29 minutes**.
- **Confirms W7** (persistence ceiling). The HTTP layer is ~59x faster than the path to PostgreSQL, so under any real
  burst PostgreSQL lags by minutes to hours, and everything in the queue is exposed to Redis data loss (W5) meanwhile.
- Measurement note: autocannon reported p50 139 ms at that rate, which is inconsistent with 10 connections at 11.5k/s.
  User-facing latency is therefore taken from an independent curl probe run alongside every load stage.

## 2026-09-22 01:39–01:43 — A1 baseline + A2 ramp (clean Redis, local PostgreSQL, load generator on the same Mac)
| connections | votes/s accepted | real-user vote latency p50/p90/max (curl probe) | non-2xx |
|---|---|---|---|
| 1 (A1) | 496 | 3 / 5 / 8 ms (autocannon) | 0 |
| 10 | 10,608 | 1 / 2 / 3 ms | 0 |
| 50 | 12,679 | 4 / 6 / 8 ms | 0 |
| 100 | 13,224 | 8 / 14 / 21 ms | 0 |
| 200 | 11,889 | 16 / 20 / 34 ms | 0 |
| 500 | 12,098 | 40 / 57 / 64 ms | 0 |
- API plateau ≈ **12–13k votes/s** from 50 connections up (CPU-bound; generator shares the CPU, so this is a lower bound). Latency grows linearly with concurrency, no errors up to 500 connections.
- `/results` at 1 connection: 496 req/s, p50 2 ms.
- After 2.5 minutes of load: **1,826,851 votes in vote:queue**, drain rate 200/s, projected **152 minutes** before PostgreSQL has them. **W7 confirmed.**
- autocannon "errors" (~500–1,700 per stage) are socket errors at stage end, not HTTP failures (0 non-2xx).
