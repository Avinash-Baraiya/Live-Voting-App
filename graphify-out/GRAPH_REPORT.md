# Graph Report - Live-Voting-App  (2026-09-21)

## Corpus Check
- Corpus is ~4,997 words - fits in a single context window. You may not need a graph.

## Summary
- 183 nodes · 376 edges · 15 communities (9 shown, 6 thin omitted)
- Extraction: 90% EXTRACTED · 10% INFERRED · 0% AMBIGUOUS · INFERRED: 36 edges (avg confidence: 0.84)
- Token cost: 58,988 input · 0 output

## Community Hubs (Navigation)
- Vote API & Rate Limiting
- Exception Handling
- Project Docs & Decisions
- Poll DTOs & Controller
- Async Vote Persistence
- Poll Repositories & Status
- Duplicate Vote Safeguards
- Application Tests
- App Bootstrap
- Maven Wrapper
- k6 Load Test
- Agent Handoff Standard
- Small-Fix Editing Rule
- Maven Package
- Layered Architecture

## God Nodes (most connected - your core abstractions)
1. `RedisVoteService` - 19 edges
2. `VoteFlushWorker` - 15 edges
3. `Poll` - 14 edges
4. `PollController` - 13 edges
5. `VoteService` - 13 edges
6. `PollService` - 12 edges
7. `PollOption` - 11 edges
8. `PollStatus` - 11 edges
9. `PollRepository` - 11 edges
10. `AI Context Guide` - 11 edges

## Surprising Connections (you probably didn't know these)
- `Redis-First Vote Acceptance` --rationale_for--> `RedisVoteService`  [INFERRED]
  .ai/project-brief.md → src/main/java/com/avi/voting/redis/RedisVoteService.java
- `Core Poll APIs (create, vote, results)` --references--> `PollController`  [INFERRED]
  .ai/project-brief.md → src/main/java/com/avi/voting/controller/PollController.java
- `Vote Rate Limiting` --rationale_for--> `RateLimiterService`  [INFERRED]
  .ai/project-brief.md → src/main/java/com/avi/voting/redis/RateLimiterService.java
- `vote:queue Redis List` --shares_data_with--> `RedisVoteService`  [INFERRED]
  README.md → src/main/java/com/avi/voting/redis/RedisVoteService.java
- `Poll Expiration Handling` --references--> `PollExpirationWorker`  [INFERRED]
  README.md → src/main/java/com/avi/voting/worker/PollExpirationWorker.java

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Redis-First Vote Processing Pipeline** — src_main_java_com_avi_voting_controller_pollcontroller_pollcontroller, src_main_java_com_avi_voting_service_voteservice_voteservice, src_main_java_com_avi_voting_redis_ratelimiterservice_ratelimiterservice, src_main_java_com_avi_voting_redis_redisvoteservice_redisvoteservice, readme_vote_queue, src_main_java_com_avi_voting_worker_voteflushworker_voteflushworker [EXTRACTED 1.00]
- **Core Voting Invariants** — _ai_project_brief_redis_first_voting, _ai_project_brief_duplicate_vote_prevention, _ai_project_brief_rate_limiting, _ai_project_brief_async_persistence, _ai_project_brief_poll_expiry_rejection [EXTRACTED 1.00]
- **AI Context Handoff Packet** — agents_agent_guide, ai_context_ai_context_guide, _ai_project_brief_project_brief, _ai_decision_log_decision_log, _ai_known_issues_known_issues [EXTRACTED 1.00]

## Communities (15 total, 6 thin omitted)

### Community 0 - "Vote API & Rate Limiting"
Cohesion: 0.13
Nodes (19): Feature Change Mirroring Rule, Core Poll APIs (create, vote, results), Feature Register, Vote Rate Limiting, Main Vote Runtime Flow, lombok.RequiredArgsConstructor, org.springframework.data.redis.core.StringRedisTemplate, org.springframework.stereotype.Service (+11 more)

