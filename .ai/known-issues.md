# Known Issues

## Context and maintenance
- The repository needs updates in the .ai files when behavior or priorities change.
- New feature work should be appended to the feature register in .ai/project-brief.md and reflected here when it creates follow-up work.

## Technical risk
- An old Supabase database password is in the git history of application.yaml. Supabase is no longer used (local only since 2026-10-07); delete or reset that Supabase project so the old password is useless.

## Confirmed bottleneck (measured 2026-09-22)
- Persistence ceiling: VoteFlushWorker saves ~200 votes/s while the API accepts ~12-13k/s, so vote:queue grows without bound under load and PostgreSQL lags by minutes to hours.
- Causes: one LPOP per vote, saveAll with GenerationType.IDENTITY (Hibernate batching disabled), and a fixed 5 s delay between flushes.
- Everything queued is lost if Redis dies, because LPOP removes a vote before the DB commit.
- A failed batch is re-queued whole, so one duplicate can make the same batch fail on every retry.

## Future improvements
- The confirmed bottleneck and data-loss issues above are addressed by .ai/kafka-migration-plan.md (not started).
- Fix the persistence path first: bulk LPOP, JDBC batch insert with ON CONFLICT DO NOTHING, drain until empty.
- Move vote:queue to Redis Streams (XADD/XREADGROUP/XACK) so votes survive a crash.
- Resilience test groups B-E (consistency, Redis/PostgreSQL failures, app crash) and A4-A6 were interrupted and need a clean re-run; see resilience-tests/PROGRESS.md.
- Consider stronger durability for vote events if the queue becomes a bottleneck.
- Add a stricter review rule for agent handoffs so feature deltas cannot be skipped.