package com.taskscheduler.controller.dto;

import com.taskscheduler.domain.entity.Task;
import com.taskscheduler.domain.entity.TaskPriority;
import com.taskscheduler.domain.entity.TaskStatus;

import java.time.LocalDateTime;

public record TaskResponse(
        Long id,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        Integer estimatedDurationMinutes,
        LocalDateTime deadline,
        LocalDateTime createdAt
) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getEstimatedDurationMinutes(),
                task.getDeadline(),
                task.getCreatedAt()
        );
    }
}