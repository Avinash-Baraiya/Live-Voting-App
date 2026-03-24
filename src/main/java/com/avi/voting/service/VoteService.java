package com.avi.voting.service;

import org.springframework.stereotype.Service;

import com.avi.voting.entity.Poll;
import com.avi.voting.entity.PollStatus;
import com.avi.voting.redis.RedisVoteService;
import com.avi.voting.repository.PollRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VoteService {

    private final RedisVoteService redisVoteService;
    private final PollRepository pollRepository;

    public String vote(Long pollId, Long userId, Long optionId) {
        Poll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new com.avi.voting.exception.PollNotFoundException("Poll not found"));

        if (poll.getStatus() != PollStatus.ACTIVE) {
            throw new com.avi.voting.exception.PollNotActiveException("Poll is not active");
        }

        boolean isNew;
        try {
            isNew = redisVoteService.addVoter(pollId, userId);
        } catch (Exception ex) {
            throw new com.avi.voting.exception.RedisUnavailableException("Redis operation failed", ex);
        }

        if (!isNew) {
            throw new com.avi.voting.exception.DuplicateVoteException("User already voted");
        }

        try {
            redisVoteService.incrementVote(pollId, optionId);
        } catch (Exception ex) {
            throw new com.avi.voting.exception.RedisUnavailableException("Redis increment failed", ex);
        }
        // enqueue event for async DB persistence
        try {
            redisVoteService.pushVoteEvent(pollId, userId, optionId);
        } catch (Exception ex) {
            // log and continue; counts are already updated in Redis
            throw new com.avi.voting.exception.RedisUnavailableException("Redis enqueue failed", ex);
        }
        return "Vote recorded successfully";
    }
}
