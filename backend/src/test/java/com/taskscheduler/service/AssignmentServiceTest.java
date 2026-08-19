package com.taskscheduler.service;

import com.taskscheduler.domain.entity.Assignment;
import com.taskscheduler.domain.entity.AssignmentStatus;
import com.taskscheduler.domain.entity.Schedule;
import com.taskscheduler.domain.entity.ScheduleStatus;
import com.taskscheduler.domain.entity.Task;
import com.taskscheduler.domain.entity.TaskStatus;
import com.taskscheduler.domain.entity.User;
import com.taskscheduler.domain.repository.AssignmentRepository;
import com.taskscheduler.domain.repository.AvailabilityRepository;
import com.taskscheduler.domain.repository.ScheduleRepository;
import com.taskscheduler.domain.repository.TaskRepository;
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
class AssignmentServiceTest {

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private AvailabilityRepository availabilityRepository;

    @Mock
    private UnavailabilityRepository unavailabilityRepository;

    @InjectMocks
    private AssignmentServiceImpl assignmentService;

    private User user;
    private Task task;
    private Schedule schedule;

    private LocalDateTime scheduleStart;
    private LocalDateTime scheduleEnd;
    private LocalDateTime start;
    private LocalDateTime end;

    @BeforeEach
    void setUp() {
        user = mock(User.class);
        task = mock(Task.class);
        schedule = mock(Schedule.class);

        scheduleStart = LocalDateTime.of(2026, 8, 19, 8, 0);
        scheduleEnd = LocalDateTime.of(2026, 8, 19, 18, 0);
        start = LocalDateTime.of(2026, 8, 19, 10, 0);
        end = LocalDateTime.of(2026, 8, 19, 12, 0);
    }

    private void stubValidUser() {
        when(user.getId()).thenReturn(1L);
        when(user.isEnabled()).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    }

    private void stubValidTask() {
        when(task.getId()).thenReturn(2L);
        when(task.getStatus()).thenReturn(TaskStatus.PENDING);
        when(taskRepository.findById(2L)).thenReturn(Optional.of(task));
    }

    private void stubValidSchedule() {
        when(schedule.getId()).thenReturn(3L);
        when(schedule.getStatus()).thenReturn(ScheduleStatus.DRAFT);
        when(schedule.getStartDateTime()).thenReturn(scheduleStart);
        when(schedule.getEndDateTime()).thenReturn(scheduleEnd);
        when(scheduleRepository.findById(3L)).thenReturn(Optional.of(schedule));
    }

