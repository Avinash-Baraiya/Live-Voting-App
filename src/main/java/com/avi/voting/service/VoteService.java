package com.avi.voting.service;

import org.springframework.stereotype.Service;

import com.avi.voting.entity.Poll;
import com.avi.voting.entity.PollStatus;
import com.avi.voting.redis.RedisVoteService;
import com.avi.voting.repository.PollRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoteService {

    private final RedisVoteService redisVoteService;
    private final PollRepository pollRepository;
    private final com.avi.voting.redis.RateLimiterService rateLimiterService;

    public String vote(Long pollId, Long userId, Long optionId) {
        log.info("Vote request received: pollId={}, userId={}, optionId={}", pollId, userId, optionId);

        if (!rateLimiterService.isAllowed(userId)) {
            log.warn("Rate limit exceeded for userId={}", userId);
            throw new com.avi.voting.exception.TooManyRequestsException("Too many requests. Please slow down.");
        }

        Poll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new com.avi.voting.exception.PollNotFoundException("Poll not found"));

        if (poll.getStatus() != PollStatus.ACTIVE) {
            throw new com.avi.voting.exception.PollNotActiveException("Poll is not active");
        }

        if (poll.getExpiresAt() != null && poll.getExpiresAt().isBefore(java.time.Instant.now())) {
            throw new com.avi.voting.exception.PollExpiredException("Poll has expired");
        }

        boolean isNew;
        try {
            isNew = redisVoteService.addVoter(pollId, userId);
        } catch (Exception ex) {
            log.error("Redis addVoter failed for pollId={}, userId={}", pollId, userId, ex);
            throw new com.avi.voting.exception.RedisUnavailableException("Redis operation failed", ex);
        }

        if (!isNew) {
            log.info("Duplicate vote attempt: pollId={}, userId={}", pollId, userId);
            throw new com.avi.voting.exception.DuplicateVoteException("User already voted");
        }

        try {
            redisVoteService.incrementVote(pollId, optionId);
        } catch (Exception ex) {
            log.error("Redis increment failed for pollId={}, optionId={}", pollId, optionId, ex);
            throw new com.avi.voting.exception.RedisUnavailableException("Redis increment failed", ex);
        }
        // enqueue event for async DB persistence
        try {
            redisVoteService.pushVoteEvent(pollId, userId, optionId);
        } catch (Exception ex) {
            log.error("Redis enqueue failed for pollId={}, userId={}, optionId={}", pollId, userId, optionId, ex);
            // log and continue; counts are already updated in Redis
            throw new com.avi.voting.exception.RedisUnavailableException("Redis enqueue failed", ex);
        }
        log.info("Vote recorded successfully: pollId={}, userId={}, optionId={}", pollId, userId, optionId);
        return "Vote recorded successfully";
    }
}
