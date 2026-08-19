package com.taskscheduler.controller.dto;

import com.taskscheduler.domain.entity.Unavailability;

import java.time.LocalDateTime;

public record UnavailabilityResponse(
        Long id,
        Long userId,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        String reason
) {

    public static UnavailabilityResponse from(Unavailability unavailability) {
        return new UnavailabilityResponse(
                unavailability.getId(),
                unavailability.getUser().getId(),
                unavailability.getStartDateTime(),
                unavailability.getEndDateTime(),
                unavailability.getReason()
        );
    }
}