package com.taskscheduler.scheduling.model;

import java.time.LocalDateTime;
import java.util.List;

public record SchedulingRequest(
        LocalDateTime windowStart,
        LocalDateTime windowEnd,
        List<SchedulingTask> tasks,
        List<SchedulingUser> users,
        SchedulingOptions options
) {
}
