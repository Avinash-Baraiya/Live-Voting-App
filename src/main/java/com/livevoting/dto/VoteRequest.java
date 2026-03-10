package com.livevoting.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VoteRequest {

    @NotNull(message = "userId must not be null")
    private Long userId;

    @NotNull(message = "optionId must not be null")
    private Long optionId;
}
