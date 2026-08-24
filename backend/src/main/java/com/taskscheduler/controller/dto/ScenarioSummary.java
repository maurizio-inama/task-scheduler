package com.taskscheduler.controller.dto;

/**
 * Catalog entry for a built-in demo scenario.
 */
public record ScenarioSummary(
        String fileName,
        String scenarioId,
        String name,
        String description
) {
}