### Community 1 - "Exception Handling"
Cohesion: 0.10
Nodes (10): org.springframework.http.ResponseEntity, org.springframework.web.bind.annotation.ExceptionHandler, org.springframework.web.bind.annotation.RestControllerAdvice, DuplicateVoteException, GlobalExceptionHandler, PollExpiredException, PollNotActiveException, PollNotFoundException (+2 more)

### Community 2 - "Project Docs & Decisions"
Cohesion: 0.11
Nodes (27): Decision Log, Environment-Based Secrets Decision, Hardcoded Sensitive Config Defaults Risk, Known Issues, Stronger Vote Event Durability, Project Brief, Redis-First Vote Acceptance, AI Context Management Folder (.ai) (+19 more)

### Community 3 - "Poll DTOs & Controller"
Cohesion: 0.20
Nodes (14): jakarta.persistence.Entity, jakarta.persistence.Table, lombok.AllArgsConstructor, lombok.Builder, lombok.Data, lombok.NoArgsConstructor, org.springframework.web.bind.annotation.GetMapping, CreatePollRequest (+6 more)

### Community 4 - "Async Vote Persistence"
Cohesion: 0.15
Nodes (16): Redis Vote Path and Queue Worker Test Matrix, Async Vote Persistence, Inactive/Expired Poll Rejection, lombok.extern.slf4j.Slf4j, org.springframework.scheduling.annotation.Scheduled, org.springframework.stereotype.Component, Poll Expiration Handling, vote:queue Redis List (+8 more)

### Community 5 - "Poll Repositories & Status"
Cohesion: 0.21
Nodes (10): org.springframework.data.jpa.repository.JpaRepository, org.springframework.stereotype.Repository, PollStatus, ACTIVE, CLOSED, DRAFT, EXPIRED, PollOptionRepository (+2 more)

### Community 6 - "Duplicate Vote Safeguards"
Cohesion: 0.40
Nodes (6): Duplicate Vote Prevention, Atomic Redis SADD/INCR Operations, Vote Dedupe SQL Script, Failure: Duplicate Votes in DB, Atomic Lua Script for addVoter+increment, Failure: Redis Partial Writes

### Community 7 - "Application Tests"
Cohesion: 0.60
Nodes (3): org.junit.jupiter.api.Test, org.springframework.boot.test.context.SpringBootTest, VotingApplicationTests

### Community 8 - "App Bootstrap"
Cohesion: 0.60
Nodes (3): org.springframework.boot.autoconfigure.SpringBootApplication, org.springframework.scheduling.annotation.EnableScheduling, VotingApplication

## Knowledge Gaps
- **21 isolated node(s):** `com.avi:voting`, `errorRate`, `options`, `DRAFT`, `ACTIVE` (+16 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 43 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **6 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `RedisVoteService` connect `Vote API & Rate Limiting` to `Project Docs & Decisions`, `Async Vote Persistence`, `Duplicate Vote Safeguards`?**
  _High betweenness centrality (0.109) - this node is a cross-community bridge._
- **Why does `VoteFlushWorker` connect `Async Vote Persistence` to `Vote API & Rate Limiting`, `Project Docs & Decisions`, `Poll Repositories & Status`?**
  _High betweenness centrality (0.095) - this node is a cross-community bridge._
- **Why does `PollController` connect `Vote API & Rate Limiting` to `Project Docs & Decisions`, `Poll DTOs & Controller`?**
  _High betweenness centrality (0.066) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `RedisVoteService` (e.g. with `Redis-First Vote Acceptance` and `vote:queue Redis List`) actually correct?**
  _`RedisVoteService` has 2 INFERRED edges - model-reasoned connections that need verification._
- **Are the 3 inferred relationships involving `VoteFlushWorker` (e.g. with `Redis Vote Path and Queue Worker Test Matrix` and `vote:queue Redis List`) actually correct?**
  _`VoteFlushWorker` has 3 INFERRED edges - model-reasoned connections that need verification._
- **What connects `com.avi:voting`, `errorRate`, `options` to the rest of the system?**
  _21 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Vote API & Rate Limiting` be split into smaller, more focused modules?**
  _Cohesion score 0.13368983957219252 - nodes in this community are weakly interconnected._