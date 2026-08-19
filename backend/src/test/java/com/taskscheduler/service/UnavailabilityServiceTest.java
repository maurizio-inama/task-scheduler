package com.taskscheduler.service;

import com.taskscheduler.domain.entity.Unavailability;
import com.taskscheduler.domain.entity.User;
import com.taskscheduler.domain.repository.UnavailabilityRepository;
import com.taskscheduler.domain.repository.UserRepository;
import com.taskscheduler.exception.BusinessRuleException;
import com.taskscheduler.exception.EntityNotFoundException;
import com.taskscheduler.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnavailabilityServiceTest {

    @Mock
    private UnavailabilityRepository unavailabilityRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UnavailabilityServiceImpl unavailabilityService;

    private User user;
    private Unavailability unavailability;

    @BeforeEach
    void setUp() {
        user = mock(User.class);

        unavailability = new Unavailability(
                user,
                LocalDateTime.of(2026, 8, 20, 9, 0),
                LocalDateTime.of(2026, 8, 20, 17, 0),
                "Vacation"
        );
    }

    @Test
    void shouldCreateUnavailability() {
        when(user.getId()).thenReturn(1L);

        when(userRepository.existsById(1L))
                .thenReturn(true);

        when(unavailabilityRepository
                .existsByUserIdAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
                        eq(1L),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class)))
                .thenReturn(false);

        when(unavailabilityRepository.save(unavailability))
                .thenReturn(unavailability);

        Unavailability result =
                unavailabilityService.create(unavailability);

        assertSame(unavailability, result);

        verify(unavailabilityRepository)
                .save(unavailability);
    }

    @Test
    void shouldRejectNullUnavailability() {
        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> unavailabilityService.create(null)
        );

        assertEquals(
                "Unavailability must not be null",
                exception.getMessage()
        );

        verifyNoInteractions(userRepository);
        verifyNoInteractions(unavailabilityRepository);
    }

    @Test
    void shouldRejectMissingUser() {
        unavailability.setUser(null);

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> unavailabilityService.create(unavailability)
        );

        assertEquals(
                "User must not be null",
                exception.getMessage()
        );

        verifyNoInteractions(userRepository);
        verifyNoInteractions(unavailabilityRepository);
    }

    @Test
    void shouldRejectInvalidDateRange() {
        unavailability.setStartDateTime(
                LocalDateTime.of(2026, 8, 20, 17, 0)
        );

        unavailability.setEndDateTime(
                LocalDateTime.of(2026, 8, 20, 9, 0)
        );

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> unavailabilityService.create(unavailability)
        );

        assertEquals(
                "Start date time must be before end date time",
                exception.getMessage()
        );

        verifyNoInteractions(userRepository);
        verifyNoInteractions(unavailabilityRepository);
    }

    @Test
    void shouldRejectWhenUserDoesNotExist() {
        when(user.getId()).thenReturn(1L);

        when(userRepository.existsById(1L))
                .thenReturn(false);

        EntityNotFoundException exception = assertThrows(
                EntityNotFoundException.class,
                () -> unavailabilityService.create(unavailability)
        );

        assertEquals(
                "User not found: 1",
                exception.getMessage()
        );

        verify(unavailabilityRepository, never())
                .save(any());

        verifyNoInteractions(
                unavailabilityRepository
        );
    }

    @Test
    void shouldRejectOverlapWithExistingUnavailability() {
        when(user.getId()).thenReturn(1L);

        when(userRepository.existsById(1L))
                .thenReturn(true);

        when(unavailabilityRepository
                .existsByUserIdAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
                        eq(1L),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class)))
                .thenReturn(true);

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> unavailabilityService.create(unavailability)
        );

        assertEquals(
                "Unavailability overlaps an existing unavailability",
                exception.getMessage()
        );

        verify(unavailabilityRepository, never())
                .save(any());
    }

    @Test
    void shouldGetUnavailabilityById() {
        when(unavailabilityRepository.findById(1L))
                .thenReturn(Optional.of(unavailability));

        Unavailability result =
                unavailabilityService.getById(1L);

        assertSame(unavailability, result);
    }

    @Test
    void shouldThrowWhenUnavailabilityDoesNotExist() {
        when(unavailabilityRepository.findById(1L))
                .thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(
                EntityNotFoundException.class,
                () -> unavailabilityService.getById(1L)
        );

        assertEquals(
                "Unavailability not found: 1",
                exception.getMessage()
        );
    }

    @Test
    void shouldGetAllUnavailabilities() {
        List<Unavailability> unavailabilities =
                List.of(unavailability);

        when(unavailabilityRepository.findAll())
                .thenReturn(unavailabilities);

        List<Unavailability> result =
                unavailabilityService.getAll();

        assertSame(unavailabilities, result);
    }

    @Test
    void shouldDeleteUnavailability() {
        when(unavailabilityRepository.findById(1L))
                .thenReturn(Optional.of(unavailability));

        unavailabilityService.delete(1L);

        verify(unavailabilityRepository)
                .delete(unavailability);
    }

    @Test
    void shouldUpdateUnavailability() {
        when(user.getId()).thenReturn(1L);

        Unavailability existing = new Unavailability(
                user,
                LocalDateTime.of(2026, 8, 20, 9, 0),
                LocalDateTime.of(2026, 8, 20, 12, 0),
                "Old reason"
        );

        when(unavailabilityRepository.findById(1L))
                .thenReturn(Optional.of(existing));

        when(userRepository.existsById(1L))
                .thenReturn(true);

        when(unavailabilityRepository
                .existsByUserIdAndStartDateTimeLessThanAndEndDateTimeGreaterThanAndIdNot(
                        eq(1L),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class),
                        eq(1L)))
                .thenReturn(false);

        when(unavailabilityRepository.save(existing))
                .thenReturn(existing);

        unavailability.setStartDateTime(
                LocalDateTime.of(2026, 8, 20, 10, 0)
        );

        unavailability.setEndDateTime(
                LocalDateTime.of(2026, 8, 20, 18, 0)
        );

        unavailability.setReason("New reason");

        Unavailability result =
                unavailabilityService.update(1L, unavailability);

        assertEquals(
                LocalDateTime.of(2026, 8, 20, 10, 0),
                result.getStartDateTime()
        );

        assertEquals(
                LocalDateTime.of(2026, 8, 20, 18, 0),
                result.getEndDateTime()
        );

        assertEquals(
                "New reason",
                result.getReason()
        );

        assertSame(user, result.getUser());

        verify(unavailabilityRepository)
                .save(existing);
    }

    @Test
    void shouldRejectUpdateWhenOverlappingExistingUnavailability() {
        when(user.getId()).thenReturn(1L);

        Unavailability existing = new Unavailability(
                user,
                LocalDateTime.of(2026, 8, 20, 9, 0),
                LocalDateTime.of(2026, 8, 20, 12, 0),
                "Existing"
        );

        when(unavailabilityRepository.findById(1L))
                .thenReturn(Optional.of(existing));

        when(userRepository.existsById(1L))
                .thenReturn(true);

        when(unavailabilityRepository
                .existsByUserIdAndStartDateTimeLessThanAndEndDateTimeGreaterThanAndIdNot(
                        eq(1L),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class),
                        eq(1L)))
                .thenReturn(true);

        unavailability.setStartDateTime(
                LocalDateTime.of(2026, 8, 20, 10, 0)
        );

        unavailability.setEndDateTime(
                LocalDateTime.of(2026, 8, 20, 18, 0)
        );

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> unavailabilityService.update(1L, unavailability)
        );

        assertEquals(
                "Unavailability overlaps an existing unavailability",
                exception.getMessage()
        );

        verify(unavailabilityRepository, never())
                .save(any());
    }
}
