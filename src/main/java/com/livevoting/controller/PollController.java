package com.livevoting.controller;

import com.livevoting.dto.*;
import com.livevoting.service.PollService;
import com.livevoting.service.VoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/poll")
@RequiredArgsConstructor
public class PollController {

    private final PollService pollService;
    private final VoteService voteService;

    @PostMapping
    public ResponseEntity<PollResponse> createPoll(@Valid @RequestBody CreatePollRequest request) {
        PollResponse response = pollService.createPoll(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{pollId}")
    public ResponseEntity<PollResponse> getPoll(@PathVariable Long pollId) {
        return ResponseEntity.ok(pollService.getPoll(pollId));
    }

    @PostMapping("/{pollId}/vote")
    public ResponseEntity<VoteResponse> vote(
            @PathVariable Long pollId,
            @Valid @RequestBody VoteRequest request) {
        VoteResponse response = voteService.castVote(pollId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{pollId}/results")
    public ResponseEntity<PollResultsResponse> getResults(@PathVariable Long pollId) {
        return ResponseEntity.ok(voteService.getResults(pollId));
    }
}
