package com.taskscheduler.scheduling.model;

public record UnscheduledTask(
        Long taskId,
        SchedulingFailureReason reason,
        String detail
) {
}
