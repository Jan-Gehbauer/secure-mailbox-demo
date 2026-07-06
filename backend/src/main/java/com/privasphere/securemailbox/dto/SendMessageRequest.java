package com.privasphere.securemailbox.dto;

import jakarta.validation.constraints.NotBlank;

public record SendMessageRequest(
        @NotBlank String sender,
        @NotBlank String recipient,
        @NotBlank String subject,
        @NotBlank String body
) {}
