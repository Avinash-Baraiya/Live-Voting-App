package com.livevoting.service;

import com.livevoting.dto.PollResultsResponse;
import com.livevoting.dto.VoteRequest;
import com.livevoting.dto.VoteResponse;
import com.livevoting.entity.Poll;
import com.livevoting.entity.PollOption;
import com.livevoting.exception.DuplicateVoteException;
import com.livevoting.exception.InvalidOptionException;
import com.livevoting.exception.PollNotActiveException;
import com.livevoting.redis.RedisVoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoteService {

    private final PollService pollService;
    private final RedisVoteService redisVoteService;

    public VoteResponse castVote(Long pollId, VoteRequest request) {
        Poll poll = pollService.getPollEntity(pollId);

        if (!poll.isActive()) {
            throw new PollNotActiveException(pollId);
        }

        // Validate that the option belongs to this poll
        boolean validOption = poll.getOptions().stream()
                .anyMatch(opt -> opt.getId().equals(request.getOptionId()));
        if (!validOption) {
            throw new InvalidOptionException(request.getOptionId(), pollId);
        }

        // Atomically check and record vote in Redis
        boolean recorded = redisVoteService.recordVote(pollId, request.getUserId(), request.getOptionId());
        if (!recorded) {
            throw new DuplicateVoteException(request.getUserId(), pollId);
        }

        log.info("Vote recorded: userId={}, pollId={}, optionId={}", request.getUserId(), pollId, request.getOptionId());

        return VoteResponse.builder()
                .message("Vote cast successfully")
                .pollId(pollId)
                .optionId(request.getOptionId())
                .userId(request.getUserId())
                .build();
    }

    public PollResultsResponse getResults(Long pollId) {
        Poll poll = pollService.getPollEntity(pollId);

        Set<Long> optionIds = poll.getOptions().stream()
                .map(PollOption::getId)
                .collect(Collectors.toSet());

        Map<Long, Long> voteCounts = redisVoteService.getVoteCounts(pollId, optionIds);

        // Build results map keyed by option text
        Map<String, Long> results = new LinkedHashMap<>();
        long total = 0L;
        for (PollOption option : poll.getOptions()) {
            long count = voteCounts.getOrDefault(option.getId(), 0L);
            results.put(option.getOptionText(), count);
            total += count;
        }

        return PollResultsResponse.builder()
                .pollId(pollId)
                .question(poll.getQuestion())
                .results(results)
                .totalVotes(total)
                .build();
    }
}
