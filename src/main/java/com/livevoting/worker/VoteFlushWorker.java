package com.livevoting.worker;

import com.livevoting.entity.Vote;
import com.livevoting.redis.RedisVoteService;
import com.livevoting.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class VoteFlushWorker {

    private static final int BATCH_SIZE = 500;

    private final RedisVoteService redisVoteService;
    private final VoteRepository voteRepository;

    /**
     * Flushes pending votes from Redis queue to PostgreSQL every 5 seconds.
     */
    @Scheduled(fixedDelay = 5000)
    public void flushVotesToDatabase() {
        long queueSize = redisVoteService.getQueueSize();
        if (queueSize == 0) {
            return;
        }

        log.debug("Flushing {} votes from Redis queue to database", queueSize);

        List<Vote> batch = new ArrayList<>();
        int processed = 0;

        while (processed < BATCH_SIZE) {
            String voteData = redisVoteService.popVoteFromQueue();
            if (voteData == null) {
                break;
            }
            Vote vote = parseVote(voteData);
            if (vote != null) {
                batch.add(vote);
            }
            processed++;
        }

        if (!batch.isEmpty()) {
            try {
                voteRepository.saveAll(batch);
                log.info("Persisted {} votes to database", batch.size());
            } catch (Exception e) {
                log.error("Failed to persist {} votes to database: {}", batch.size(), e.getMessage(), e);
                // Re-queue failed votes back to Redis for retry
                for (Vote vote : batch) {
                    String data = vote.getPollId() + ":" + vote.getOptionId() + ":" + vote.getUserId() + ":"
                            + System.currentTimeMillis();
                    redisVoteService.requeueVote(data);
                }
            }
        }
    }

    private Vote parseVote(String voteData) {
        try {
            String[] parts = voteData.split(":");
            if (parts.length < 4) {
                log.warn("Invalid vote data format: {}", voteData);
                return null;
            }
            Long pollId = Long.parseLong(parts[0]);
            Long optionId = Long.parseLong(parts[1]);
            Long userId = Long.parseLong(parts[2]);
            long epochMillis = Long.parseLong(parts[3]);

            return Vote.builder()
                    .pollId(pollId)
                    .optionId(optionId)
                    .userId(userId)
                    .createdAt(LocalDateTime.ofEpochSecond(epochMillis / 1000, 0, java.time.ZoneOffset.UTC))
                    .build();
        } catch (NumberFormatException e) {
            log.warn("Failed to parse vote data '{}': {}", voteData, e.getMessage());
            return null;
        }
    }
}
