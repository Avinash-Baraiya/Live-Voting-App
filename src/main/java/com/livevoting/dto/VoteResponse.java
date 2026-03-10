package com.livevoting.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VoteResponse {

    private String message;
    private Long pollId;
    private Long optionId;
    private Long userId;
}
