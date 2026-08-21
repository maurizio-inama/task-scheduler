package com.taskscheduler.service;

import com.taskscheduler.domain.entity.Assignment;
import com.taskscheduler.domain.entity.AssignmentStatus;
import com.taskscheduler.domain.entity.Availability;
import com.taskscheduler.domain.entity.Role;
import com.taskscheduler.domain.entity.Schedule;
import com.taskscheduler.domain.entity.ScheduleStatus;
import com.taskscheduler.domain.entity.Task;
import com.taskscheduler.domain.entity.TaskPriority;
import com.taskscheduler.domain.entity.TaskStatus;
import com.taskscheduler.domain.entity.Unavailability;
import com.taskscheduler.domain.entity.User;
import com.taskscheduler.domain.repository.AssignmentRepository;
import com.taskscheduler.domain.repository.AvailabilityRepository;
import com.taskscheduler.domain.repository.ScheduleRepository;
import com.taskscheduler.domain.repository.TaskRepository;
import com.taskscheduler.domain.repository.UnavailabilityRepository;
import com.taskscheduler.domain.repository.UserRepository;
import com.taskscheduler.exception.BusinessRuleException;
import com.taskscheduler.exception.EntityNotFoundException;
import com.taskscheduler.scheduling.SchedulingEngine;
import com.taskscheduler.scheduling.model.Allocation;
import com.taskscheduler.scheduling.model.SchedulingOptions;
import com.taskscheduler.scheduling.model.SchedulingRequest;
import com.taskscheduler.scheduling.model.SchedulingResult;
import com.taskscheduler.scheduling.model.SchedulingTask;
import com.taskscheduler.scheduling.model.SchedulingUser;
import com.taskscheduler.scheduling.model.SchedulingFailureReason;
import com.taskscheduler.scheduling.model.TaskSchedule;
import com.taskscheduler.scheduling.model.UnscheduledTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchedulingServiceTest {

    private static final Long SCHEDULE_ID = 7L;
    private static final LocalDateTime WINDOW_START =
            LocalDateTime.of(2026, 9, 1, 8, 0);
    private static final LocalDateTime WINDOW_END =
            LocalDateTime.of(2026, 9, 5, 18, 0);

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AvailabilityRepository availabilityRepository;

    @Mock
    private UnavailabilityRepository unavailabilityRepository;

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private SchedulingEngine schedulingEngine;

    @InjectMocks
    private SchedulingServiceImpl schedulingService;

    private Schedule schedule;
    private Task task;
    private User user;

    @BeforeEach
    void setUp() {
        schedule = new Schedule();
        schedule.setStartDateTime(WINDOW_START);
        schedule.setEndDateTime(WINDOW_END);
        schedule.setStatus(ScheduleStatus.DRAFT);

        task = new Task(5L);
        task.setStatus(TaskStatus.PENDING);
        task.setPriority(TaskPriority.MEDIUM);
        task.setEstimatedDurationMinutes(120);

        user = new User(10L);
        user.setRole(Role.OPERATOR);
        user.setEnabled(true);
    }

    @Test
    void shouldPersistAssignmentsAndMarkTasksScheduled() {
        stubBaseInputs();
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(schedulingEngine.schedule(any())).thenReturn(new SchedulingResult(
                List.of(new TaskSchedule(
                        5L,
                        10L,
                        List.of(new Allocation(
                                LocalDateTime.of(2026, 9, 1, 9, 0),
                                LocalDateTime.of(2026, 9, 1, 11, 0)
                        ))
                )),
                List.of()
        ));

        schedulingService.generate(SCHEDULE_ID);

        ArgumentCaptor<Assignment> assignmentCaptor =
                ArgumentCaptor.forClass(Assignment.class);
        verify(assignmentRepository).save(assignmentCaptor.capture());

        Assignment saved = assignmentCaptor.getValue();
        assertEquals(10L, saved.getUser().getId());
        assertEquals(5L, saved.getTask().getId());
        assertEquals(schedule, saved.getSchedule());
        assertEquals(LocalDateTime.of(2026, 9, 1, 9, 0), saved.getStartDateTime());
        assertEquals(LocalDateTime.of(2026, 9, 1, 11, 0), saved.getEndDateTime());
        assertEquals(AssignmentStatus.ASSIGNED, saved.getStatus());

        assertEquals(TaskStatus.SCHEDULED, task.getStatus());
        verify(taskRepository).save(task);
    }

    @Test
    void shouldThrowWhenScheduleDoesNotExist() {
        when(scheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> schedulingService.generate(SCHEDULE_ID));
    }

    @Test
    void shouldThrowWhenScheduleIsCancelled() {
        schedule.setStatus(ScheduleStatus.CANCELLED);
        when(scheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));

        assertThrows(BusinessRuleException.class,
                () -> schedulingService.generate(SCHEDULE_ID));
    }

    @Test
    void shouldThrowWhenPendingTaskAlreadyHasAssignments() {
        when(scheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
        when(taskRepository.findByStatus(TaskStatus.PENDING)).thenReturn(List.of(task));

        Assignment existing = new Assignment();
        existing.setUser(user);
        existing.setTask(task);
        existing.setStartDateTime(WINDOW_START);
        existing.setEndDateTime(WINDOW_START.plusHours(1));
        existing.setStatus(AssignmentStatus.ASSIGNED);

        when(assignmentRepository.findByStatusNot(AssignmentStatus.CANCELLED))
                .thenReturn(List.of(existing));

        assertThrows(BusinessRuleException.class,
                () -> schedulingService.generate(SCHEDULE_ID));
    }

    @Test
    void shouldExcludeDisabledUsersFromSchedulingInput() {
        user.setEnabled(false);
        when(scheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
        when(taskRepository.findByStatus(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(assignmentRepository.findByStatusNot(AssignmentStatus.CANCELLED))
                .thenReturn(List.of());
        when(userRepository.findAll()).thenReturn(List.of(user));

        ArgumentCaptor<SchedulingRequest> requestCaptor =
                ArgumentCaptor.forClass(SchedulingRequest.class);
        when(schedulingEngine.schedule(requestCaptor.capture()))
                .thenReturn(new SchedulingResult(List.of(), List.of()));

        schedulingService.generate(SCHEDULE_ID);

        assertTrue(requestCaptor.getValue().users().isEmpty());
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void shouldGroupActiveOverlappingAssignmentsIntoUserInput() {
        when(scheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
        when(taskRepository.findByStatus(TaskStatus.PENDING)).thenReturn(List.of(task));

        Assignment existing = new Assignment();
        existing.setUser(user);
        existing.setTask(new Task(99L));
        existing.setStartDateTime(WINDOW_START);
        existing.setEndDateTime(WINDOW_START.plusHours(2));
        existing.setStatus(AssignmentStatus.ASSIGNED);

        when(assignmentRepository.findByStatusNot(AssignmentStatus.CANCELLED))
                .thenReturn(List.of(existing));
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(availabilityRepository.findByUserId(10L)).thenReturn(List.of());
        when(unavailabilityRepository.findByUserId(10L)).thenReturn(List.of());

        ArgumentCaptor<SchedulingRequest> requestCaptor =
                ArgumentCaptor.forClass(SchedulingRequest.class);
        when(schedulingEngine.schedule(requestCaptor.capture()))
                .thenReturn(new SchedulingResult(List.of(), List.of()));

        schedulingService.generate(SCHEDULE_ID);

        SchedulingUser schedulingUser = requestCaptor.getValue().users().get(0);
        assertEquals(1, schedulingUser.existingAssignments().size());
        assertEquals(WINDOW_START, schedulingUser.existingAssignments().get(0).start());
    }

    @Test
    void shouldNotPersistAnythingForUnscheduledTasks() {
        stubBaseInputs();
        when(schedulingEngine.schedule(any())).thenReturn(new SchedulingResult(
                List.of(),
                List.of(new UnscheduledTask(
                        5L,
                        SchedulingFailureReason.INSUFFICIENT_CAPACITY,
                        "not enough capacity"
                ))
        ));

        schedulingService.generate(SCHEDULE_ID);

        verify(assignmentRepository, never()).save(any());
        assertEquals(TaskStatus.PENDING, task.getStatus());
        verify(taskRepository, never()).save(any());
    }

    @Test
    void shouldMapDomainDataIntoSchedulingRequest() {
        when(scheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
        when(taskRepository.findByStatus(TaskStatus.PENDING)).thenReturn(List.of(task));

        task.setDeadline(LocalDateTime.of(2026, 9, 3, 17, 0));

        when(assignmentRepository.findByStatusNot(AssignmentStatus.CANCELLED))
                .thenReturn(List.of());
        when(userRepository.findAll()).thenReturn(List.of(user));

        Availability availability = new Availability(
                user,
                LocalDateTime.of(2026, 9, 1, 9, 0),
                LocalDateTime.of(2026, 9, 1, 17, 0)
        );
        when(availabilityRepository.findByUserId(10L)).thenReturn(List.of(availability));

        Unavailability unavailability = new Unavailability(
                user,
                LocalDateTime.of(2026, 9, 2, 9, 0),
                LocalDateTime.of(2026, 9, 2, 13, 0),
                "appointment"
        );
        when(unavailabilityRepository.findByUserId(10L)).thenReturn(List.of(unavailability));

        ArgumentCaptor<SchedulingRequest> requestCaptor =
                ArgumentCaptor.forClass(SchedulingRequest.class);
        when(schedulingEngine.schedule(requestCaptor.capture()))
                .thenReturn(new SchedulingResult(List.of(), List.of()));

        schedulingService.generate(SCHEDULE_ID);

        SchedulingRequest request = requestCaptor.getValue();
        assertEquals(WINDOW_START, request.windowStart());
        assertEquals(WINDOW_END, request.windowEnd());
        assertEquals(SchedulingOptions.defaults(), request.options());

        SchedulingTask schedulingTask = request.tasks().get(0);
        assertEquals(5L, schedulingTask.taskId());
        assertEquals(TaskPriority.MEDIUM, schedulingTask.priority());
        assertEquals(120, schedulingTask.estimatedDurationMinutes());
        assertEquals(LocalDateTime.of(2026, 9, 3, 17, 0), schedulingTask.deadline());

        SchedulingUser schedulingUser = request.users().get(0);
        assertEquals(10L, schedulingUser.userId());
        assertEquals(Role.OPERATOR, schedulingUser.role());
        assertEquals(1, schedulingUser.availabilities().size());
        assertEquals(1, schedulingUser.unavailabilities().size());
        assertTrue(schedulingUser.existingAssignments().isEmpty());
    }

    private void stubBaseInputs() {
        when(scheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
        when(taskRepository.findByStatus(TaskStatus.PENDING)).thenReturn(List.of(task));
        when(assignmentRepository.findByStatusNot(AssignmentStatus.CANCELLED))
                .thenReturn(List.of());
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(availabilityRepository.findByUserId(10L)).thenReturn(List.of());
        when(unavailabilityRepository.findByUserId(10L)).thenReturn(List.of());
    }
}
