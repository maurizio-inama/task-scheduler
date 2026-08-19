package com.taskscheduler.controller.dto;

import com.taskscheduler.domain.entity.Unavailability;
import com.taskscheduler.domain.entity.User;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreateUnavailabilityRequest(
        @NotNull Long userId,
        @NotNull LocalDateTime startDateTime,
        @NotNull LocalDateTime endDateTime,
        String reason
) {

    public Unavailability toEntity() {
        return new Unavailability(
                new User(userId),
                startDateTime,
                endDateTime,
                reason
        );
    }
}