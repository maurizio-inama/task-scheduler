package com.taskscheduler.controller.dto;

import com.taskscheduler.scheduling.model.SchedulingResult;
import com.taskscheduler.scheduling.model.UnscheduledTask;

import java.util.List;

public record GenerateResponse(
        Long scheduleId,
        int scheduledTaskCount,
        int createdAssignmentCount,
        List<AssignmentResponse> assignments,
        List<UnscheduledTaskResponse> unscheduledTasks
) {

    public record UnscheduledTaskResponse(
            Long taskId,
            String reason,
            String detail
    ) {
    }

    public static GenerateResponse from(
            Long scheduleId,
            SchedulingResult result,
            List<AssignmentResponse> assignments
    ) {
        List<UnscheduledTaskResponse> unscheduled = result.unscheduledTasks()
                .stream()
                .map(GenerateResponse::toUnscheduledResponse)
                .toList();

        return new GenerateResponse(
                scheduleId,
                result.scheduledTasks().size(),
                assignments.size(),
                assignments,
                unscheduled
        );
    }

    private static UnscheduledTaskResponse toUnscheduledResponse(
            UnscheduledTask unscheduled
    ) {
        return new UnscheduledTaskResponse(
                unscheduled.taskId(),
                unscheduled.reason().name(),
                unscheduled.detail()
        );
    }
}
