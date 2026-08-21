package com.taskscheduler.controller;

import com.taskscheduler.domain.entity.Role;
import com.taskscheduler.domain.entity.User;
import com.taskscheduler.exception.BusinessRuleException;
import com.taskscheduler.exception.EntityNotFoundException;
import com.taskscheduler.exception.ValidationException;
import com.taskscheduler.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    private User user(Long id) {
        User user = new User(id);
        user.setUsername("alice");
        user.setPassword("secret");
        user.setFirstName("Alice");
        user.setLastName("Smith");
        user.setEmail("alice@example.com");
        user.setRole(Role.OPERATOR);
        user.setEnabled(true);
        return user;
    }

    @Test
    void shouldCreateUser() throws Exception {
        when(userService.create(any(User.class))).thenReturn(user(1L));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "secret",
                                  "firstName": "Alice",
                                  "lastName": "Smith",
                                  "email": "alice@example.com",
                                  "role": "OPERATOR",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.lastName").value("Smith"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.role").value("OPERATOR"))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void shouldGetAllUsers() throws Exception {
        when(userService.getAll()).thenReturn(List.of(user(1L), user(2L)));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].username").value("alice"))
                .andExpect(jsonPath("$[1].id").value(2));
    }

    @Test
    void shouldGetUserById() throws Exception {
        when(userService.getById(1L)).thenReturn(user(1L));

        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void shouldReturnNotFoundWhenUserMissing() throws Exception {
        when(userService.getById(99L))
                .thenThrow(new EntityNotFoundException("User not found: 99"));

        mockMvc.perform(get("/api/users/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("ENTITY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("User not found: 99"))
                .andExpect(jsonPath("$.path").value("/api/users/99"));
    }

    @Test
    void shouldUpdateUser() throws Exception {
        when(userService.update(eq(1L), any(User.class))).thenReturn(user(1L));

        mockMvc.perform(put("/api/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "newsecret",
                                  "firstName": "Alice",
                                  "lastName": "Smith",
                                  "email": "alice@example.com",
                                  "role": "ADMIN",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void shouldDeleteUser() throws Exception {
        mockMvc.perform(delete("/api/users/1"))
                .andExpect(status().isNoContent());

        verify(userService).delete(1L);
    }

    @Test
    void shouldRejectMissingRequiredField() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "password": "secret",
                                  "firstName": "Alice",
                                  "lastName": "Smith",
                                  "email": "alice@example.com",
                                  "role": "OPERATOR",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("username: must not be blank"))
                .andExpect(jsonPath("$.path").value("/api/users"));
    }

    @Test
    void shouldRejectInvalidEmailFormat() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "secret",
                                  "firstName": "Alice",
                                  "lastName": "Smith",
                                  "email": "not-an-email",
                                  "role": "OPERATOR",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldRejectMalformedRequestBody() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not valid json }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void shouldReturnConflictOnBusinessRuleViolation() throws Exception {
        when(userService.create(any(User.class)))
                .thenThrow(new BusinessRuleException(
                        "Username already exists: alice"
                ));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "secret",
                                  "firstName": "Alice",
                                  "lastName": "Smith",
                                  "email": "alice@example.com",
                                  "role": "OPERATOR",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Username already exists: alice"));
    }

    @Test
    void shouldReturnUnprocessableEntityOnServiceValidation() throws Exception {
        when(userService.create(any(User.class)))
                .thenThrow(new ValidationException("Invalid email format"));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "secret",
                                  "firstName": "Alice",
                                  "lastName": "Smith",
                                  "email": "alice@example.com",
                                  "role": "OPERATOR",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Invalid email format"));
    }
}