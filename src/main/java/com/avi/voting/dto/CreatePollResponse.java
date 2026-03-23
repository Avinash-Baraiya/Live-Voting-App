package com.avi.voting.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreatePollResponse {

    private Long pollId;
    private String message;
}
