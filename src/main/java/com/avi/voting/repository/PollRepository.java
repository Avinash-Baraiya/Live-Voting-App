package com.avi.voting.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.avi.voting.entity.Poll;

@Repository
public interface PollRepository extends JpaRepository<Poll, Long> {
}
