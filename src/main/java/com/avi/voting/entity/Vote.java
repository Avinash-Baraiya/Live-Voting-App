package com.avi.voting.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "vote", uniqueConstraints = {
    @UniqueConstraint(name = "uk_vote_user_poll", columnNames = {"user_id", "poll_id"})
}, indexes = {
    @Index(name = "idx_vote_poll_id", columnList = "poll_id"),
    @Index(name = "idx_vote_user_id", columnList = "user_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "poll_id", nullable = false)
    private Long pollId;

    @Column(name = "option_id", nullable = false)
    private Long optionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Instant createdAt;
}
