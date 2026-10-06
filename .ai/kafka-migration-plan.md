# Kafka-first migration plan (design B)

Status: approved 2026-10-03, not started. Work happens on `main`, one commit per phase.
Read this before changing the vote, queue, persistence or infrastructure code.

## Why
Measured on the current Redis-first design (resilience-tests/FINDINGS.md):
- The vote API accepts ~12-13k votes/s, but VoteFlushWorker saves only ~200 votes/s to PostgreSQL.
- After 2.5 minutes of load, 1.8M votes were waiting in `vote:queue` (~152 minutes behind).
- Votes can be lost: `LPOP` removes a vote before it is saved; Redis has no persistence configured.
- The Redis count and the queue push are separate writes (dual-write problem).
- `userId` comes from the request body, so it can be forged.

Design B makes Kafka the record of truth and derives everything else from it.

## Target architecture
```
POST /poll/{id}/vote
  -> API (stateless, N instances): JWT user, rate limit, poll active + option valid (local cache),
     fast duplicate pre-check (Redis SISMEMBER)
  -> Kafka topic "votes" (12 partitions, 7 days retention), key = pollId:userId, acks=all, idempotent producer
  -> consumer group "vote-counter":   Lua SADD voters + HINCRBY counts  -> Redis (live results)
  -> consumer group "vote-persister": batch INSERT ... ON CONFLICT DO NOTHING -> PostgreSQL
GET /poll/{id}/results reads Redis; if Redis data is lost, rebuild from PostgreSQL or replay Kafka.
```

## Design decisions
- **D1** Kafka is the record of truth. A vote is accepted once Kafka confirms it (`acks=all`, `enable.idempotence=true`).
- **D2** Message key = `pollId:userId`. Same user + poll always lands in the same partition (exact dedupe, ordered); a hot poll is spread over all partitions (no hot partition).
- **D3** The vote API returns `202 Accepted`. A Redis `SISMEMBER` pre-check returns `409` to repeat voters; the consumer makes the final decision. Never reserve the voter in Redis before producing (that recreates the dual-write problem).
- **D4** At-least-once delivery with idempotent consumers: Redis uses one Lua script (`SADD` == 1 then `HINCRBY`), PostgreSQL uses the `(user_id, poll_id)` unique key with `ON CONFLICT DO NOTHING`. Both keep the first vote, so they always agree.
- **D5** Offsets are committed only after the write succeeds.
- **D6** Poll status/expiry is checked in the API (Caffeine cache, 5-10 s TTL) and authoritatively in the consumer using the event timestamp.
- **D7** Graded dependencies. Kafka down: `503` fast (circuit breaker), no fallback queue. Redis down: votes still accepted with a per-instance rate limit (Bucket4j), results `503`. PostgreSQL is not on the vote path. Never fall back to writing votes straight to PostgreSQL.
- **D8** After 3 retries with backoff a message goes to `votes.DLQ`; it never blocks a partition.
- **D9** Development and all testing run locally in Docker (Redis, PostgreSQL, Kafka, monitoring). Every backend is still chosen by environment variables only, never by code, so a managed service can be added later without code changes. (Revised 2026-10-07: the hybrid/cloud presets with Supabase and Redis Cloud were removed; see the decision log.)

## Environments
Everything runs on this machine via `docker-compose.yml` profiles; start only what you need:

| Profile | Service | Address |
|---|---|---|
| `redis` | `redis:8`, AOF on | `localhost:6379` |
| `postgres` | `postgres:16` (db/user/password `voting`) | `localhost:5432` |
| `kafka` | `apache/kafka` KRaft single node + Kafka UI | `localhost:9092`, UI http://localhost:8090 |
| `monitoring` | Prometheus + Grafana | http://localhost:9090, http://localhost:3000 |

- Settings live in `.env.local` (gitignored; template `.env.local.example`).
- `./run.sh local [--fast]` starts Redis + PostgreSQL, checks them, runs the app; `./run.sh smoke` checks a running app; `./run.sh stop` stops all containers.
- `/actuator/info` shows which servers the app uses (host only, never credentials).
- A single local broker cannot show replication; production would use replication factor 3 and `min.insync.replicas=2`.

