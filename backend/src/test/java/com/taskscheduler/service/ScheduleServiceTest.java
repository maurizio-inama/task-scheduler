package com.taskscheduler.service;

import com.taskscheduler.domain.entity.Schedule;
import com.taskscheduler.domain.entity.ScheduleStatus;
import com.taskscheduler.domain.repository.ScheduleRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    @InjectMocks
    private ScheduleServiceImpl scheduleService;

    private LocalDateTime start;
    private LocalDateTime end;

    @BeforeEach
    void setUp() {
        start = LocalDateTime.of(2026, 8, 19, 9, 0);
        end = LocalDateTime.of(2026, 8, 19, 17, 0);
    }

    @Test
    void createValidScheduleShouldSave() {
        Schedule schedule = createSchedule(
                start,
                end,
                ScheduleStatus.DRAFT
        );

        when(scheduleRepository.save(schedule))
                .thenReturn(schedule);

        Schedule result = scheduleService.create(schedule);

        assertSame(schedule, result);
        verify(scheduleRepository).save(schedule);
    }

    @Test
    void createNullScheduleShouldThrowValidationException() {
        assertThrows(
                ValidationException.class,
                () -> scheduleService.create(null)
        );

        verifyNoInteractions(scheduleRepository);
    }

    @Test
    void createScheduleWithNullStartShouldThrowValidationException() {
        Schedule schedule = createSchedule(
                null,
                end,
                ScheduleStatus.DRAFT
        );

        assertThrows(
                ValidationException.class,
                () -> scheduleService.create(schedule)
        );

        verifyNoInteractions(scheduleRepository);
    }

    @Test
    void createScheduleWithNullEndShouldThrowValidationException() {
        Schedule schedule = createSchedule(
                start,
                null,
                ScheduleStatus.DRAFT
        );

        assertThrows(
                ValidationException.class,
                () -> scheduleService.create(schedule)
        );

        verifyNoInteractions(scheduleRepository);
    }

    @Test
    void createScheduleWithNullStatusShouldThrowValidationException() {
        Schedule schedule = createSchedule(
                start,
                end,
                null
        );

        assertThrows(
                ValidationException.class,
                () -> scheduleService.create(schedule)
        );

        verifyNoInteractions(scheduleRepository);
    }

    @Test
    void createScheduleWithInvalidIntervalShouldThrowValidationException() {
        Schedule schedule = createSchedule(
                end,
                start,
                ScheduleStatus.DRAFT
        );

        assertThrows(
                ValidationException.class,
                () -> scheduleService.create(schedule)
        );

        verifyNoInteractions(scheduleRepository);
    }

    @Test
    void getByIdShouldReturnSchedule() {
        Schedule schedule = createSchedule(
                start,
                end,
                ScheduleStatus.DRAFT
        );

        when(scheduleRepository.findById(1L))
                .thenReturn(Optional.of(schedule));

        Schedule result = scheduleService.getById(1L);

        assertSame(schedule, result);
    }

    @Test
    void getByIdWhenNotFoundShouldThrowEntityNotFoundException() {
        when(scheduleRepository.findById(1L))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> scheduleService.getById(1L)
        );
    }

    @Test
    void getAllShouldReturnSchedules() {
        Schedule schedule = createSchedule(
                start,
                end,
                ScheduleStatus.DRAFT
        );

        when(scheduleRepository.findAll())
                .thenReturn(List.of(schedule));

        List<Schedule> result = scheduleService.getAll();

        assertEquals(1, result.size());
        assertSame(schedule, result.get(0));
    }

    @Test
    void updateShouldModifySchedule() {
        Schedule existing = createSchedule(
                start,
                end,
                ScheduleStatus.DRAFT
        );

        Schedule update = createSchedule(
                start.plusHours(1),
                end.plusHours(1),
                ScheduleStatus.PUBLISHED
        );

        when(scheduleRepository.findById(1L))
                .thenReturn(Optional.of(existing));

        when(scheduleRepository.save(existing))
                .thenReturn(existing);

        Schedule result = scheduleService.update(1L, update);

        assertSame(existing, result);
        assertEquals(update.getStartDateTime(),
                existing.getStartDateTime());
        assertEquals(update.getEndDateTime(),
                existing.getEndDateTime());
        assertEquals(
                ScheduleStatus.PUBLISHED,
                existing.getStatus()
        );

        verify(scheduleRepository).save(existing);
    }

    @Test
    void updateInvalidStatusTransitionShouldThrowBusinessRuleException() {
        Schedule existing = createSchedule(
                start,
                end,
                ScheduleStatus.DRAFT
        );

        Schedule update = createSchedule(
                start,
                end,
                ScheduleStatus.COMPLETED
        );

        when(scheduleRepository.findById(1L))
                .thenReturn(Optional.of(existing));

        assertThrows(
                BusinessRuleException.class,
                () -> scheduleService.update(1L, update)
        );

        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void deleteShouldDeleteExistingSchedule() {
        Schedule schedule = createSchedule(
                start,
                end,
                ScheduleStatus.DRAFT
        );

        when(scheduleRepository.findById(1L))
                .thenReturn(Optional.of(schedule));

        scheduleService.delete(1L);

        verify(scheduleRepository).delete(schedule);
    }

    @Test
    void deleteMissingScheduleShouldThrowEntityNotFoundException() {
        when(scheduleRepository.findById(1L))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> scheduleService.delete(1L)
        );

        verify(scheduleRepository, never()).delete(any());
    }

    private Schedule createSchedule(
            LocalDateTime start,
            LocalDateTime end,
            ScheduleStatus status
    ) {
        Schedule schedule = new ScheduleTestHelper()
                .create();

        schedule.setStartDateTime(start);
        schedule.setEndDateTime(end);
        schedule.setStatus(status);

        return schedule;
    }

    private static class ScheduleTestHelper {

        Schedule create() {
            try {
                var constructor =
                        Schedule.class.getDeclaredConstructor();

                constructor.setAccessible(true);

                return constructor.newInstance();

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}