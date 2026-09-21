package com.avi.voting.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.avi.voting.entity.Poll;
import com.avi.voting.entity.PollOption;
import com.avi.voting.entity.PollStatus;
import com.avi.voting.exception.InvalidVoteException;
import com.avi.voting.redis.RateLimiterService;
import com.avi.voting.redis.RedisVoteService;
import com.avi.voting.repository.PollOptionRepository;
import com.avi.voting.repository.PollRepository;

@ExtendWith(MockitoExtension.class)
class VoteServiceTest {

    @Mock
    private RedisVoteService redisVoteService;
    @Mock
    private PollRepository pollRepository;
    @Mock
    private PollOptionRepository pollOptionRepository;
    @Mock
    private RateLimiterService rateLimiterService;

    @InjectMocks
    private VoteService voteService;

    @BeforeEach
    void setUp() {
        Poll poll = Poll.builder()
                .id(1L)
                .status(PollStatus.ACTIVE)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        lenient().when(rateLimiterService.isAllowed(anyLong())).thenReturn(true);
        lenient().when(pollRepository.findById(1L)).thenReturn(Optional.of(poll));
    }

    @Test
    void acceptsVoteForCachedOption() {
        when(redisVoteService.isPollOption(1L, 10L)).thenReturn(true);
        when(redisVoteService.addVoter(1L, 7L)).thenReturn(true);

        assertEquals("Vote recorded successfully", voteService.vote(1L, 7L, 10L));
        verify(redisVoteService).incrementVote(1L, 10L);
        verify(pollOptionRepository, never()).findByPollId(anyLong());
    }

    @Test
    void rejectsOptionFromAnotherPoll() {
        when(redisVoteService.isPollOption(1L, 99L)).thenReturn(false);
        when(redisVoteService.hasPollOptions(1L)).thenReturn(true);

        assertThrows(InvalidVoteException.class, () -> voteService.vote(1L, 7L, 99L));
        verify(redisVoteService, never()).addVoter(anyLong(), anyLong());
        verify(redisVoteService, never()).incrementVote(anyLong(), anyLong());
    }

    @Test
    void loadsOptionsFromDbWhenCacheMissing() {
        when(redisVoteService.isPollOption(1L, 10L)).thenReturn(false);
        when(redisVoteService.hasPollOptions(1L)).thenReturn(false);
        when(pollOptionRepository.findByPollId(1L)).thenReturn(List.of(
                PollOption.builder().id(10L).pollId(1L).optionText("A").build(),
                PollOption.builder().id(11L).pollId(1L).optionText("B").build()));
        when(redisVoteService.addVoter(1L, 7L)).thenReturn(true);

        assertEquals("Vote recorded successfully", voteService.vote(1L, 7L, 10L));
        verify(redisVoteService).cachePollOptions(any(), any());
    }

    @Test
    void rejectsMissingOptionId() {
        assertThrows(InvalidVoteException.class, () -> voteService.vote(1L, 7L, null));
        verify(redisVoteService, never()).addVoter(anyLong(), anyLong());
    }
}
