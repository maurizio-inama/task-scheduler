package com.taskscheduler.controller.dto;

import com.taskscheduler.domain.entity.Role;

public record AuthResponse(
        String token,
        String tokenType,
        long expiresIn,
        String username,
        Role role
) {
}