package com.taskscheduler.scheduling.model;

import com.taskscheduler.domain.entity.Role;

import java.util.List;

public record SchedulingUser(
        Long userId,
        Role role,
        List<TimeInterval> availabilities,
        List<TimeInterval> unavailabilities,
        List<TimeInterval> existingAssignments
) {
}
