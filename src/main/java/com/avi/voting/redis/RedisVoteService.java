package com.avi.voting.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RedisVoteService {

    private final StringRedisTemplate redisTemplate;

    private String getVoteKey(Long pollId, Long optionId) {
        return "poll:" + pollId + ":option:" + optionId;
    }

    private String getVoterKey(Long pollId) {
        return "poll:" + pollId + ":voters";
    }

    public boolean addVoter(Long pollId, Long userId) {
        String key = getVoterKey(pollId);
        Long added = redisTemplate.opsForSet().add(key, String.valueOf(userId));
        return added != null && added == 1L;
    }

    public void incrementVote(Long pollId, Long optionId) {
        String key = getVoteKey(pollId, optionId);
        redisTemplate.opsForValue().increment(key);
    }

    public Long getVoteCount(Long pollId, Long optionId) {
        String key = getVoteKey(pollId, optionId);
        String value = redisTemplate.opsForValue().get(key);
        try {
            return value != null ? Long.valueOf(value) : 0L;
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
    
    public void pushVoteEvent(Long pollId, Long userId, Long optionId) {
        String key = "vote:queue";
        String value = pollId + ":" + userId + ":" + optionId;
        redisTemplate.opsForList().rightPush(key, value);
    }
}
