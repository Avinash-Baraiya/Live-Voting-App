package com.avi.voting.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PollOptionResponse {

    private Long optionId;
    private String optionText;
}
