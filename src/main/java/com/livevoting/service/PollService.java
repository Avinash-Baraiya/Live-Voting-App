package com.livevoting.service;

import com.livevoting.dto.CreatePollRequest;
import com.livevoting.dto.PollResponse;
import com.livevoting.entity.Poll;
import com.livevoting.entity.PollOption;
import com.livevoting.exception.PollNotFoundException;
import com.livevoting.repository.PollRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PollService {

    private final PollRepository pollRepository;

    @Transactional
    public PollResponse createPoll(CreatePollRequest request) {
        Poll poll = Poll.builder()
                .question(request.getQuestion())
                .expiresAt(request.getExpiresAt())
                .status(Poll.PollStatus.ACTIVE)
                .build();

        List<PollOption> options = request.getOptions().stream()
                .map(optionText -> PollOption.builder()
                        .poll(poll)
                        .optionText(optionText)
                        .build())
                .toList();

        poll.setOptions(options);
        Poll saved = pollRepository.save(poll);
        log.info("Created poll id={} with {} options", saved.getId(), options.size());
        return PollResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PollResponse getPoll(Long pollId) {
        Poll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new PollNotFoundException(pollId));
        return PollResponse.from(poll);
    }

    @Transactional(readOnly = true)
    public Poll getPollEntity(Long pollId) {
        return pollRepository.findById(pollId)
                .orElseThrow(() -> new PollNotFoundException(pollId));
    }
}
