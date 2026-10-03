# Graph Report - Live-Voting-App  (2026-10-03)

## Corpus Check
- Corpus is ~11,242 words - fits in a single context window. You may not need a graph.

## Summary
- 271 nodes · 562 edges · 18 communities (10 shown, 8 thin omitted)
- Extraction: 89% EXTRACTED · 11% INFERRED · 0% AMBIGUOUS · INFERRED: 61 edges (avg confidence: 0.84)
- Token cost: 93,156 input · 0 output

## Community Hubs (Navigation)
- Project Decisions & Known Issues
- Exception Handling
- Redis Checks & Repositories
- Load Generator
- Redis Vote Service & Tests
- Controllers & API Endpoints
- DTOs & Entities
- Workers & Health Indicator
- Test Harness Library
- App Bootstrap
- Consistency Checker
- Maven Wrapper
- Consistency Test Group B
- Redis Failure Tests (C)
- PostgreSQL Failure Tests (D)
- App Crash Tests (E)
- Phase 1 Load Test
- Maven Package

## God Nodes (most connected - your core abstractions)
1. `RedisVoteService` - 25 edges
2. `Poll` - 15 edges
3. `VoteService` - 15 edges
4. `VoteFlushWorker` - 14 edges
5. `Real-Time Voting System (Backend)` - 14 edges
6. `PollOption` - 13 edges
7. `PollRepository` - 13 edges
8. `VoteServiceTest` - 13 edges
9. `PollController` - 12 edges
10. `PollStatus` - 12 edges

## Surprising Connections (you probably didn't know these)
- `Poll Expiration Handling` --implements--> `PollExpirationWorker`  [INFERRED]
  README.md → src/main/java/com/avi/voting/worker/PollExpirationWorker.java
- `POST /poll/{pollId}/vote (Vote)` --implements--> `PollController`  [INFERRED]
  README.md → src/main/java/com/avi/voting/controller/PollController.java
- `Decision: Actuator Health Checks with Hidden Details` --rationale_for--> `VoteQueueHealthIndicator`  [INFERRED]
  .ai/decision-log.md → src/main/java/com/avi/voting/health/VoteQueueHealthIndicator.java
- `Duplicate Vote Prevention via Redis Set` --implements--> `RedisVoteService`  [INFERRED]
  README.md → src/main/java/com/avi/voting/redis/RedisVoteService.java
- `Prevention: Atomic Lua Script for addVoter + increment` --rationale_for--> `RedisVoteService`  [EXTRACTED]
  RUNBOOK.md → src/main/java/com/avi/voting/redis/RedisVoteService.java

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Redis-First Vote Path (Controller to PostgreSQL)** — _ai_project_brief_main_runtime_flow, src_main_java_com_avi_voting_controller_pollcontroller_pollcontroller, src_main_java_com_avi_voting_service_voteservice_voteservice, src_main_java_com_avi_voting_redis_redisvoteservice_redisvoteservice, src_main_java_com_avi_voting_redis_ratelimiterservice_ratelimiterservice, src_main_java_com_avi_voting_worker_voteflushworker_voteflushworker, _ai_project_brief_redis_first_invariant [EXTRACTED 1.00]
- **Agent Context Packet** — agents_agent_guide, ai_context_handoff_guide, _ai_readme_context_layer, _ai_project_brief, _ai_decision_log, _ai_known_issues, readme_live_voting_backend, runbook_live_voting [EXTRACTED 1.00]
- **Persistence Ceiling: Measurement to Proposed Fixes** — resilience_tests_findings_w7_persistence_ceiling, resilience_tests_findings_api_plateau, _ai_project_brief_measured_performance, _ai_known_issues_persistence_ceiling, _ai_known_issues_queue_data_loss, _ai_known_issues_bulk_lpop_jdbc_batch, _ai_known_issues_redis_streams_queue, readme_kafka_event_streaming [INFERRED 0.85]

## Communities (18 total, 8 thin omitted)

### Community 0 - "Project Decisions & Known Issues"
Cohesion: 0.06
Nodes (57): Decision Log, Decision: Actuator Health Checks with Hidden Details, Decision: Commit Directly to main (No Pull Requests), Decision: Standardize Context on .ai Folder plus AGENTS.md, Decision: Remove Credential Fallbacks from application.yaml, Decision: Remove k6 Load Testing, Decision: Commit Resilience Scripts, Ignore Per-Run Output, Decision: Move Redis Config to spring.data.redis.* (+49 more)

