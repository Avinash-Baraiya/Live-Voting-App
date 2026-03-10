package com.livevoting.service;

import com.livevoting.dto.CreatePollRequest;
import com.livevoting.dto.PollResponse;
import com.livevoting.entity.Poll;
import com.livevoting.entity.PollOption;
import com.livevoting.exception.PollNotFoundException;
import com.livevoting.repository.PollRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PollServiceTest {

    @Mock
    private PollRepository pollRepository;

    @InjectMocks
    private PollService pollService;

    private Poll samplePoll;

    @BeforeEach
    void setUp() {
        PollOption optionCSK = PollOption.builder().id(1L).optionText("CSK").build();
        PollOption optionMI = PollOption.builder().id(2L).optionText("MI").build();
        PollOption optionRCB = PollOption.builder().id(3L).optionText("RCB").build();

        samplePoll = Poll.builder()
                .id(1L)
                .question("Who will win IPL?")
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .status(Poll.PollStatus.ACTIVE)
                .options(List.of(optionCSK, optionMI, optionRCB))
                .build();

        optionCSK.setPoll(samplePoll);
        optionMI.setPoll(samplePoll);
        optionRCB.setPoll(samplePoll);
    }

    @Test
    @DisplayName("Should create poll successfully")
    void createPoll_shouldReturnPollResponse() {
        CreatePollRequest request = new CreatePollRequest();
        request.setQuestion("Who will win IPL?");
        request.setOptions(List.of("CSK", "MI", "RCB"));
        request.setExpiresAt(LocalDateTime.now().plusDays(7));

        when(pollRepository.save(any(Poll.class))).thenReturn(samplePoll);

        PollResponse response = pollService.createPoll(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getQuestion()).isEqualTo("Who will win IPL?");
        assertThat(response.getOptions()).hasSize(3);
        verify(pollRepository, times(1)).save(any(Poll.class));
    }

    @Test
    @DisplayName("Should get poll by ID successfully")
    void getPoll_shouldReturnPollResponse() {
        when(pollRepository.findById(1L)).thenReturn(Optional.of(samplePoll));

        PollResponse response = pollService.getPoll(1L);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getQuestion()).isEqualTo("Who will win IPL?");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        verify(pollRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Should throw PollNotFoundException when poll not found")
    void getPoll_shouldThrowWhenNotFound() {
        when(pollRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pollService.getPoll(99L))
                .isInstanceOf(PollNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("Should return poll entity for voting")
    void getPollEntity_shouldReturnPoll() {
        when(pollRepository.findById(1L)).thenReturn(Optional.of(samplePoll));

        Poll poll = pollService.getPollEntity(1L);

        assertThat(poll).isNotNull();
        assertThat(poll.getId()).isEqualTo(1L);
        assertThat(poll.isActive()).isTrue();
    }

    @Test
    @DisplayName("Should create poll with correct option texts")
    void createPoll_shouldSetOptionTextsCorrectly() {
        List<String> optionTexts = List.of("Option A", "Option B", "Option C");
        CreatePollRequest request = new CreatePollRequest();
        request.setQuestion("Test question?");
        request.setOptions(optionTexts);
        request.setExpiresAt(LocalDateTime.now().plusHours(1));

        when(pollRepository.save(any(Poll.class))).thenAnswer(inv -> {
            Poll p = inv.getArgument(0);
            p.setId(2L);
            return p;
        });

        PollResponse response = pollService.createPoll(request);

        assertThat(response.getOptions()).extracting(PollResponse.PollOptionResponse::getOptionText)
                .containsExactlyInAnyOrder("Option A", "Option B", "Option C");
    }
}
