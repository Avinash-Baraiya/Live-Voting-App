# Live Voting App

A **production-grade Real-Time Voting System** built with **Java 17 and Spring Boot**, designed to handle 100k votes and up to 500k active users during peak voting events.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 17, Spring Boot 3.2 |
| Database | PostgreSQL (Supabase) |
| Cache / Counters | Redis (local) |
| Build | Maven |
| ORM | JPA / Hibernate |
| Testing | JUnit 5, Mockito, Spring Boot Test |
| Load Testing | k6 |
| Containers | Docker, Docker Compose |

## Project Structure

```
src/main/java/com/livevoting/
├── controller/       PollController
├── service/          PollService, VoteService
├── repository/       PollRepository, PollOptionRepository, VoteRepository
├── entity/           Poll, PollOption, Vote
├── redis/            RedisVoteService
├── worker/           VoteFlushWorker (async DB flush)
├── config/           RedisConfig, ThreadPoolConfig
├── dto/              Request/Response DTOs
└── exception/        Custom exceptions + GlobalExceptionHandler
```

## REST APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/poll` | Create a new poll |
| GET | `/poll/{pollId}` | Get poll details |
| POST | `/poll/{pollId}/vote` | Cast a vote |
| GET | `/poll/{pollId}/results` | Get real-time vote results |

### Create Poll

```http
POST /poll
Content-Type: application/json

{
  "question": "Who will win the IPL final?",
  "options": ["CSK", "MI", "RCB"],
  "expiresAt": "2026-06-01T18:00:00"
}
```

### Vote

```http
POST /poll/{pollId}/vote
Content-Type: application/json

{
  "userId": 12345,
  "optionId": 2
}
```

### Get Results

```http
GET /poll/{pollId}/results

Response:
{
  "pollId": 1,
  "question": "Who will win the IPL final?",
  "results": { "CSK": 1200, "MI": 980, "RCB": 1500 },
  "totalVotes": 3680
}
```

## Data Model

**Poll**: id, question, createdAt, expiresAt, status (ACTIVE/CLOSED/EXPIRED)

**PollOption**: id, pollId, optionText

**Vote**: id, pollId, optionId, userId, createdAt — with unique constraint `(userId, pollId)`

## Redis Usage

| Key Pattern | Purpose |
|------------|---------|
| `poll:{pollId}:option:{optionId}` | Atomic vote counter (INCR) |
| `poll:{pollId}:voters` | Set of userIds who voted (SADD) |
| `vote:queue` | Queue for async DB persistence (RPUSH / LPOP) |

## Vote Flow

1. User sends `POST /poll/{pollId}/vote`
2. Check if poll is ACTIVE and not expired
3. Validate optionId belongs to this poll
4. Atomically add userId to Redis Set (`SADD`) — returns 0 if duplicate
5. If new vote: increment Redis counter (`INCR`) and queue for persistence
6. Return success (or 409 Conflict for duplicate)
7. `VoteFlushWorker` runs every 5s, batch-persists queued votes to PostgreSQL

## Setup & Running

### Prerequisites

- Java 17
- Maven 3.9+
- Redis running locally on port 6379
- Supabase account (or any PostgreSQL instance)

### 1. Configure Supabase PostgreSQL

Edit `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://YOUR_SUPABASE_HOST:5432/postgres?sslmode=require
spring.datasource.username=postgres
spring.datasource.password=YOUR_PASSWORD
```

Or use environment variables:

```bash
export DB_HOST=your-project.supabase.co
export DB_NAME=postgres
export DB_USER=postgres
export DB_PASSWORD=your-password
```

### 2. Start Redis Locally

```bash
redis-server
```

### 3. Build and Run

```bash
mvn clean package -DskipTests
java -jar target/live-voting-system-1.0.0.jar
```

### 4. Run with Docker Compose

Create a `.env` file:

```
DB_HOST=your-project.supabase.co
DB_NAME=postgres
DB_USER=postgres
DB_PASSWORD=your-password
```

Then:

```bash
docker-compose up --build
```

This starts the app on port 8080 and Redis on port 6379.

## Testing

### Unit & Integration Tests

```bash
mvn test
```

### Load Testing with k6

Install k6: https://k6.io/docs/getting-started/installation/

```bash
# Smoke test (10 VUs for 30s)
k6 run -e SCENARIO=smoke k6/load-test.js

# 1,000 concurrent users
k6 run -e SCENARIO=load_1k k6/load-test.js

# 5,000 concurrent users
k6 run -e SCENARIO=load_5k k6/load-test.js

# 10,000 concurrent users
k6 run -e SCENARIO=load_10k k6/load-test.js
```

Override the base URL if needed:

```bash
k6 run -e BASE_URL=http://your-server:8080 -e SCENARIO=load_5k k6/load-test.js
```

## Performance Design

- **Redis atomic SADD**: Prevents duplicate votes without distributed locks
- **Redis INCR**: Lock-free counter increment (single-threaded Redis guarantees atomicity)
- **Async DB flush**: `VoteFlushWorker` persists votes in batches of 500 every 5 seconds
- **HikariCP pool**: 20 connections for PostgreSQL
- **Thread pool**: 20 core / 100 max threads for async processing
- **Database unique constraint**: `(userId, pollId)` as a fallback safety net
- **Results from Redis**: Sub-millisecond read latency for vote counts
