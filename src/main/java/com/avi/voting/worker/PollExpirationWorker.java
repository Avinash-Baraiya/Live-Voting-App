package com.avi.voting.worker;

import java.time.Instant;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.avi.voting.entity.Poll;
import com.avi.voting.entity.PollStatus;
import com.avi.voting.repository.PollRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PollExpirationWorker {

    private final PollRepository pollRepository;

    @Scheduled(fixedDelay = 10000)
    public void closeExpiredPolls() {
        List<Poll> expired = pollRepository.findByStatusAndExpiresAtBefore(PollStatus.ACTIVE, Instant.now());
        if (expired.isEmpty()) {
            return;
        }

        for (Poll p : expired) {
            p.setStatus(PollStatus.CLOSED);
        }

        try {
            pollRepository.saveAll(expired);
            log.info("Closed {} expired polls", expired.size());
        } catch (Exception ex) {
            log.error("Failed to close expired polls", ex);
        }
    }
}
