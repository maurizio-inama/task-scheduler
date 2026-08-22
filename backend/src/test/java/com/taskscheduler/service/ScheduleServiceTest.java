package com.taskscheduler.service;

import com.taskscheduler.domain.entity.Assignment;
import com.taskscheduler.domain.entity.AssignmentStatus;
import com.taskscheduler.domain.entity.Schedule;
import com.taskscheduler.domain.entity.ScheduleStatus;
import com.taskscheduler.domain.entity.Task;
import com.taskscheduler.domain.entity.TaskPriority;
import com.taskscheduler.domain.entity.TaskStatus;
import com.taskscheduler.domain.repository.AssignmentRepository;
import com.taskscheduler.domain.repository.ScheduleRepository;
import com.taskscheduler.domain.repository.TaskRepository;
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

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private TaskRepository taskRepository;

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
    void deleteWithoutAssignmentsShouldOnlyRemoveSchedule() {
        Schedule schedule = createSchedule(
                start,
                end,
                ScheduleStatus.DRAFT
        );

        when(scheduleRepository.findById(1L))
                .thenReturn(Optional.of(schedule));
        when(assignmentRepository.findByScheduleId(1L))
                .thenReturn(List.of());

        scheduleService.delete(1L);

        verify(taskRepository, never()).save(any());
        verify(assignmentRepository).deleteAll(List.of());
        verify(scheduleRepository).delete(schedule);
    }

    @Test
    void deleteShouldReleaseAssignedTasksBackToPending() {
        Schedule schedule = createSchedule(
                start,
                end,
                ScheduleStatus.DRAFT
        );
        Task scheduled = scheduledTask("Assigned task");
        Task alsoScheduled = scheduledTask("Second assigned task");
        Assignment first = assignmentFor(schedule, scheduled);
        Assignment second = assignmentFor(schedule, alsoScheduled);

        when(scheduleRepository.findById(1L))
                .thenReturn(Optional.of(schedule));
        when(assignmentRepository.findByScheduleId(1L))
                .thenReturn(List.of(first, second));

        scheduleService.delete(1L);

        assertEquals(TaskStatus.PENDING, scheduled.getStatus());
        assertEquals(TaskStatus.PENDING, alsoScheduled.getStatus());
        verify(taskRepository).save(scheduled);
        verify(taskRepository).save(alsoScheduled);
        verify(assignmentRepository)
                .deleteAll(List.of(first, second));
        verify(scheduleRepository).delete(schedule);
    }

    @Test
    void deleteShouldNotModifyTasksOrAssignmentsOfOtherSchedules() {
        Schedule schedule = createSchedule(
                start,
                end,
                ScheduleStatus.DRAFT
        );
        Task ownTask = scheduledTask("Own task");
        Task foreignTask = scheduledTask("Other schedule's task");
        Assignment ownAssignment = assignmentFor(schedule, ownTask);

        when(scheduleRepository.findById(1L))
                .thenReturn(Optional.of(schedule));
        // the repository only returns assignments of THIS schedule
        when(assignmentRepository.findByScheduleId(1L))
                .thenReturn(List.of(ownAssignment));

        scheduleService.delete(1L);

        assertEquals(TaskStatus.SCHEDULED, foreignTask.getStatus());
        verify(taskRepository).save(ownTask);
        verify(taskRepository, never()).save(foreignTask);
        verify(assignmentRepository).deleteAll(List.of(ownAssignment));
    }

    @Test
    void deleteShouldKeepManuallyAdvancedTaskStatuses() {
        Schedule schedule = createSchedule(
                start,
                end,
                ScheduleStatus.DRAFT
        );
        Task inProgress = new Task(
                "Started task",
                null,
                TaskStatus.IN_PROGRESS,
                TaskPriority.HIGH,
                60,
                LocalDateTime.of(2026, 8, 30, 17, 0)
        );
        Assignment assignment = assignmentFor(schedule, inProgress);

        when(scheduleRepository.findById(1L))
                .thenReturn(Optional.of(schedule));
        when(assignmentRepository.findByScheduleId(1L))
                .thenReturn(List.of(assignment));

        scheduleService.delete(1L);

        assertEquals(TaskStatus.IN_PROGRESS, inProgress.getStatus());
        verify(taskRepository, never()).save(any());
        verify(assignmentRepository).deleteAll(List.of(assignment));
        verify(scheduleRepository).delete(schedule);
    }

    @Test
    void deleteFailureShouldLeaveNoPartialChanges() {
        Schedule schedule = createSchedule(
                start,
                end,
                ScheduleStatus.DRAFT
        );
        Task scheduled = scheduledTask("Assigned task");
        Assignment assignment = assignmentFor(schedule, scheduled);

        when(scheduleRepository.findById(1L))
                .thenReturn(Optional.of(schedule));
        when(assignmentRepository.findByScheduleId(1L))
                .thenReturn(List.of(assignment));
        when(taskRepository.save(any(Task.class)))
                .thenThrow(new IllegalStateException("simulated failure"));

        assertThrows(
                IllegalStateException.class,
                () -> scheduleService.delete(1L)
        );

        verify(assignmentRepository, never()).deleteAll(any());
        verify(scheduleRepository, never()).delete(any());
    }

    private Task scheduledTask(String title) {
        return new Task(
                title,
                null,
                TaskStatus.SCHEDULED,
                TaskPriority.MEDIUM,
                60,
                LocalDateTime.of(2026, 8, 30, 17, 0)
        );
    }

    private Assignment assignmentFor(Schedule schedule, Task task) {
        Assignment assignment = new Assignment();
        assignment.setUser(new com.taskscheduler.domain.entity.User(1L));
        assignment.setTask(task);
        assignment.setSchedule(schedule);
        assignment.setStartDateTime(start);
        assignment.setEndDateTime(start.plusHours(1));
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        return assignment;
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