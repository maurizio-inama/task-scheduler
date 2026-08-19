package com.taskscheduler.service;

import com.taskscheduler.domain.entity.Availability;
import com.taskscheduler.domain.entity.Unavailability;
import com.taskscheduler.domain.entity.User;
import com.taskscheduler.domain.repository.AvailabilityRepository;
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
class AvailabilityServiceTest {

    @Mock
    private AvailabilityRepository availabilityRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UnavailabilityRepository unavailabilityRepository;

    @InjectMocks
    private AvailabilityServiceImpl availabilityService;

    private User user;
    private Availability availability;

    @BeforeEach
    void setUp() {
        user = mock(User.class);

        availability = new Availability(
                user,
                LocalDateTime.of(2026, 8, 20, 9, 0),
                LocalDateTime.of(2026, 8, 20, 17, 0)
        );
    }

    @Test
    void shouldCreateAvailability() {
        when(user.getId()).thenReturn(1L);

        when(userRepository.existsById(1L)).thenReturn(true);

        when(unavailabilityRepository
                .existsByUserIdAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
                        eq(1L),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class)))
                .thenReturn(false);

        when(availabilityRepository.save(availability))
                .thenReturn(availability);

        Availability result = availabilityService.create(availability);

        assertSame(availability, result);

        verify(availabilityRepository).save(availability);
    }

    @Test
    void shouldRejectNullAvailability() {
        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> availabilityService.create(null)
        );

        assertEquals(
                "Availability must not be null",
                exception.getMessage()
        );

        verifyNoInteractions(userRepository);
        verifyNoInteractions(availabilityRepository);
        verifyNoInteractions(unavailabilityRepository);
    }

    @Test
    void shouldRejectMissingUser() {
        availability.setUser(null);

        assertThrows(
                ValidationException.class,
                () -> availabilityService.create(availability)
        );

        verifyNoInteractions(userRepository);
        verifyNoInteractions(availabilityRepository);
        verifyNoInteractions(unavailabilityRepository);
    }

    @Test
    void shouldRejectInvalidDateRange() {
        availability.setStartDateTime(
                LocalDateTime.of(2026, 8, 20, 17, 0)
        );

        availability.setEndDateTime(
                LocalDateTime.of(2026, 8, 20, 9, 0)
        );

        assertThrows(
                ValidationException.class,
                () -> availabilityService.create(availability)
        );

        verifyNoInteractions(userRepository);
        verifyNoInteractions(availabilityRepository);
        verifyNoInteractions(unavailabilityRepository);
    }

    @Test
    void shouldRejectWhenUserDoesNotExist() {
        when(user.getId()).thenReturn(1L);

        when(userRepository.existsById(1L)).thenReturn(false);

        EntityNotFoundException exception = assertThrows(
                EntityNotFoundException.class,
                () -> availabilityService.create(availability)
        );

        assertEquals(
                "User not found: 1",
                exception.getMessage()
        );

        verify(availabilityRepository, never()).save(any());
        verifyNoInteractions(unavailabilityRepository);
    }

    @Test
    void shouldRejectOverlapWithUnavailability() {
        when(user.getId()).thenReturn(1L);

        when(userRepository.existsById(1L)).thenReturn(true);

        when(unavailabilityRepository
                .existsByUserIdAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
                        eq(1L),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class)))
                .thenReturn(true);

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> availabilityService.create(availability)
        );

        assertEquals(
                "Availability overlaps an existing unavailability",
                exception.getMessage()
        );

        verify(availabilityRepository, never()).save(any());
    }

    @Test
    void shouldGetAvailabilityById() {
        when(availabilityRepository.findById(1L))
                .thenReturn(Optional.of(availability));

        Availability result = availabilityService.getById(1L);

        assertSame(availability, result);
    }

    @Test
    void shouldThrowWhenAvailabilityDoesNotExist() {
        when(availabilityRepository.findById(1L))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> availabilityService.getById(1L)
        );
    }

    @Test
    void shouldGetAllAvailabilities() {
        List<Availability> availabilities = List.of(availability);

        when(availabilityRepository.findAll())
                .thenReturn(availabilities);

        List<Availability> result =
                availabilityService.getAll();

        assertSame(availabilities, result);
    }

    @Test
    void shouldDeleteAvailability() {
        when(availabilityRepository.findById(1L))
                .thenReturn(Optional.of(availability));

        availabilityService.delete(1L);

        verify(availabilityRepository).delete(availability);
    }

    @Test
    void shouldUpdateAvailability() {
        when(user.getId()).thenReturn(1L);

        Availability existing = new Availability(
                user,
                LocalDateTime.of(2026, 8, 20, 9, 0),
                LocalDateTime.of(2026, 8, 20, 12, 0)
        );

        when(availabilityRepository.findById(1L))
                .thenReturn(Optional.of(existing));

        when(userRepository.existsById(1L))
                .thenReturn(true);

        when(unavailabilityRepository
                .existsByUserIdAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
                        eq(1L),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class)))
                .thenReturn(false);

        when(availabilityRepository.save(existing))
                .thenReturn(existing);

        availability.setStartDateTime(
                LocalDateTime.of(2026, 8, 20, 10, 0)
        );

        availability.setEndDateTime(
                LocalDateTime.of(2026, 8, 20, 18, 0)
        );

        Availability result =
                availabilityService.update(1L, availability);

        assertEquals(
                LocalDateTime.of(2026, 8, 20, 10, 0),
                result.getStartDateTime()
        );

        assertEquals(
                LocalDateTime.of(2026, 8, 20, 18, 0),
                result.getEndDateTime()
        );

        verify(availabilityRepository).save(existing);
    }
}