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
- **D9** Every backend (Redis, PostgreSQL, Kafka, monitoring) is chosen by configuration only: local Docker or managed cloud. No code branches on environment. Performance and failure numbers count only on the `local` preset.

## Environments
| Service | Local (Docker) | Cloud | Cloud-specific settings |
|---|---|---|---|
| Redis | `redis:8`, AOF on | Redis Cloud | TLS, password, small pool |
| PostgreSQL | `postgres:16` | Supabase | Session pooler (IPv4, port 5432; not the 6543 transaction pooler), pool 5-10 |
| Kafka | `apache/kafka` KRaft single node + Kafka UI | Confluent Cloud / Aiven / Redpanda | SASL_SSL key + secret, replication factor 3, topics created up front |
| Monitoring | Prometheus + Grafana | Grafana Cloud (optional) | remote-write URL + key |

| Preset | Redis | PostgreSQL | Kafka | Use |
|---|---|---|---|---|
| `local` | Docker | Docker | Docker | development, all load and failure tests |
| `hybrid` | Docker | Supabase | Docker | real-database behaviour without cloud Kafka |
| `cloud` | Redis Cloud | Supabase | managed | demo / production-like runs |

- One `docker-compose.yml` with profiles (`redis`, `postgres`, `kafka`, `monitoring`); start only what runs locally.
- Committed templates `.env.local.example`, `.env.hybrid.example`, `.env.cloud.example`; real `.env.*` files are gitignored.
- `./run.sh local|hybrid|cloud` starts containers, loads the env file, runs the app and a smoke test.
- `/actuator/info` shows which backend each service uses (host only, never credentials).
- Switching preset switches data; run the Redis rebuild job afterwards.
- Free tiers pause or delete data (both Supabase and Redis Cloud did); never load-test them.

## File changes
New: `docker-compose.yml`, `.env.*.example`, `run.sh`, `config/KafkaConfig.java` (topics, DLQ, SASL/SSL from env), `config/BackendInfoContributor.java`, `messaging/VoteEvent.java`, `messaging/VoteProducer.java`, `consumer/VoteCounterConsumer.java`, `consumer/VotePersistenceConsumer.java`, `redis/lua/count_vote.lua`, `cache/PollCache.java`, `service/ResultRebuildService.java`, `health/KafkaHealthIndicator.java`, `security/*` (JWT).

Modified: `pom.xml` (spring-kafka, Caffeine, Resilience4j, Bucket4j, Micrometer Prometheus, Testcontainers), `application.yaml`, `VoteService`, `PollController` (202, user from JWT), `VoteRequest` (drop userId), `RedisVoteService` (pre-check + hash counts), `RateLimiterService` (one Lua call + local fallback), `PollService` (results from `poll:{id}:counts`), `Vote` (add unique `eventId`), `PollExpirationWorker` (ShedLock), `GlobalExceptionHandler`, `postman/*`.

Removed: `VoteFlushWorker`, `VoteQueueHealthIndicator`, the `vote:queue` list, per-option string counters.

Docs: `AGENTS.md` ("Redis first" becomes "Kafka first; Redis is a derived view"), `.ai/project-brief.md`, `.ai/decision-log.md`, `RUNBOOK.md`, `README.md`.

## Phases (each ends with a test that must pass)
| # | Phase | Done when |
|---|---|---|
| 0 | Environments + baseline: compose profiles, env templates, `run.sh`, backend info, Redis AOF, Prometheus/Grafana, SLOs, re-run resilience groups B-E on the current design | current app runs on all 3 presets, smoke test passes on each, `/actuator/info` correct, "before" numbers in FINDINGS.md |
| 1 | Producer: VoteEvent, VoteProducer, PollCache, new VoteService, 202, `VOTE_PIPELINE=redis\|kafka` switch | local: 10k votes/s reach Kafka with 0 failures, one poll spread over all 12 partitions; cloud: smoke test over SASL_SSL |
| 2 | Counter consumer: Lua dedupe + count, results from hash, rebuild job | 1M votes with 20% duplicates give exact counts; consumer killed mid-run still exact; rebuild after preset switch matches PostgreSQL |
| 3 | Persistence consumer: JDBC batch, ON CONFLICT, DLQ, expiry check | local drain rate >= produce rate (vs 200/s); consistency_check.py: Redis = PostgreSQL = unique users; hybrid drains without exhausting Supabase connections |
| 4 | Failure hardening: circuit breaker, local rate-limit fallback, ShedLock, lag alerts, rewrite resilience groups C/D/E | every row of the failure table passes on local |
| 5 | Security: JWT, userId from token, rotate Supabase password, enable RLS | forged user IDs impossible; repo secret scan clean |
| 6 | Scale-out: 3 instances behind nginx, 12 consumers, hot-poll test | throughput scales to a documented limit |
| 7 | Cut over + CI: remove old path, docs, graphify refresh, GitHub Actions + Testcontainers | CI green; Postman run passes on local and cloud |

SLOs (local preset): p99 < 50 ms at 10k votes/s, 0 accepted votes lost, results lag < 1 s, database lag < 10 s.

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
| Cloud backend unreachable | same as the local outage, with a clear error naming the backend |

## Accepted costs
- More infrastructure to run and monitor.
- Results lag ~100 ms-1 s; the client gets `202`, not a final answer; a raced duplicate gets `202` but is not counted.
- Higher per-vote latency (`acks=all`), much higher in cloud mode due to network distance.
- A single local Kafka broker cannot show replication; use replication factor 3 / `min.insync.replicas=2` in real deployments.

## Postponed until measurements demand them
Table partitioning, sharding, Kubernetes, distributed tracing, API gateway.