### Community 1 - "Exception Handling"
Cohesion: 0.09
Nodes (11): org.springframework.http.ResponseEntity, org.springframework.web.bind.annotation.ExceptionHandler, org.springframework.web.bind.annotation.RestControllerAdvice, DuplicateVoteException, GlobalExceptionHandler, InvalidVoteException, PollExpiredException, PollNotActiveException (+3 more)

### Community 2 - "Redis Checks & Repositories"
Cohesion: 0.13
Nodes (20): Decision: Validate Vote Options from a Redis Option Set, org.junit.jupiter.api.BeforeEach, org.junit.jupiter.api.extension.ExtendWith, org.mockito.junit.jupiter.MockitoExtension, org.springframework.data.jpa.repository.JpaRepository, org.springframework.stereotype.Repository, org.springframework.stereotype.Service, Per-User Rate Limiting with Redis INCR + EXPIRE (+12 more)

### Community 3 - "Load Generator"
Cohesion: 0.08
Nodes (22): autocannon, args, autocannon, connections, fs, inst, options, opts (+14 more)

### Community 4 - "Redis Vote Service & Tests"
Cohesion: 0.18
Nodes (4): org.junit.jupiter.api.Test, org.springframework.boot.test.context.SpringBootTest, RedisVoteService, VotingApplicationTests

### Community 5 - "Controllers & API Endpoints"
Cohesion: 0.17
Nodes (10): org.springframework.web.bind.annotation.GetMapping, org.springframework.web.bind.annotation.PostMapping, org.springframework.web.bind.annotation.RequestMapping, org.springframework.web.bind.annotation.RestController, POST /poll/{pollId}/vote (Vote), HealthCheckController, PollController, CreatePollRequest (+2 more)

### Community 6 - "DTOs & Entities"
Cohesion: 0.36
Nodes (12): jakarta.persistence.Entity, jakarta.persistence.Table, lombok.AllArgsConstructor, lombok.Builder, lombok.Data, lombok.NoArgsConstructor, CreatePollResponse, PollOptionResponse (+4 more)

### Community 7 - "Workers & Health Indicator"
Cohesion: 0.24
Nodes (11): lombok.extern.slf4j.Slf4j, lombok.RequiredArgsConstructor, org.springframework.boot.health.contributor.AbstractHealthIndicator, org.springframework.data.redis.core.StringRedisTemplate, org.springframework.scheduling.annotation.Scheduled, org.springframework.stereotype.Component, Override, Builder (+3 more)

### Community 9 - "App Bootstrap"
Cohesion: 0.60
Nodes (3): org.springframework.boot.autoconfigure.SpringBootApplication, org.springframework.scheduling.annotation.EnableScheduling, VotingApplication

### Community 10 - "Consistency Checker"
Cohesion: 0.60
Nodes (4): main(), pg(), Compare what Redis says about a poll with what PostgreSQL actually stored.…, redis()

## Knowledge Gaps
- **32 isolated node(s):** `com.avi:voting`, `e_app_crash.sh script`, `lib.sh script`, `autocannon`, `fs` (+27 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 74 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **8 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `InvalidVoteException` connect `Exception Handling` to `Redis Checks & Repositories`, `Redis Vote Service & Tests`?**
  _High betweenness centrality (0.155) - this node is a cross-community bridge._
- **Why does `RedisVoteService` connect `Redis Vote Service & Tests` to `Project Decisions & Known Issues`, `Redis Checks & Repositories`, `Controllers & API Endpoints`, `Workers & Health Indicator`?**
  _High betweenness centrality (0.107) - this node is a cross-community bridge._
- **Why does `VoteFlushWorker` connect `Workers & Health Indicator` to `Project Decisions & Known Issues`, `Redis Checks & Repositories`?**
  _High betweenness centrality (0.072) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `RedisVoteService` (e.g. with `Duplicate Vote Prevention via Redis Set` and `spring.data.redis Configuration`) actually correct?**
  _`RedisVoteService` has 2 INFERRED edges - model-reasoned connections that need verification._
- **What connects `com.avi:voting`, `e_app_crash.sh script`, `lib.sh script` to the rest of the system?**
  _32 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Project Decisions & Known Issues` be split into smaller, more focused modules?**
  _Cohesion score 0.06328320802005012 - nodes in this community are weakly interconnected._
- **Should `Exception Handling` be split into smaller, more focused modules?**
  _Cohesion score 0.08912655971479501 - nodes in this community are weakly interconnected._