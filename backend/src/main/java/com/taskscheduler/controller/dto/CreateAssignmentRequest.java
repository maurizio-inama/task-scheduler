package com.taskscheduler.controller.dto;

import com.taskscheduler.domain.entity.Assignment;
import com.taskscheduler.domain.entity.AssignmentStatus;
import com.taskscheduler.domain.entity.Schedule;
import com.taskscheduler.domain.entity.Task;
import com.taskscheduler.domain.entity.User;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreateAssignmentRequest(
        @NotNull Long userId,
        @NotNull Long taskId,
        @NotNull Long scheduleId,
        @NotNull LocalDateTime startDateTime,
        @NotNull LocalDateTime endDateTime,
        @NotNull AssignmentStatus status
) {

    public Assignment toEntity() {
        Assignment assignment = new Assignment();
        assignment.setUser(new User(userId));
        assignment.setTask(new Task(taskId));
        assignment.setSchedule(new Schedule(scheduleId));
        assignment.setStartDateTime(startDateTime);
        assignment.setEndDateTime(endDateTime);
        assignment.setStatus(status);
        return assignment;
    }
}