## File changes
New: `docker-compose.yml`, `.env.*.example`, `run.sh`, `config/KafkaConfig.java` (topics, DLQ, SASL/SSL from env), `config/BackendInfoContributor.java`, `messaging/VoteEvent.java`, `messaging/VoteProducer.java`, `consumer/VoteCounterConsumer.java`, `consumer/VotePersistenceConsumer.java`, `redis/lua/count_vote.lua`, `cache/PollCache.java`, `service/ResultRebuildService.java`, `health/KafkaHealthIndicator.java`, `security/*` (JWT).

Modified: `pom.xml` (spring-kafka, Caffeine, Resilience4j, Bucket4j, Micrometer Prometheus, Testcontainers), `application.yaml`, `VoteService`, `PollController` (202, user from JWT), `VoteRequest` (drop userId), `RedisVoteService` (pre-check + hash counts), `RateLimiterService` (one Lua call + local fallback), `PollService` (results from `poll:{id}:counts`), `Vote` (add unique `eventId`), `PollExpirationWorker` (ShedLock), `GlobalExceptionHandler`, `postman/*`.

Removed: `VoteFlushWorker`, `VoteQueueHealthIndicator`, the `vote:queue` list, per-option string counters.

Docs: `AGENTS.md` ("Redis first" becomes "Kafka first; Redis is a derived view"), `.ai/project-brief.md`, `.ai/decision-log.md`, `RUNBOOK.md`, `README.md`.

## Phases (each ends with a test that must pass)
| # | Phase | Done when |
|---|---|---|
| 0 | Environments + baseline: compose profiles, `.env.local`, `run.sh`, backend info, Redis AOF, Prometheus/Grafana, SLOs, re-run resilience groups B-E on the current design | current app runs locally via `run.sh`, smoke test and Postman pass, Grafana shows the bottleneck, "before" numbers in FINDINGS.md |
| 1 | Producer: VoteEvent, VoteProducer, PollCache, new VoteService, 202, `VOTE_PIPELINE=redis\|kafka` switch | local: 10k votes/s reach Kafka with 0 failures, one poll spread over all 12 partitions |
| 2 | Counter consumer: Lua dedupe + count, results from hash, rebuild job | 1M votes with 20% duplicates give exact counts; consumer killed mid-run still exact; rebuild after a Redis wipe matches PostgreSQL |
| 3 | Persistence consumer: JDBC batch, ON CONFLICT, DLQ, expiry check | local drain rate >= produce rate (vs 200/s); consistency_check.py: Redis = PostgreSQL = unique users |
| 4 | Failure hardening: circuit breaker, local rate-limit fallback, ShedLock, lag alerts, rewrite resilience groups C/D/E | every row of the failure table passes on local |
| 5 | Security: JWT, userId from token | forged user IDs impossible; repo secret scan clean |
| 6 | Scale-out: 3 instances behind nginx, 12 consumers, hot-poll test | throughput scales to a documented limit |
| 7 | Cut over + CI: remove old path, docs, graphify refresh, GitHub Actions + Testcontainers | CI green; Postman run passes |

SLOs (local): p99 < 50 ms at 10k votes/s, 0 accepted votes lost, results lag < 1 s, database lag < 10 s.

## Failure table (run on `local`)
| Failure | Expected behaviour |
|---|---|
| Kafka down | votes `503` within 1 s; nothing accepted is lost |
| Redis down | votes still accepted (local rate limit); results `503`; counts rebuilt after |
| Redis wiped | rebuild restores exact counts |
| PostgreSQL down 10 min | voting and live counts unaffected; lag grows then fully catches up |
| Consumer killed mid-batch | re-delivered; no double counts, no gaps |
| API instance killed | load balancer routes around it; in-flight requests saved or cleanly failed |
| Poisoned message | goes to `votes.DLQ`; partition keeps moving |
| Duplicate storm | exactly one vote per user |
| Hot poll | load spread over all partitions |
| Vote after expiry (stale cache) | rejected by the consumer |

## Accepted costs
- More infrastructure to run and monitor.
- Results lag ~100 ms-1 s; the client gets `202`, not a final answer; a raced duplicate gets `202` but is not counted.
- Higher per-vote latency (`acks=all`).
- A single local Kafka broker cannot show replication; use replication factor 3 / `min.insync.replicas=2` in real deployments.

## Postponed until measurements demand them
Table partitioning, sharding, Kubernetes, distributed tracing, API gateway.
