package com.livevoting.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CreatePollRequest {

    @NotBlank(message = "Poll question must not be blank")
    @Size(max = 500, message = "Question must not exceed 500 characters")
    private String question;

    @NotEmpty(message = "Options must not be empty")
    @Size(min = 2, max = 10, message = "Poll must have between 2 and 10 options")
    private List<@NotBlank String> options;

    @Future(message = "Expiry date must be in the future")
    private LocalDateTime expiresAt;
}
