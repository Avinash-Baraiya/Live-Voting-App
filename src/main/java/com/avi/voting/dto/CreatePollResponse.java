package com.avi.voting.dto;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreatePollResponse {

    private Long pollId;
    private String message;
    private List<PollOptionResponse> options;
}
