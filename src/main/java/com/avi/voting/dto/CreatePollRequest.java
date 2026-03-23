package com.avi.voting.dto;

import java.time.Instant;
import java.util.List;

import lombok.Data;

@Data
public class CreatePollRequest {

    private String question;
    private List<String> options;
    private Instant expiresAt;
}
