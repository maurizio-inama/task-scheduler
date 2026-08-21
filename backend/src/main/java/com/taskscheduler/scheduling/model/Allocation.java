package com.taskscheduler.scheduling.model;

import java.time.Duration;
import java.time.LocalDateTime;

public record Allocation(LocalDateTime startDateTime, LocalDateTime endDateTime) {

    public long durationMinutes() {
        return Duration.between(startDateTime, endDateTime).toMinutes();
    }
}
