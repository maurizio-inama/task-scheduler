package com.taskscheduler.security;

import tools.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtService jwtService,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/api/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/tasks/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/tasks/**").hasAnyRole("ADMIN", "REVIEWER")
                        .requestMatchers(HttpMethod.PUT, "/api/tasks/**").hasAnyRole("ADMIN", "REVIEWER")
                        .requestMatchers(HttpMethod.DELETE, "/api/tasks/**").hasAnyRole("ADMIN", "REVIEWER")
                        .requestMatchers("/api/availability/**").authenticated()
                        .requestMatchers("/api/unavailability/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/schedules/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/schedules/**").hasAnyRole("ADMIN", "REVIEWER")
                        .requestMatchers(HttpMethod.PUT, "/api/schedules/**").hasAnyRole("ADMIN", "REVIEWER")
                        .requestMatchers(HttpMethod.DELETE, "/api/schedules/**").hasAnyRole("ADMIN", "REVIEWER")
                        .requestMatchers(HttpMethod.GET, "/api/assignments/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/assignments/**").hasAnyRole("ADMIN", "REVIEWER")
                        .requestMatchers(HttpMethod.PUT, "/api/assignments/**").hasAnyRole("ADMIN", "REVIEWER")
                        .requestMatchers(HttpMethod.DELETE, "/api/assignments/**").hasAnyRole("ADMIN", "REVIEWER")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService),
                        UsernamePasswordAuthenticationFilter.class
                );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
        return new RestAuthenticationEntryPoint(objectMapper);
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
        return new RestAccessDeniedHandler(objectMapper);
    }
}