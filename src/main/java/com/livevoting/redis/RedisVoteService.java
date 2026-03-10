package com.livevoting.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RedisVoteService {

    private static final String VOTE_COUNTER_KEY = "poll:%d:option:%d";
    private static final String VOTERS_SET_KEY = "poll:%d:voters";
    private static final String VOTE_QUEUE_KEY = "vote:queue";

    private final StringRedisTemplate redisTemplate;

    /**
     * Check if the user has already voted in the given poll.
     */
    public boolean hasUserVoted(Long pollId, Long userId) {
        String key = String.format(VOTERS_SET_KEY, pollId);
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, userId.toString()));
    }

    /**
     * Atomically record a vote: add user to voters set and increment option counter.
     * Returns true if vote was recorded successfully, false if user already voted.
     */
    public boolean recordVote(Long pollId, Long userId, Long optionId) {
        String votersKey = String.format(VOTERS_SET_KEY, pollId);
        // SADD returns the number of elements added; 0 means user was already present
        Long added = redisTemplate.opsForSet().add(votersKey, userId.toString());
        if (added == null || added == 0) {
            return false;
        }
        String counterKey = String.format(VOTE_COUNTER_KEY, pollId, optionId);
        redisTemplate.opsForValue().increment(counterKey);

        // Queue vote for async persistence
        String voteData = pollId + ":" + optionId + ":" + userId + ":" + System.currentTimeMillis();
        redisTemplate.opsForList().rightPush(VOTE_QUEUE_KEY, voteData);
        return true;
    }

    /**
     * Get vote count for a specific option.
     */
    public long getVoteCount(Long pollId, Long optionId) {
        String counterKey = String.format(VOTE_COUNTER_KEY, pollId, optionId);
        String value = redisTemplate.opsForValue().get(counterKey);
        return value != null ? Long.parseLong(value) : 0L;
    }

    /**
     * Get all vote counts for a poll's options.
     */
    public Map<Long, Long> getVoteCounts(Long pollId, Set<Long> optionIds) {
        Map<Long, Long> counts = new HashMap<>();
        for (Long optionId : optionIds) {
            counts.put(optionId, getVoteCount(pollId, optionId));
        }
        return counts;
    }

    /**
     * Pop up to batchSize votes from the queue for persistence.
     */
    public String popVoteFromQueue() {
        return redisTemplate.opsForList().leftPop(VOTE_QUEUE_KEY);
    }

    /**
     * Get the number of votes pending in the queue.
     */
    public long getQueueSize() {
        Long size = redisTemplate.opsForList().size(VOTE_QUEUE_KEY);
        return size != null ? size : 0L;
    }

    /**
     * Re-queue a vote string for retry.
     */
    public void requeueVote(String voteData) {
        redisTemplate.opsForList().rightPush(VOTE_QUEUE_KEY, voteData);
    }
}
