package com.avi.voting.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.avi.voting.dto.CreatePollRequest;
import com.avi.voting.dto.CreatePollResponse;
import com.avi.voting.service.PollService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/poll")
@RequiredArgsConstructor
public class PollController {

    private final PollService pollService;

    @PostMapping
    public CreatePollResponse createPoll(@RequestBody CreatePollRequest request) {
        return pollService.createPoll(request);
    }
}
