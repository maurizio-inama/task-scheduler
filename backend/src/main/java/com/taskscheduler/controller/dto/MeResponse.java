package com.taskscheduler.controller.dto;

import com.taskscheduler.domain.entity.Role;

public record MeResponse(
        Long id,
        String username,
        Role role
) {
}
