package com.taskscheduler.service;

import com.taskscheduler.controller.dto.AuthResponse;
import com.taskscheduler.controller.dto.LoginRequest;
import com.taskscheduler.domain.entity.User;
import com.taskscheduler.domain.repository.UserRepository;
import com.taskscheduler.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public AuthServiceImpl(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            UserRepository userRepository
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username(),
                        request.password()
                )
        );

        String username = authentication.getName();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AuthenticationException("Invalid credentials") {
                });

        String token = jwtService.generateToken(user);

        return new AuthResponse(
                token,
                "Bearer",
                jwtService.getExpirationSeconds(),
                user.getUsername(),
                user.getRole()
        );
    }

    @Override
    public MeResponse me(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "User not found: " + username
                        )
                );

        return new MeResponse(
                user.getId(),
                user.getUsername(),
                user.getRole()
        );
    }
}