package com.avi.voting.dto;

import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PollResultResponse {
    private Long pollId;
    private Map<String, Long> results;
}
