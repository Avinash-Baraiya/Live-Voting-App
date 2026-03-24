package com.avi.voting.dto;

import lombok.Data;

@Data
public class VoteRequest {
    private Long userId;
    private Long optionId;
}
