package com.taskscheduler.service;

import com.taskscheduler.controller.dto.AuthResponse;
import com.taskscheduler.controller.dto.LoginRequest;
import com.taskscheduler.controller.dto.MeResponse;

public interface AuthService {

    AuthResponse login(LoginRequest request);

    MeResponse me(String username);
}