    private void stubAvailabilityCovers() {
        when(availabilityRepository
                .existsByUserIdAndStartDateTimeLessThanEqualAndEndDateTimeGreaterThanEqual(
                        eq(1L),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class)))
                .thenReturn(true);
    }

    private void stubNoUnavailabilityOverlap() {
        when(unavailabilityRepository
                .existsByUserIdAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
                        eq(1L),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class)))
                .thenReturn(false);
    }

    private void stubNoAssignmentConflict() {
        when(assignmentRepository
                .existsByUserIdAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
                        eq(1L),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class)))
                .thenReturn(false);
    }

    private void stubValidReferences() {
        stubValidUser();
        stubValidTask();
        stubValidSchedule();
        stubAvailabilityCovers();
        stubNoUnavailabilityOverlap();
        stubNoAssignmentConflict();
    }

    private Assignment createAssignment(
            LocalDateTime assignmentStart,
            LocalDateTime assignmentEnd,
            AssignmentStatus status
    ) {
        Assignment assignment = new AssignmentTestHelper()
                .create();

        assignment.setUser(user);
        assignment.setTask(task);
        assignment.setSchedule(schedule);
        assignment.setStartDateTime(assignmentStart);
        assignment.setEndDateTime(assignmentEnd);
        assignment.setStatus(status);

        return assignment;
    }

    // ---------------------------------------------------------------
    // VALID CREATION
    // ---------------------------------------------------------------

    @Test
    void shouldCreateValidAssignment() {
        Assignment assignment = createAssignment(
                start,
                end,
                AssignmentStatus.ASSIGNED
        );

        stubValidReferences();

        when(assignmentRepository.save(assignment))
                .thenReturn(assignment);

        Assignment result = assignmentService.create(assignment);

        assertSame(assignment, result);
        verify(assignmentRepository).save(assignment);
    }

    // ---------------------------------------------------------------
    // VALIDATION
    // ---------------------------------------------------------------

    @Test
    void shouldRejectNullAssignment() {
        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> assignmentService.create(null)
        );

        assertEquals(
                "Assignment must not be null",
                exception.getMessage()
        );

        verifyNoInteractions(assignmentRepository);
        verifyNoInteractions(userRepository);
        verifyNoInteractions(taskRepository);
        verifyNoInteractions(scheduleRepository);
    }

    @Test
    void shouldRejectNullUser() {
        Assignment assignment = createAssignment(
                start,
                end,
                AssignmentStatus.ASSIGNED
        );
        assignment.setUser(null);

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> assignmentService.create(assignment)
        );

        assertEquals("User must not be null", exception.getMessage());
        verifyNoInteractions(assignmentRepository);
        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldRejectNullTask() {
        Assignment assignment = createAssignment(
                start,
                end,
                AssignmentStatus.ASSIGNED
        );
        assignment.setTask(null);

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> assignmentService.create(assignment)
        );

        assertEquals("Task must not be null", exception.getMessage());
        verifyNoInteractions(assignmentRepository);
        verifyNoInteractions(taskRepository);
    }

    @Test
    void shouldRejectNullSchedule() {
        Assignment assignment = createAssignment(
                start,
                end,
                AssignmentStatus.ASSIGNED
        );
        assignment.setSchedule(null);

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> assignmentService.create(assignment)
        );

        assertEquals("Schedule must not be null", exception.getMessage());
        verifyNoInteractions(assignmentRepository);
        verifyNoInteractions(scheduleRepository);
    }

    @Test
    void shouldRejectNullStartDateTime() {
        Assignment assignment = createAssignment(
                start,
                end,
                AssignmentStatus.ASSIGNED
        );
        assignment.setStartDateTime(null);

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> assignmentService.create(assignment)
        );

        assertEquals(
                "Start date/time must not be null",
                exception.getMessage()
        );

        verifyNoInteractions(assignmentRepository);
        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldRejectNullEndDateTime() {
        Assignment assignment = createAssignment(
                start,
                end,
                AssignmentStatus.ASSIGNED
        );
        assignment.setEndDateTime(null);

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> assignmentService.create(assignment)
        );

        assertEquals(
                "End date/time must not be null",
                exception.getMessage()
        );

        verifyNoInteractions(assignmentRepository);
        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldRejectStartAtOrAfterEnd() {
        Assignment assignment = createAssignment(
                end,
                end,
                AssignmentStatus.ASSIGNED
        );

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> assignmentService.create(assignment)
        );

        assertEquals(
                "Start date/time must be before end date/time",
                exception.getMessage()
        );

        verifyNoInteractions(assignmentRepository);
        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldRejectNullStatus() {
        Assignment assignment = createAssignment(
                start,
                end,
                null
        );

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> assignmentService.create(assignment)
        );

        assertEquals("Status must not be null", exception.getMessage());

        verifyNoInteractions(assignmentRepository);
        verifyNoInteractions(userRepository);
    }

    // ---------------------------------------------------------------
    // USER
    // ---------------------------------------------------------------

    @Test
    void shouldRejectUserNotFound() {
        Assignment assignment = createAssignment(
                start,
                end,
                AssignmentStatus.ASSIGNED
        );

        when(user.getId()).thenReturn(1L);
        when(userRepository.findById(1L))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> assignmentService.create(assignment)
        );

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectDisabledUser() {
        Assignment assignment = createAssignment(
                start,
                end,
                AssignmentStatus.ASSIGNED
        );

        when(user.getId()).thenReturn(1L);
        when(user.isEnabled()).thenReturn(false);

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        assertThrows(
                BusinessRuleException.class,
                () -> assignmentService.create(assignment)
        );

        verify(assignmentRepository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // TASK
    // ---------------------------------------------------------------

    @Test
    void shouldRejectTaskNotFound() {
        Assignment assignment = createAssignment(
                start,
                end,
                AssignmentStatus.ASSIGNED
        );

        stubValidUser();

        when(task.getId()).thenReturn(2L);
        when(taskRepository.findById(2L)).thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> assignmentService.create(assignment)
        );

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectCompletedTask() {
        Assignment assignment = createAssignment(
                start,
                end,
                AssignmentStatus.ASSIGNED
        );

        stubValidUser();

        when(task.getId()).thenReturn(2L);
        when(task.getStatus()).thenReturn(TaskStatus.COMPLETED);
        when(taskRepository.findById(2L)).thenReturn(Optional.of(task));

        assertThrows(
                BusinessRuleException.class,
                () -> assignmentService.create(assignment)
        );

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectCancelledTask() {
        Assignment assignment = createAssignment(
                start,
                end,
                AssignmentStatus.ASSIGNED
        );

        stubValidUser();

        when(task.getId()).thenReturn(2L);
        when(task.getStatus()).thenReturn(TaskStatus.CANCELLED);
        when(taskRepository.findById(2L)).thenReturn(Optional.of(task));

        assertThrows(
                BusinessRuleException.class,
                () -> assignmentService.create(assignment)
        );

        verify(assignmentRepository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // SCHEDULE
    // ---------------------------------------------------------------

    @Test
    void shouldRejectScheduleNotFound() {
        Assignment assignment = createAssignment(
                start,
                end,
                AssignmentStatus.ASSIGNED
        );

        stubValidUser();
        stubValidTask();

        when(schedule.getId()).thenReturn(3L);
        when(scheduleRepository.findById(3L)).thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> assignmentService.create(assignment)
        );

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectAssignmentOutsideSchedule() {
        Assignment assignment = createAssignment(
                LocalDateTime.of(2026, 8, 19, 7, 0),
                LocalDateTime.of(2026, 8, 19, 9, 0),
                AssignmentStatus.ASSIGNED
        );

        stubValidUser();
        stubValidTask();

        when(schedule.getId()).thenReturn(3L);
        when(schedule.getStatus()).thenReturn(ScheduleStatus.DRAFT);
        when(schedule.getStartDateTime()).thenReturn(scheduleStart);
        when(scheduleRepository.findById(3L)).thenReturn(Optional.of(schedule));

        assertThrows(
                BusinessRuleException.class,
                () -> assignmentService.create(assignment)
        );

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectAssignmentCrossingScheduleStart() {
        Assignment assignment = createAssignment(
                LocalDateTime.of(2026, 8, 19, 7, 30),
                LocalDateTime.of(2026, 8, 19, 10, 0),
                AssignmentStatus.ASSIGNED
        );

        stubValidUser();
        stubValidTask();

        when(schedule.getId()).thenReturn(3L);
        when(schedule.getStatus()).thenReturn(ScheduleStatus.DRAFT);
        when(schedule.getStartDateTime()).thenReturn(scheduleStart);
        when(scheduleRepository.findById(3L)).thenReturn(Optional.of(schedule));

        assertThrows(
                BusinessRuleException.class,
                () -> assignmentService.create(assignment)
        );

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectAssignmentCrossingScheduleEnd() {
        Assignment assignment = createAssignment(
                LocalDateTime.of(2026, 8, 19, 16, 0),
                LocalDateTime.of(2026, 8, 19, 19, 0),
                AssignmentStatus.ASSIGNED
        );

        stubValidUser();
        stubValidTask();
        stubValidSchedule();

        assertThrows(
                BusinessRuleException.class,
                () -> assignmentService.create(assignment)
        );

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectCancelledSchedule() {
        Assignment assignment = createAssignment(
                start,
                end,
                AssignmentStatus.ASSIGNED
        );

        stubValidUser();
        stubValidTask();

        when(schedule.getId()).thenReturn(3L);
        when(schedule.getStatus()).thenReturn(ScheduleStatus.CANCELLED);
        when(scheduleRepository.findById(3L)).thenReturn(Optional.of(schedule));

        assertThrows(
                BusinessRuleException.class,
                () -> assignmentService.create(assignment)
        );

        verify(assignmentRepository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // AVAILABILITY
    // ---------------------------------------------------------------

    @Test
    void shouldRejectNoAvailability() {
        Assignment assignment = createAssignment(
                start,
                end,
                AssignmentStatus.ASSIGNED
        );

        stubValidUser();
        stubValidTask();
        stubValidSchedule();

        when(availabilityRepository
                .existsByUserIdAndStartDateTimeLessThanEqualAndEndDateTimeGreaterThanEqual(
                        eq(1L),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class)))
                .thenReturn(false);

        assertThrows(
                BusinessRuleException.class,
                () -> assignmentService.create(assignment)
        );

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectPartialAvailabilityCoverage() {
        Assignment assignment = createAssignment(
                start,
                end,
                AssignmentStatus.ASSIGNED
        );

        stubValidUser();
        stubValidTask();
        stubValidSchedule();

        when(availabilityRepository
                .existsByUserIdAndStartDateTimeLessThanEqualAndEndDateTimeGreaterThanEqual(
                        eq(1L),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class)))
                .thenReturn(false);

        assertThrows(
                BusinessRuleException.class,
                () -> assignmentService.create(assignment)
        );

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void shouldAcceptBoundaryCoveredAvailability() {
        Assignment assignment = createAssignment(
                start,
                end,
                AssignmentStatus.ASSIGNED
        );

        stubValidReferences();

        when(assignmentRepository.save(assignment))
                .thenReturn(assignment);

        Assignment result = assignmentService.create(assignment);

        assertSame(assignment, result);
        verify(assignmentRepository).save(assignment);
    }

    // ---------------------------------------------------------------
    // UNAVAILABILITY
    // ---------------------------------------------------------------

    @Test
    void shouldRejectUnavailabilityConflictInMiddle() {
        Assignment assignment = createAssignment(
                start,
                end,
                AssignmentStatus.ASSIGNED
        );

        stubValidUser();
        stubValidTask();
        stubValidSchedule();
        stubAvailabilityCovers();

        when(unavailabilityRepository
                .existsByUserIdAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
                        eq(1L),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class)))
                .thenReturn(true);

        assertThrows(
                BusinessRuleException.class,
                () -> assignmentService.create(assignment)
        );

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectUnavailabilityConflictAtStart() {
        Assignment assignment = createAssignment(
                start,
                end,
                AssignmentStatus.ASSIGNED
        );

        stubValidUser();
        stubValidTask();
        stubValidSchedule();
        stubAvailabilityCovers();

        when(unavailabilityRepository
                .existsByUserIdAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
                        eq(1L),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class)))
                .thenReturn(true);

        assertThrows(
                BusinessRuleException.class,
                () -> assignmentService.create(assignment)
        );

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectUnavailabilityConflictAtEnd() {
        Assignment assignment = createAssignment(
                start,
                end,
                AssignmentStatus.ASSIGNED
        );

        stubValidUser();
        stubValidTask();
        stubValidSchedule();
        stubAvailabilityCovers();

        when(unavailabilityRepository
                .existsByUserIdAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
                        eq(1L),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class)))
                .thenReturn(true);

        assertThrows(
                BusinessRuleException.class,
                () -> assignmentService.create(assignment)
        );

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectUnavailabilityContainingAssignment() {
        Assignment assignment = createAssignment(
                start,
                end,
                AssignmentStatus.ASSIGNED
        );

        stubValidUser();
        stubValidTask();
        stubValidSchedule();
        stubAvailabilityCovers();

        when(unavailabilityRepository
                .existsByUserIdAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
                        eq(1L),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class)))
                .thenReturn(true);

        assertThrows(
                BusinessRuleException.class,
                () -> assignmentService.create(assignment)
        );

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void shouldAcceptBoundaryTouchingUnavailability() {
        Assignment assignment = createAssignment(
                start,
                end,
                AssignmentStatus.ASSIGNED
        );

        stubValidReferences();

        when(assignmentRepository.save(assignment))
                .thenReturn(assignment);

        Assignment result = assignmentService.create(assignment);

        assertSame(assignment, result);
        verify(assignmentRepository).save(assignment);
    }

    // ---------------------------------------------------------------
    // ASSIGNMENT CONFLICT
    // ---------------------------------------------------------------

    @Test
    void shouldAcceptNoAssignmentConflict() {
        Assignment assignment = createAssignment(
                start,
                end,
                AssignmentStatus.ASSIGNED
        );

        stubValidReferences();

        when(assignmentRepository.save(assignment))
                .thenReturn(assignment);

        Assignment result = assignmentService.create(assignment);

        assertSame(assignment, result);
        verify(assignmentRepository).save(assignment);
    }

    @Test
    void shouldRejectOverlappingExistingAssignment() {
        Assignment assignment = createAssignment(
                start,
                end,
                AssignmentStatus.ASSIGNED
        );

        stubValidUser();
        stubValidTask();
        stubValidSchedule();
        stubAvailabilityCovers();
        stubNoUnavailabilityOverlap();

        when(assignmentRepository
                .existsByUserIdAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
                        eq(1L),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class)))
                .thenReturn(true);

        assertThrows(
                BusinessRuleException.class,
                () -> assignmentService.create(assignment)
        );

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectExistingAssignmentContainingNew() {
        Assignment assignment = createAssignment(
                start,
                end,
                AssignmentStatus.ASSIGNED
        );

        stubValidUser();
        stubValidTask();
        stubValidSchedule();
        stubAvailabilityCovers();
        stubNoUnavailabilityOverlap();

        when(assignmentRepository
                .existsByUserIdAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
                        eq(1L),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class)))
                .thenReturn(true);

        assertThrows(
                BusinessRuleException.class,
                () -> assignmentService.create(assignment)
        );

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectNewAssignmentContainingExisting() {
        Assignment assignment = createAssignment(
                start,
                end,
                AssignmentStatus.ASSIGNED
        );

        stubValidUser();
        stubValidTask();
        stubValidSchedule();
        stubAvailabilityCovers();
        stubNoUnavailabilityOverlap();

        when(assignmentRepository
                .existsByUserIdAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
                        eq(1L),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class)))
                .thenReturn(true);

        assertThrows(
                BusinessRuleException.class,
                () -> assignmentService.create(assignment)
        );

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void shouldAcceptBoundaryTouchingAssignments() {
        Assignment assignment = createAssignment(
                start,
                end,
                AssignmentStatus.ASSIGNED
        );

        stubValidReferences();

        when(assignmentRepository.save(assignment))
                .thenReturn(assignment);

        Assignment result = assignmentService.create(assignment);

        assertSame(assignment, result);
        verify(assignmentRepository).save(assignment);
    }

    // ---------------------------------------------------------------
    // UPDATE
    // ---------------------------------------------------------------

    @Test
    void shouldUpdateAssignment() {
        Assignment existing = createAssignment(
                start,
                end,
                AssignmentStatus.ASSIGNED
        );

        Assignment updated = createAssignment(
                LocalDateTime.of(2026, 8, 19, 13, 0),
                LocalDateTime.of(2026, 8, 19, 14, 0),
                AssignmentStatus.IN_PROGRESS
        );

        stubValidUser();
        stubValidTask();
        stubValidSchedule();
        stubAvailabilityCovers();
        stubNoUnavailabilityOverlap();

        when(assignmentRepository.findById(1L))
                .thenReturn(Optional.of(existing));
        when(assignmentRepository
                .existsByUserIdAndStartDateTimeLessThanAndEndDateTimeGreaterThanAndIdNot(
                        eq(1L),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class),
                        eq(1L)))
                .thenReturn(false);
        when(assignmentRepository.save(existing))
                .thenReturn(existing);

        Assignment result = assignmentService.update(1L, updated);

        assertSame(existing, result);
        verify(assignmentRepository).save(existing);
    }

    @Test
    void shouldNotConflictWithItselfOnUpdate() {
        Assignment existing = createAssignment(
                start,
                end,
                AssignmentStatus.ASSIGNED
        );

        stubValidUser();
        stubValidTask();
        stubValidSchedule();
        stubAvailabilityCovers();
        stubNoUnavailabilityOverlap();

        when(assignmentRepository.findById(1L))
                .thenReturn(Optional.of(existing));
        when(assignmentRepository
                .existsByUserIdAndStartDateTimeLessThanAndEndDateTimeGreaterThanAndIdNot(
                        eq(1L),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class),
                        eq(1L)))
                .thenReturn(false);
        when(assignmentRepository.save(existing))
                .thenReturn(existing);

        Assignment result = assignmentService.update(1L, existing);

        assertSame(existing, result);
        verify(assignmentRepository).save(existing);
    }

    @Test
    void shouldRejectUpdatingMissingAssignment() {
        Assignment updated = createAssignment(
                start,
                end,
                AssignmentStatus.IN_PROGRESS
        );

        when(assignmentRepository.findById(1L))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> assignmentService.update(1L, updated)
        );

        verify(assignmentRepository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // GET
    // ---------------------------------------------------------------

    @Test
    void shouldReturnAssignmentById() {
        Assignment assignment = createAssignment(
                start,
                end,
                AssignmentStatus.ASSIGNED
        );

        when(assignmentRepository.findById(1L))
                .thenReturn(Optional.of(assignment));

        Assignment result = assignmentService.getById(1L);

        assertSame(assignment, result);
    }

    @Test
    void shouldRejectMissingAssignmentOnGetById() {
        when(assignmentRepository.findById(1L))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> assignmentService.getById(1L)
        );

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void shouldReturnAllAssignments() {
        Assignment first = createAssignment(
                start,
                end,
                AssignmentStatus.ASSIGNED
        );

        Assignment second = createAssignment(
                LocalDateTime.of(2026, 8, 19, 13, 0),
                LocalDateTime.of(2026, 8, 19, 14, 0),
                AssignmentStatus.IN_PROGRESS
        );

        when(assignmentRepository.findAll())
                .thenReturn(List.of(first, second));

        List<Assignment> result = assignmentService.getAll();

        assertEquals(2, result.size());
        assertTrue(result.contains(first));
        assertTrue(result.contains(second));
    }

    // ---------------------------------------------------------------
    // DELETE
    // ---------------------------------------------------------------

    @Test
    void shouldDeleteAssignment() {
        Assignment assignment = createAssignment(
                start,
                end,
                AssignmentStatus.ASSIGNED
        );

        when(assignmentRepository.findById(1L))
                .thenReturn(Optional.of(assignment));

        assignmentService.delete(1L);

        verify(assignmentRepository).delete(assignment);
    }

    @Test
    void shouldRejectDeletingMissingAssignment() {
        when(assignmentRepository.findById(1L))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> assignmentService.delete(1L)
        );

        verify(assignmentRepository, never()).delete(any());
    }

    private static class AssignmentTestHelper {

        Assignment create() {
            try {
                var constructor =
                        Assignment.class.getDeclaredConstructor();

                constructor.setAccessible(true);

                return constructor.newInstance();

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}