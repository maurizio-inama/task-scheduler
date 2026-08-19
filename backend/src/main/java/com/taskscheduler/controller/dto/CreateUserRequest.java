package com.taskscheduler.controller.dto;

import com.taskscheduler.domain.entity.Role;
import com.taskscheduler.domain.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateUserRequest(
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank @Email String email,
        @NotNull Role role,
        boolean enabled
) {

    public User toEntity() {
        return new User(
                username,
                password,
                firstName,
                lastName,
                email,
                role,
                enabled
        );
    }
}