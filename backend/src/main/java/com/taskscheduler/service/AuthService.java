package com.taskscheduler.service;

import com.taskscheduler.controller.dto.AuthResponse;
import com.taskscheduler.controller.dto.LoginRequest;

public interface AuthService {

    AuthResponse login(LoginRequest request);
}