package com.livevoting.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livevoting.dto.*;
import com.livevoting.exception.DuplicateVoteException;
import com.livevoting.exception.PollNotFoundException;
import com.livevoting.service.PollService;
import com.livevoting.service.VoteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PollController.class)
class PollControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PollService pollService;

    @MockBean
    private VoteService voteService;

    @Test
    @DisplayName("POST /poll - should create poll and return 201")
    void createPoll_shouldReturn201() throws Exception {
        CreatePollRequest request = new CreatePollRequest();
        request.setQuestion("Who will win IPL?");
        request.setOptions(List.of("CSK", "MI", "RCB"));
        request.setExpiresAt(LocalDateTime.now().plusDays(7));

        PollResponse mockResponse = PollResponse.builder()
                .id(1L)
                .question("Who will win IPL?")
                .status("ACTIVE")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .options(List.of(
                        PollResponse.PollOptionResponse.builder().id(1L).optionText("CSK").build(),
                        PollResponse.PollOptionResponse.builder().id(2L).optionText("MI").build(),
                        PollResponse.PollOptionResponse.builder().id(3L).optionText("RCB").build()
                ))
                .build();

        when(pollService.createPoll(any(CreatePollRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/poll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.question").value("Who will win IPL?"))
                .andExpect(jsonPath("$.options").isArray())
                .andExpect(jsonPath("$.options.length()").value(3));
    }

    @Test
    @DisplayName("POST /poll - should return 400 for invalid request")
    void createPoll_shouldReturn400WhenInvalid() throws Exception {
        CreatePollRequest request = new CreatePollRequest();
        request.setQuestion(""); // blank question
        request.setOptions(List.of("CSK"));

        mockMvc.perform(post("/poll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /poll/{pollId} - should return poll details")
    void getPoll_shouldReturn200() throws Exception {
        PollResponse mockResponse = PollResponse.builder()
                .id(1L)
                .question("Who will win IPL?")
                .status("ACTIVE")
                .options(List.of())
                .build();

        when(pollService.getPoll(1L)).thenReturn(mockResponse);

        mockMvc.perform(get("/poll/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.question").value("Who will win IPL?"));
    }

    @Test
    @DisplayName("GET /poll/{pollId} - should return 404 when not found")
    void getPoll_shouldReturn404WhenNotFound() throws Exception {
        when(pollService.getPoll(99L)).thenThrow(new PollNotFoundException(99L));

        mockMvc.perform(get("/poll/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("POST /poll/{pollId}/vote - should cast vote and return 201")
    void vote_shouldReturn201() throws Exception {
        VoteRequest request = new VoteRequest();
        request.setUserId(100L);
        request.setOptionId(1L);

        VoteResponse mockResponse = VoteResponse.builder()
                .message("Vote cast successfully")
                .pollId(1L)
                .optionId(1L)
                .userId(100L)
                .build();

        when(voteService.castVote(eq(1L), any(VoteRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/poll/1/vote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Vote cast successfully"))
                .andExpect(jsonPath("$.pollId").value(1))
                .andExpect(jsonPath("$.userId").value(100));
    }

    @Test
    @DisplayName("POST /poll/{pollId}/vote - should return 409 for duplicate vote")
    void vote_shouldReturn409ForDuplicateVote() throws Exception {
        VoteRequest request = new VoteRequest();
        request.setUserId(100L);
        request.setOptionId(1L);

        when(voteService.castVote(eq(1L), any(VoteRequest.class)))
                .thenThrow(new DuplicateVoteException(100L, 1L));

        mockMvc.perform(post("/poll/1/vote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("GET /poll/{pollId}/results - should return vote counts")
    void getResults_shouldReturn200() throws Exception {
        PollResultsResponse mockResults = PollResultsResponse.builder()
                .pollId(1L)
                .question("Who will win IPL?")
                .results(Map.of("CSK", 1200L, "MI", 980L, "RCB", 1500L))
                .totalVotes(3680L)
                .build();

        when(voteService.getResults(1L)).thenReturn(mockResults);

        mockMvc.perform(get("/poll/1/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pollId").value(1))
                .andExpect(jsonPath("$.totalVotes").value(3680))
                .andExpect(jsonPath("$.results.CSK").value(1200));
    }
}
