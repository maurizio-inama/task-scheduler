package com.taskscheduler.scheduling.model;

public enum SchedulingFailureReason {
    NO_ELIGIBLE_USER,
    OUTSIDE_TASK_WINDOW,
    NO_AVAILABLE_CAPACITY,
    INSUFFICIENT_CAPACITY
}
