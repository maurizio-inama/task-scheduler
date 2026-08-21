package com.taskscheduler.service;

import com.taskscheduler.domain.entity.Role;
import com.taskscheduler.domain.entity.User;
import com.taskscheduler.domain.repository.UserRepository;
import com.taskscheduler.exception.BusinessRuleException;
import com.taskscheduler.exception.EntityNotFoundException;
import com.taskscheduler.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository, passwordEncoder);
    }

    @Test
    void shouldCreateValidUser() {
        User user = createValidUser();

        when(passwordEncoder.encode(anyString())).thenReturn("encoded-hash");
        when(userRepository.existsByUsername(user.getUsername()))
            .thenReturn(false);
        when(userRepository.existsByEmail(user.getEmail()))
            .thenReturn(false);
        when(userRepository.save(user))
            .thenReturn(user);

        User result = userService.create(user);

        assertSame(user, result);
        verify(userRepository).save(user);
    }

    @Test
    void shouldRejectNullUser() {
        assertThrows(
            ValidationException.class,
            () -> userService.create(null)
        );

        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldRejectBlankUsername() {
        User user = createValidUser();
        user.setUsername(" ");

        assertThrows(
            ValidationException.class,
            () -> userService.create(user)
        );

        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldRejectInvalidEmail() {
        User user = createValidUser();
        user.setEmail("invalid-email");

        assertThrows(
            ValidationException.class,
            () -> userService.create(user)
        );

        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldRejectDuplicateUsername() {
        User user = createValidUser();

        when(userRepository.existsByUsername(user.getUsername()))
            .thenReturn(true);

        assertThrows(
            BusinessRuleException.class,
            () -> userService.create(user)
        );

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldRejectDuplicateEmail() {
        User user = createValidUser();

        when(userRepository.existsByUsername(user.getUsername()))
            .thenReturn(false);
        when(userRepository.existsByEmail(user.getEmail()))
            .thenReturn(true);

        assertThrows(
            BusinessRuleException.class,
            () -> userService.create(user)
        );

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldEncodePasswordBeforeSaving() {
        User user = createValidUser();

        when(passwordEncoder.encode("password")).thenReturn("encoded-hash");
        when(userRepository.existsByUsername(user.getUsername()))
            .thenReturn(false);
        when(userRepository.existsByEmail(user.getEmail()))
            .thenReturn(false);
        when(userRepository.save(any(User.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        userService.create(user);

        assertEquals("encoded-hash", user.getPassword());
        assertNotEquals("password", user.getPassword());
        verify(userRepository).save(argThat(saved ->
                "encoded-hash".equals(saved.getPassword())
        ));
    }

    @Test
    void shouldEncodePasswordOnUpdate() {
        User existing = createValidUser();
        User incoming = createValidUser();
        incoming.setPassword("new-password");

        when(passwordEncoder.encode("new-password")).thenReturn("encoded-hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        userService.update(1L, incoming);

        assertEquals("encoded-hash", existing.getPassword());
        assertNotEquals("new-password", existing.getPassword());
    }

    @Test
    void shouldFindExistingUser() {
        User user = createValidUser();

        when(userRepository.findById(1L))
            .thenReturn(Optional.of(user));

        User result = userService.getById(1L);

        assertSame(user, result);
    }

    @Test
    void shouldThrowWhenUserDoesNotExist() {
        when(userRepository.findById(1L))
            .thenReturn(Optional.empty());

        assertThrows(
            EntityNotFoundException.class,
            () -> userService.getById(1L)
        );
    }

    private User createValidUser() {
        return new User(
            "mauri",
            "password",
            "Maurizio",
            "Inama",
            "mauri@example.com",
            Role.OPERATOR,
            true
        );
    }
}