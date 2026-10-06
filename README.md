# Real-Time Voting System (Backend)

A production-grade backend system designed to handle high-concurrency voting events using Redis and PostgreSQL.

## Key Features

- Real-time voting using Redis (O(1) operations)
- Duplicate vote prevention using Redis Set
- Instant results with Redis counters
- Asynchronous vote persistence (Redis → PostgreSQL)
- Rate limiting to prevent abuse
- Poll expiration handling
- Clean layered architecture (Controller → Service → Repository)

## Tech Stack

- Java 25
- Spring Boot
- PostgreSQL
- Redis
- Maven

## APIs

### Create Poll

POST /poll

Request body:

```json
{
  "question": "Who will win IPL final?",
  "options": ["CSK", "MI", "RCB"],
  "expiresAt": "2026-06-01T18:00:00Z"
}
```

### Vote

POST /poll/{pollId}/vote

Request body:

```json
{
  "userId": 101,
  "optionId": 2
}
```

### Get Results

GET /poll/{pollId}/results

Response example:

```json
{
  "pollId": 1,
  "results": { "CSK": 1500, "MI": 980 }
}
```

## Running the app

Development runs fully on this machine: Redis and PostgreSQL in Docker, the app on the JVM. All connection settings come from environment variables in `.env.local`, so the code never contains credentials.

1. Install and start Docker Desktop.
2. Create your local settings (gitignored; works as is):

```bash
cp .env.local.example .env.local
```

3. Run it. `run.sh` starts the containers, checks Redis and PostgreSQL are reachable, then starts the app:

```bash
./run.sh local --fast   # app ready in a few seconds (rebuilds the jar only when code changed)
./run.sh smoke          # in another terminal: health + which servers the app uses
./run.sh stop           # stop all containers (data is kept in Docker volumes)
```

| Service | Address | Login / notes |
|---|---|---|
| App | http://localhost:8080 | `/actuator/health`, `/actuator/info`, `/actuator/prometheus` |
| PostgreSQL | `localhost:5432` | database, user and password: `voting` (pgAdmin: register a server with these) |
| Redis | `localhost:6379` | database 0, no password (Redis Insight: `127.0.0.1:6379`) |
| Kafka + Kafka UI | `localhost:9092`, http://localhost:8090 | optional: `docker compose --profile kafka up -d` |
| Prometheus + Grafana | http://localhost:9090, http://localhost:3000 | optional: `docker compose --profile monitoring up -d`; dashboard "Live Voting" |

Ports are bound to `127.0.0.1`, so none of these are reachable from other machines. The app fails at startup if the datasource variables are missing.

## Checking PostgreSQL and Redis

With `HEALTH_SHOW_DETAILS=always` (set in `.env.local`), `GET /actuator/health` reports each dependency:

- `db`: PostgreSQL connection
- `redis`: Redis connection
- `voteQueue`: votes waiting in `vote:queue` to be saved to PostgreSQL (`pendingVotes`)

`/health` only confirms the app process is up.

## Testing with Postman

Import both files from `postman/` into Postman and select the **Live Voting - Local** environment (`baseUrl` = `http://localhost:8080`):

- `postman/Live-Voting-App.postman_collection.json`
- `postman/local.postman_environment.json`

Run the "2. Poll flow" folder in order with the Collection Runner. It creates a poll, votes, and checks the duplicate, invalid-option, missing-field and unknown-poll errors, then checks the results.

## Design Notes

- Votes are handled in Redis for low-latency counting. Redis operations are atomic (`SADD`, `INCR`).
- Vote events are queued in Redis (`vote:queue`) and persisted to PostgreSQL by a scheduled worker in batches.
- Rate limiting uses Redis `INCR` + `EXPIRE` per user.
- Polls are auto-closed when expired by a scheduled worker and also validated at vote time.

## Future Improvements

- Use Kafka for durable, replayable event streaming.
- Add WebSocket or SSE for live client updates.
- Add observability (metrics, tracing, alerts).

## AI Context Management

Use [AI_CONTEXT.md](AI_CONTEXT.md), the .ai folder, and AGENTS.md to keep agent handoffs stable across coding sessions.

- Start with [AI_CONTEXT.md](AI_CONTEXT.md) for the visible project summary and feature workflow.
- Start with .ai/project-brief.md for the canonical system summary.
- Record architecture or behavior decisions in .ai/decision-log.md.
- Keep .ai/known-issues.md for recurring risks and follow-up items.

## Author

Avinash# Live-Voting-App