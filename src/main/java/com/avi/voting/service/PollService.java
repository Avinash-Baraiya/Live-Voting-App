package com.avi.voting.service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.avi.voting.dto.CreatePollRequest;
import com.avi.voting.dto.CreatePollResponse;
import com.avi.voting.dto.PollOptionResponse;
import com.avi.voting.entity.Poll;
import com.avi.voting.entity.PollOption;
import com.avi.voting.entity.PollStatus;
import com.avi.voting.repository.PollOptionRepository;
import com.avi.voting.repository.PollRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PollService {

        private final PollRepository pollRepository;
        private final PollOptionRepository pollOptionRepository;
        private final com.avi.voting.redis.RedisVoteService redisVoteService;

    public CreatePollResponse createPoll(CreatePollRequest request) {

                log.info("Creating poll: question='{}', optionsCount={}", request.getQuestion(), request.getOptions() == null ? 0 : request.getOptions().size());

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

        List<PollOption> savedOptions = pollOptionRepository.saveAll(options);

        // Cache option IDs for vote validation; VoteService reloads them from the DB if this fails
        try {
                redisVoteService.cachePollOptions(savedPoll.getId(),
                                savedOptions.stream().map(PollOption::getId).toList());
        } catch (Exception ex) {
                log.warn("Could not cache option IDs for pollId={}", savedPoll.getId(), ex);
        }

        log.info("Poll created: id={}, optionsSaved={}", savedPoll.getId(), options.size());

        return CreatePollResponse.builder()
                .pollId(savedPoll.getId())
                .message("Poll created successfully")
                .options(savedOptions.stream()
                        .map(o -> PollOptionResponse.builder()
                                .optionId(o.getId())
                                .optionText(o.getOptionText())
                                .build())
                        .toList())
                .build();
    }

        public com.avi.voting.dto.PollResultResponse getPollResults(Long pollId) {
                java.util.List<PollOption> options = pollOptionRepository.findByPollId(pollId);

                java.util.Map<String, Long> results = new java.util.HashMap<>();

                for (PollOption option : options) {
                        Long count = redisVoteService.getVoteCount(pollId, option.getId());
                        results.put(option.getOptionText(), count);
                }

                log.info("Fetched results for pollId={}, options={}", pollId, results.size());

                return com.avi.voting.dto.PollResultResponse.builder()
                                .pollId(pollId)
                                .results(results)
                                .build();
        }
}
