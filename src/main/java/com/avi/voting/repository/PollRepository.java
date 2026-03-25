package com.avi.voting.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.avi.voting.entity.Poll;
import com.avi.voting.entity.PollStatus;

@Repository
public interface PollRepository extends JpaRepository<Poll, Long> {
	List<Poll> findByStatusAndExpiresAtBefore(PollStatus status, Instant now);
}
