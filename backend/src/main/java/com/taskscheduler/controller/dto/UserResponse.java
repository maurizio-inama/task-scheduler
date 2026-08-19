package com.taskscheduler.controller.dto;

import com.taskscheduler.domain.entity.Role;
import com.taskscheduler.domain.entity.User;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String username,
        String firstName,
        String lastName,
        String email,
        Role role,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}