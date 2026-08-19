package com.taskscheduler.controller.dto;

import com.taskscheduler.domain.entity.Assignment;
import com.taskscheduler.domain.entity.AssignmentStatus;

import java.time.LocalDateTime;

public record AssignmentResponse(
        Long id,
        Long userId,
        Long taskId,
        Long scheduleId,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        AssignmentStatus status
) {

    public static AssignmentResponse from(Assignment assignment) {
        return new AssignmentResponse(
                assignment.getId(),
                assignment.getUser().getId(),
                assignment.getTask().getId(),
                assignment.getSchedule().getId(),
                assignment.getStartDateTime(),
                assignment.getEndDateTime(),
                assignment.getStatus()
        );
    }
}