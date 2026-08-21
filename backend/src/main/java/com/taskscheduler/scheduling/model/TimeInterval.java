package com.taskscheduler.scheduling.model;

import java.time.Duration;
import java.time.LocalDateTime;

public record TimeInterval(LocalDateTime start, LocalDateTime end) {

    public long durationMinutes() {
        return Duration.between(start, end).toMinutes();
    }

    public boolean isEmpty() {
        return !start.isBefore(end);
    }

    public boolean overlaps(TimeInterval other) {
        return start.isBefore(other.end()) && other.start().isBefore(end);
    }
}
