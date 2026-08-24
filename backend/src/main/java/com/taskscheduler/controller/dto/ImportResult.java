package com.taskscheduler.controller.dto;

/**
 * Summary of a successful scenario import.
 */
public record ImportResult(
        String scenarioId,
        String scenarioName,
        int usersCreated,
        int tasksCreated,
        int availabilitiesCreated,
        int unavailabilitiesCreated,
        int schedulesCreated,
        int tasksScheduled
) {
}
