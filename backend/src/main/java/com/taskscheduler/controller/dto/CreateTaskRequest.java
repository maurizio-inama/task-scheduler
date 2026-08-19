package com.taskscheduler.controller.dto;

import com.taskscheduler.domain.entity.Task;
import com.taskscheduler.domain.entity.TaskPriority;
import com.taskscheduler.domain.entity.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record CreateTaskRequest(
        @NotBlank String title,
        String description,
        @NotNull TaskStatus status,
        @NotNull TaskPriority priority,
        @NotNull @Positive Integer estimatedDurationMinutes,
        LocalDateTime deadline
) {

    public Task toEntity() {
        return new Task(
                title,
                description,
                status,
                priority,
                estimatedDurationMinutes,
                deadline
        );
    }
}