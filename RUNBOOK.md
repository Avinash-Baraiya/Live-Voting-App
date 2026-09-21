# RUNBOOK — Live Voting App

Short runbook and failure scenarios for the Live Voting backend (Redis + PostgreSQL).

## Purpose
- Provide quick recovery steps and common failure scenarios.
- Short checklist for on-call engineers to triage and remediate incidents.

## Quick actions
- Start Redis (local):
```bash
docker run -p 6379:6379 --name redis -d redis
```
- Start app:
```bash
./mvnw spring-boot:run
```
- Check Redis queue length:
```bash
redis-cli LLEN vote:queue
```

## Important endpoints
- Create poll: `POST /poll`
- Vote: `POST /poll/{pollId}/vote`
- Results: `GET /poll/{pollId}/results`

## Redis key conventions
- Vote counter: `poll:{pollId}:option:{optionId}`
- Voter set: `poll:{pollId}:voters` (prevents duplicates via SADD)
- Persistence queue: `vote:queue` (entries: `pollId:userId:optionId`)
- Rate limiter: `rate_limit:{userId}`

## Failure scenarios (symptoms → immediate actions → remediation)

### 1) Redis unreachable / connection errors
- Symptoms: `503` on vote endpoints, logs show `RedisUnavailableException`.
- Immediate actions:
  - `redis-cli -h $HOST -p $PORT PING`
  - If using Docker: `docker ps` → `docker restart <container>`
  - Check `SPRING_REDIS_HOST`/`SPRING_REDIS_PORT` env vars.
- Remediation:
  - Restore Redis service; ensure password/ACLs match app config.
  - After restore, monitor `vote:queue` and let `VoteFlushWorker` drain backlog.
  - If queue backlog is very large, scale worker or run manual batch flush scripts.
- Prevention: use HA Redis (replica / sentinel or managed Redis), connection pooling and retries.

### 2) Redis write/increment failures (partial writes)
- Symptoms: voters present (set members) but counters inconsistent or missing.
- Immediate actions:
  - Inspect keys: `redis-cli GET poll:1:option:1`
  - Check application logs for `increment` errors.
- Remediation:
  - If counts are lost, recompute counters from DB votes (slow) or reconcile using persisted vote history.
  - Consider a script to rebuild Redis counters from DB: `SELECT option_id, count(*) FROM vote WHERE poll_id=? GROUP BY option_id`.
- Prevention: make increment + set operations atomic (Lua script) or ensure both operations are guarded.

### 3) `vote:queue` backlog (flush worker not keeping up)
- Symptoms: `LLEN vote:queue` grows; DB insert errors or backlog persists.
- Immediate actions:
  - Check `VoteFlushWorker` logs for exceptions.
  - Confirm DB connectivity with `psql`/`pg_isready`.
- Remediation:
  - If DB is healthy: scale/parallelize flush worker or temporarily increase `BATCH_SIZE`.
  - If DB is down: do not drop the queue — fix DB, then let worker process items.
  - For long backlogs consider pop-and-process script that uses `ON CONFLICT DO NOTHING` to avoid duplicates.
- Prevention: make DB writes idempotent (`ON CONFLICT DO NOTHING`), add backpressure/metrics on queue length.

### 4) PostgreSQL unavailable or slow
- Symptoms: flush worker errors, `saveAll` fails, errors in application logs.
- Immediate actions:
  - `pg_isready -h $DB_HOST -p $DB_PORT`
  - Check DB instance health, CPU, connections, and disk.
- Remediation:
  - Restore DB from replica or restart DB service.
  - After recovery, monitor for unique constraint violations while replaying the queue.
  - Use dedupe/upsert strategies when reprocessing queue.
- Prevention: set up replicas, connection pool limits, and alerts for connection errors/slow queries.

### 5) Duplicate votes observed in DB
- Symptoms: multiple `vote` rows for same `(user_id, poll_id)` despite Redis checks.
- Immediate actions:
  - Confirm unique constraint on `vote(user_id, poll_id)` still present.
  - Check replication lag, Redis failures, or network partition causing SADD to not persist.
- Remediation:
  - Run a dedupe SQL script (example):
```sql
DELETE FROM vote a
USING vote b
WHERE a.id > b.id
  AND a.user_id = b.user_id
  AND a.poll_id = b.poll_id;
```
  - Consider adding `ON CONFLICT DO NOTHING` for batch inserts.
- Prevention: use atomic Redis Lua script for addVoter+increment, add stricter idempotency in DB writes.

### 6) Background worker crash or stuck
- Symptoms: no DB persistence, queue grows, exceptions in scheduler logs.
- Immediate actions:
  - Restart the JVM/service hosting the worker.
  - Check logs: `tail -n 200 <app-logs>`.
- Remediation:
  - Fix root cause (e.g., OOM, SQL errors); manually run a safe replayer for queue items.
- Prevention: add process supervisor (systemd/k8s) and health checks; alert on worker errors.

### 7) Rate limiter misconfiguration (false positives)
- Symptoms: many `429` responses; legitimate users blocked during burst traffic.
- Immediate actions:
  - Inspect `rate_limit:*` keys: `redis-cli KEYS 'rate_limit:*'` and sample values.
  - Temporarily raise `LIMIT` or window size if safe.
- Remediation:
  - Adjust limit/window or implement a smoother token-bucket/sliding-window algorithm.
- Prevention: tune limits for production traffic patterns and document rate-limiter behavior.

## Safe recovery checklist (short)
1. Identify whether Redis or DB is the failing component.
2. If Redis down: bring Redis up; do not purge `vote:queue`.
3. If DB down: fix DB, then monitor queue drain and dedupe if necessary.
4. If duplicate entries suspected: run dedupe SQL and reconcile counters.
5. After recovery, run smoke tests (create poll, vote, fetch results).

## Monitoring & alerts (suggested)
- Alert if `LLEN vote:queue` > 10k for > 5 minutes.
- Alert on Redis connection errors or `RedisUnavailableException` rate spike.
- Alert on 5xx rate in production > 1%.
- Track `vote` endpoint p95 latency, p99 latency, and error rates.

## Short-term improvements (recommended)
- Make DB inserts idempotent (`ON CONFLICT DO NOTHING`).
- Replace Redis-list queue with durable broker (Kafka) for replayability.
- Add metrics (Prometheus) and tracing (Jaeger) for end-to-end visibility.

## Useful file references
- Controller: [src/main/java/com/avi/voting/controller/PollController.java](src/main/java/com/avi/voting/controller/PollController.java)
- Vote worker: [src/main/java/com/avi/voting/worker/VoteFlushWorker.java](src/main/java/com/avi/voting/worker/VoteFlushWorker.java)
- Redis helpers: [src/main/java/com/avi/voting/redis/RedisVoteService.java](src/main/java/com/avi/voting/redis/RedisVoteService.java)

## Author / on-call
- Avinash
