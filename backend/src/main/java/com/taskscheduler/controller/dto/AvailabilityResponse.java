package com.taskscheduler.controller.dto;

import com.taskscheduler.domain.entity.Availability;

import java.time.LocalDateTime;

public record AvailabilityResponse(
        Long id,
        Long userId,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime
) {

    public static AvailabilityResponse from(Availability availability) {
        return new AvailabilityResponse(
                availability.getId(),
                availability.getUser().getId(),
                availability.getStartDateTime(),
                availability.getEndDateTime()
        );
    }
}