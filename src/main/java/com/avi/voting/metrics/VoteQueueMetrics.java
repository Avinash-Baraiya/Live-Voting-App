package com.avi.voting.metrics;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Gauge "vote.queue.size": votes waiting in the Redis vote:queue list for VoteFlushWorker.
 * Read on every Prometheus scrape (LLEN is O(1)). In Grafana a rising line means the
 * worker saves votes slower than the API accepts them.
 */
@Component
public class VoteQueueMetrics {

    private static final String QUEUE_KEY = "vote:queue";

    public VoteQueueMetrics(MeterRegistry registry, StringRedisTemplate redisTemplate) {
        Gauge.builder("vote.queue.size", () -> {
                    try {
                        Long size = redisTemplate.opsForList().size(QUEUE_KEY);
                        return size != null ? size : 0;
                    } catch (Exception ex) {
                        return Double.NaN; // Redis unreachable: show a gap, not a fake zero
                    }
                })
                .description("Votes waiting in Redis to be saved to PostgreSQL")
                .register(registry);
    }
}
