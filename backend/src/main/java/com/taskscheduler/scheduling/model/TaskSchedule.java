package com.taskscheduler.scheduling.model;

import java.util.List;

public record TaskSchedule(
        Long taskId,
        Long userId,
        List<Allocation> allocations
) {

    public long totalMinutes() {
        return allocations.stream()
                .mapToLong(Allocation::durationMinutes)
                .sum();
    }
}
