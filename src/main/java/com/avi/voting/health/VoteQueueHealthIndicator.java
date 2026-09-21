package com.avi.voting.health;

import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Reports the vote:queue backlog under /actuator/health (component "voteQueue").
 * A large or growing backlog means VoteFlushWorker is not keeping up with PostgreSQL writes.
 */
@Component
@RequiredArgsConstructor
public class VoteQueueHealthIndicator extends AbstractHealthIndicator {

    private static final String QUEUE_KEY = "vote:queue";
    // Matches the RUNBOOK alert threshold
    private static final long BACKLOG_WARN_THRESHOLD = 10_000;

    private final StringRedisTemplate redisTemplate;

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        Long size = redisTemplate.opsForList().size(QUEUE_KEY);
        long pending = size != null ? size : 0L;
        builder.up()
                .withDetail("pendingVotes", pending)
                .withDetail("backlogWarning", pending > BACKLOG_WARN_THRESHOLD);
    }
}
