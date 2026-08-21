package com.taskscheduler.scheduling.model;

import java.util.List;

public record SchedulingResult(
        List<TaskSchedule> scheduledTasks,
        List<UnscheduledTask> unscheduledTasks
) {
}
