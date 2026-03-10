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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoteServiceTest {

    @Mock
    private PollService pollService;

    @Mock
    private RedisVoteService redisVoteService;

    @InjectMocks
    private VoteService voteService;

    private Poll activePoll;
    private Poll expiredPoll;

    @BeforeEach
    void setUp() {
        PollOption optionCSK = PollOption.builder().id(1L).optionText("CSK").build();
        PollOption optionMI = PollOption.builder().id(2L).optionText("MI").build();
        PollOption optionRCB = PollOption.builder().id(3L).optionText("RCB").build();

        activePoll = Poll.builder()
                .id(1L)
                .question("Who will win IPL?")
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .status(Poll.PollStatus.ACTIVE)
                .options(List.of(optionCSK, optionMI, optionRCB))
                .build();

        expiredPoll = Poll.builder()
                .id(2L)
                .question("Expired poll?")
                .createdAt(LocalDateTime.now().minusDays(10))
                .expiresAt(LocalDateTime.now().minusDays(1))
                .status(Poll.PollStatus.ACTIVE)
                .options(List.of(optionCSK))
                .build();
    }

    @Test
    @DisplayName("Should cast vote successfully")
    void castVote_shouldSucceed() {
        VoteRequest request = new VoteRequest();
        request.setUserId(100L);
        request.setOptionId(1L);

        when(pollService.getPollEntity(1L)).thenReturn(activePoll);
        when(redisVoteService.recordVote(1L, 100L, 1L)).thenReturn(true);

        VoteResponse response = voteService.castVote(1L, request);

        assertThat(response.getMessage()).isEqualTo("Vote cast successfully");
        assertThat(response.getPollId()).isEqualTo(1L);
        assertThat(response.getUserId()).isEqualTo(100L);
        assertThat(response.getOptionId()).isEqualTo(1L);
        verify(redisVoteService).recordVote(1L, 100L, 1L);
    }

    @Test
    @DisplayName("Should throw DuplicateVoteException when user already voted")
    void castVote_shouldThrowDuplicateVoteException() {
        VoteRequest request = new VoteRequest();
        request.setUserId(100L);
        request.setOptionId(1L);

        when(pollService.getPollEntity(1L)).thenReturn(activePoll);
        when(redisVoteService.recordVote(1L, 100L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> voteService.castVote(1L, request))
                .isInstanceOf(DuplicateVoteException.class)
                .hasMessageContaining("100")
                .hasMessageContaining("1");
    }

    @Test
    @DisplayName("Should throw PollNotActiveException when poll is expired")
    void castVote_shouldThrowWhenPollExpired() {
        VoteRequest request = new VoteRequest();
        request.setUserId(100L);
        request.setOptionId(1L);

        when(pollService.getPollEntity(2L)).thenReturn(expiredPoll);

        assertThatThrownBy(() -> voteService.castVote(2L, request))
                .isInstanceOf(PollNotActiveException.class);
    }

    @Test
    @DisplayName("Should throw InvalidOptionException for wrong option")
    void castVote_shouldThrowForInvalidOption() {
        VoteRequest request = new VoteRequest();
        request.setUserId(100L);
        request.setOptionId(99L); // non-existent option

        when(pollService.getPollEntity(1L)).thenReturn(activePoll);

        assertThatThrownBy(() -> voteService.castVote(1L, request))
                .isInstanceOf(InvalidOptionException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("Should return poll results with correct counts")
    void getResults_shouldReturnCorrectResults() {
        when(pollService.getPollEntity(1L)).thenReturn(activePoll);
        when(redisVoteService.getVoteCounts(eq(1L), anySet()))
                .thenReturn(Map.of(1L, 1200L, 2L, 980L, 3L, 1500L));

        PollResultsResponse results = voteService.getResults(1L);

        assertThat(results.getPollId()).isEqualTo(1L);
        assertThat(results.getResults()).containsEntry("CSK", 1200L);
        assertThat(results.getResults()).containsEntry("MI", 980L);
        assertThat(results.getResults()).containsEntry("RCB", 1500L);
        assertThat(results.getTotalVotes()).isEqualTo(3680L);
    }

    @Test
    @DisplayName("Should return zero counts when no votes cast")
    void getResults_shouldReturnZeroWhenNoVotes() {
        when(pollService.getPollEntity(1L)).thenReturn(activePoll);
        when(redisVoteService.getVoteCounts(eq(1L), anySet())).thenReturn(Map.of());

        PollResultsResponse results = voteService.getResults(1L);

        assertThat(results.getTotalVotes()).isEqualTo(0L);
        assertThat(results.getResults()).containsEntry("CSK", 0L);
        assertThat(results.getResults()).containsEntry("MI", 0L);
        assertThat(results.getResults()).containsEntry("RCB", 0L);
    }
}
