package com.avi.voting.worker;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.avi.voting.entity.Vote;
import com.avi.voting.repository.VoteRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class VoteFlushWorker {

    private final StringRedisTemplate redisTemplate;
    private final VoteRepository voteRepository;

    private static final String QUEUE_KEY = "vote:queue";
    private static final int BATCH_SIZE = 1000;

    @Scheduled(fixedDelay = 5000)
    public void flushVotes() {
        List<Vote> votes = new ArrayList<>();

        for (int i = 0; i < BATCH_SIZE; i++) {
            String data = redisTemplate.opsForList().leftPop(QUEUE_KEY);
            if (data == null) break;

            try {
                String[] parts = data.split(":");
                Long pollId = Long.valueOf(parts[0]);
                Long userId = Long.valueOf(parts[1]);
                Long optionId = Long.valueOf(parts[2]);

                Vote v = Vote.builder()
                        .pollId(pollId)
                        .userId(userId)
                        .optionId(optionId)
                        .createdAt(Instant.now())
                        .build();

                votes.add(v);
            } catch (NumberFormatException ex) {
                // malformed entry — skip
            }
        }

        if (!votes.isEmpty()) {
            try {
                voteRepository.saveAll(votes);
                log.info("Flushed {} votes to DB", votes.size());
            } catch (Exception ex) {
                log.error("Failed to persist {} votes", votes.size(), ex);
                // In case of DB failure, push back to Redis queue for retry
                for (Vote v : votes) {
                    try {
                        String value = v.getPollId() + ":" + v.getUserId() + ":" + v.getOptionId();
                        redisTemplate.opsForList().leftPush(QUEUE_KEY, value);
                    } catch (Exception e) {
                        log.error("Failed to requeue vote {}", v, e);
                    }
                }
            }
        }
    }
}
