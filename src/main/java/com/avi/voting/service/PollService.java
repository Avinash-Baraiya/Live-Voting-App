package com.avi.voting.service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.avi.voting.dto.CreatePollRequest;
import com.avi.voting.dto.CreatePollResponse;
import com.avi.voting.entity.Poll;
import com.avi.voting.entity.PollOption;
import com.avi.voting.entity.PollStatus;
import com.avi.voting.repository.PollOptionRepository;
import com.avi.voting.repository.PollRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PollService {

        private final PollRepository pollRepository;
        private final PollOptionRepository pollOptionRepository;
        private final com.avi.voting.redis.RedisVoteService redisVoteService;

    public CreatePollResponse createPoll(CreatePollRequest request) {

        Poll poll = Poll.builder()
                .question(request.getQuestion())
                .createdAt(Instant.now())
                .expiresAt(request.getExpiresAt())
                .status(PollStatus.ACTIVE)
                .build();

        Poll savedPoll = pollRepository.save(poll);

        List<PollOption> options = request.getOptions().stream()
                .map(opt -> PollOption.builder()
                        .pollId(savedPoll.getId())
                        .optionText(opt)
                        .build())
                .collect(Collectors.toList());

        pollOptionRepository.saveAll(options);

        return CreatePollResponse.builder()
                .pollId(savedPoll.getId())
                .message("Poll created successfully")
                .build();
    }

        public com.avi.voting.dto.PollResultResponse getPollResults(Long pollId) {
                java.util.List<PollOption> options = pollOptionRepository.findByPollId(pollId);

                java.util.Map<String, Long> results = new java.util.HashMap<>();

                for (PollOption option : options) {
                        Long count = redisVoteService.getVoteCount(pollId, option.getId());
                        results.put(option.getOptionText(), count);
                }

                return com.avi.voting.dto.PollResultResponse.builder()
                                .pollId(pollId)
                                .results(results)
                                .build();
        }
}
