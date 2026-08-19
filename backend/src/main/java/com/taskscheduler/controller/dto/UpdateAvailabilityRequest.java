package com.taskscheduler.controller.dto;

import com.taskscheduler.domain.entity.Availability;
import com.taskscheduler.domain.entity.User;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record UpdateAvailabilityRequest(
        @NotNull Long userId,
        @NotNull LocalDateTime startDateTime,
        @NotNull LocalDateTime endDateTime
) {

    public Availability toEntity() {
        return new Availability(
                new User(userId),
                startDateTime,
                endDateTime
        );
    }
}