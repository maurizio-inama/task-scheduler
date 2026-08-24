package com.taskscheduler.controller.dto;

import java.util.List;

/**
 * Result of a scenario validation. {@code status} is one of
 * {@code VALID}, {@code INVALID} or {@code CONFLICT}; {@code problems}
 * lists every reason why the document was rejected.
 */
public record ValidationReport(
        boolean valid,
        String status,
        String scenarioId,
        String scenarioName,
        Counts counts,
        List<String> problems
) {

    public record Counts(
            int users,
            int tasks,
            int availabilities,
            int unavailabilities,
            int schedules
    ) {
    }
}
