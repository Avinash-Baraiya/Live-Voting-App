package com.livevoting.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class PollResultsResponse {

    private Long pollId;
    private String question;
    private Map<String, Long> results;
    private long totalVotes;
}
