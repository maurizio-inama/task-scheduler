package com.taskscheduler.controller.dto;

/**
 * Full content of a built-in scenario, returned as pretty-printed JSON for
 * preview purposes.
 */
public record ScenarioPreview(
        String fileName,
        String scenarioId,
        String name,
        String description,
        String content
) {
}
