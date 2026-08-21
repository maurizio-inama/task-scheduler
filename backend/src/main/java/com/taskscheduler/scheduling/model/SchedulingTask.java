package com.taskscheduler.scheduling.model;

import com.taskscheduler.domain.entity.TaskPriority;

import java.time.LocalDateTime;

public record SchedulingTask(
        Long taskId,
        TaskPriority priority,
        int estimatedDurationMinutes,
        LocalDateTime deadline
) {
}
