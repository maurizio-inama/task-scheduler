package com.taskscheduler.security;

import com.taskscheduler.controller.AuthController;
import com.taskscheduler.controller.TaskController;
import com.taskscheduler.controller.UserController;
import com.taskscheduler.controller.dto.AuthResponse;
import com.taskscheduler.controller.dto.LoginRequest;
import com.taskscheduler.domain.entity.Role;
import com.taskscheduler.domain.entity.Task;
import com.taskscheduler.domain.entity.TaskPriority;
import com.taskscheduler.domain.entity.TaskStatus;
import com.taskscheduler.domain.entity.User;
import com.taskscheduler.service.AuthService;
import com.taskscheduler.service.TaskService;
import com.taskscheduler.service.UserService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        TaskController.class,
        UserController.class,
        AuthController.class
})
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=integration-test-secret-key-long-enough-for-hs256-signing-1234567890",
        "jwt.expiration=3600"
})
class SecurityAuthorizationTest {

    private static final String TEST_SECRET =
            "integration-test-secret-key-long-enough-for-hs256-signing-1234567890";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private TaskService taskService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private String tokenFor(String username, Role role) {
        User user = new User(
                username,
                "encoded-hash",
                "First",
                "Last",
                username + "@example.com",
                role,
                true
        );
        return jwtService.generateToken(user);
    }

    private Task task() {
        Task task = new Task(1L);
        task.setTitle("Report");
        task.setStatus(TaskStatus.PENDING);
        task.setPriority(TaskPriority.MEDIUM);
        task.setEstimatedDurationMinutes(60);
        return task;
    }

    @Test
    void loginEndpointRemainsPublic() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new AuthResponse("token", "Bearer", 3600, "alice", Role.ADMIN));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "secret"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token"));
    }

    @Test
    void protectedEndpointRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/tasks"));
    }

    @Test
    void protectedEndpointRejectsMalformedToken() throws Exception {
        mockMvc.perform(get("/api/tasks")
                        .header("Authorization", "Bearer not-a-valid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void protectedEndpointRejectsInvalidSignature() throws Exception {
        String token = Jwts.builder()
                .subject("alice")
                .claim("role", "ADMIN")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(
                                "another-secret-key-that-is-long-enough-to-sign-000000000000"
                                        .getBytes(StandardCharsets.UTF_8)),
                        Jwts.SIG.HS256)
                .compact();

        mockMvc.perform(get("/api/tasks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void protectedEndpointRejectsExpiredToken() throws Exception {
        String token = Jwts.builder()
                .subject("alice")
                .claim("role", "ADMIN")
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)),
                        Jwts.SIG.HS256)
                .compact();

        mockMvc.perform(get("/api/tasks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void validTokenAllowsTaskReadForAdmin() throws Exception {
        when(taskService.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/tasks")
                        .header("Authorization",
                                "Bearer " + tokenFor("alice", Role.ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void anyAuthenticatedRoleCanReadTasks() throws Exception {
        when(taskService.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/tasks")
                        .header("Authorization",
                                "Bearer " + tokenFor("bob", Role.OPERATOR)))
                .andExpect(status().isOk());
    }

    @Test
    void operatorCannotCreateTask() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .header("Authorization",
                                "Bearer " + tokenFor("bob", Role.OPERATOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Report",
                                  "status": "PENDING",
                                  "priority": "MEDIUM",
                                  "estimatedDurationMinutes": 60
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.path").value("/api/tasks"));
    }

    @Test
    void reviewerCanCreateTask() throws Exception {
        when(taskService.create(any(Task.class))).thenReturn(task());

        mockMvc.perform(post("/api/tasks")
                        .header("Authorization",
                                "Bearer " + tokenFor("reviewer", Role.REVIEWER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Report",
                                  "status": "PENDING",
                                  "priority": "MEDIUM",
                                  "estimatedDurationMinutes": 60
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Report"));
    }

    @Test
    void operatorCannotAccessUserManagement() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("Authorization",
                                "Bearer " + tokenFor("bob", Role.OPERATOR)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void reviewerCannotAccessUserManagement() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("Authorization",
                                "Bearer " + tokenFor("reviewer", Role.REVIEWER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void adminCanAccessUserManagement() throws Exception {
        when(userService.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/users")
                        .header("Authorization",
                                "Bearer " + tokenFor("alice", Role.ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanCreateUser() throws Exception {
        User user = new User(1L);
        user.setUsername("newuser");
        user.setFirstName("New");
        user.setLastName("User");
        user.setEmail("new@example.com");
        user.setRole(Role.OPERATOR);
        user.setEnabled(true);
        when(userService.create(any(User.class))).thenReturn(user);

        mockMvc.perform(post("/api/users")
                        .header("Authorization",
                                "Bearer " + tokenFor("alice", Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "newuser",
                                  "password": "secret",
                                  "firstName": "New",
                                  "lastName": "User",
                                  "email": "new@example.com",
                                  "role": "OPERATOR",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }
}