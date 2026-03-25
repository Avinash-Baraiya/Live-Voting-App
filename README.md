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

## Running Locally

1. Start Redis:

```bash
docker run -p 6379:6379 redis
```

2. Configure PostgreSQL connection in `src/main/resources/application.yaml`.

3. Run the app:

```bash
./mvnw spring-boot:run
```

## Design Notes

- Votes are handled in Redis for low-latency counting. Redis operations are atomic (`SADD`, `INCR`).
- Vote events are queued in Redis (`vote:queue`) and persisted to PostgreSQL by a scheduled worker in batches.
- Rate limiting uses Redis `INCR` + `EXPIRE` per user.
- Polls are auto-closed when expired by a scheduled worker and also validated at vote time.

## Future Improvements

- Use Kafka for durable, replayable event streaming.
- Add WebSocket or SSE for live client updates.
- Add observability (metrics, tracing, alerts).

## Author

Avinash# Live-Voting-App