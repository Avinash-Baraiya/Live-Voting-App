package com.livevoting.repository;

import com.livevoting.entity.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VoteRepository extends JpaRepository<Vote, Long> {

    boolean existsByUserIdAndPollId(Long userId, Long pollId);
}
