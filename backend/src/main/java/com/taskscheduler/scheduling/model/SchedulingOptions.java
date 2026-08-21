package com.taskscheduler.scheduling.model;

import com.taskscheduler.domain.entity.Role;

import java.util.Set;

public record SchedulingOptions(
        int maxMinutesPerDay,
        Set<Role> eligibleRoles
) {

    public static SchedulingOptions defaults() {
        return new SchedulingOptions(480, Set.of(Role.REVIEWER, Role.OPERATOR));
    }
}
