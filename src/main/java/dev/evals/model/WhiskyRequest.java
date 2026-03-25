package dev.evals.model;

import io.swagger.v3.oas.annotations.media.Schema;

public record WhiskyRequest(
        String message,
        String conversationId,
        @Schema(description = "Credit card number (optional)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String ccNumber
) {
}
