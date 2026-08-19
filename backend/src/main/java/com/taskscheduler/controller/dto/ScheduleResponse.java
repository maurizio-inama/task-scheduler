package com.taskscheduler.controller.dto;

import com.taskscheduler.domain.entity.Schedule;
import com.taskscheduler.domain.entity.ScheduleStatus;

import java.time.LocalDateTime;

public record ScheduleResponse(
        Long id,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        ScheduleStatus status,
        LocalDateTime createdAt
) {

    public static ScheduleResponse from(Schedule schedule) {
        return new ScheduleResponse(
                schedule.getId(),
                schedule.getStartDateTime(),
                schedule.getEndDateTime(),
                schedule.getStatus(),
                schedule.getCreatedAt()
        );
    }
}