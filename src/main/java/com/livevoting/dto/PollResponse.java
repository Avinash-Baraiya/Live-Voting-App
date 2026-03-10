package com.livevoting.dto;

import com.livevoting.entity.Poll;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PollResponse {

    private Long id;
    private String question;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private String status;
    private List<PollOptionResponse> options;

    @Data
    @Builder
    public static class PollOptionResponse {
        private Long id;
        private String optionText;
    }

    public static PollResponse from(Poll poll) {
        List<PollOptionResponse> optionResponses = poll.getOptions().stream()
                .map(opt -> PollOptionResponse.builder()
                        .id(opt.getId())
                        .optionText(opt.getOptionText())
                        .build())
                .toList();

        return PollResponse.builder()
                .id(poll.getId())
                .question(poll.getQuestion())
                .createdAt(poll.getCreatedAt())
                .expiresAt(poll.getExpiresAt())
                .status(poll.getStatus().name())
                .options(optionResponses)
                .build();
    }
}
