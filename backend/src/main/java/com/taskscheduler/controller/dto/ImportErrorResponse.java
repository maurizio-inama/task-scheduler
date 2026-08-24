package com.taskscheduler.controller.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Structured body returned when a scenario import is rejected. Extends the
 * common {@link ApiError} shape with the rejection {@code kind} and the
 * detailed list of {@code problems}.
 */
public record ImportErrorResponse(
        int status,
        String error,
        String kind,
        String scenarioId,
        List<String> problems,
        String path,
        LocalDateTime timestamp
) {
}
