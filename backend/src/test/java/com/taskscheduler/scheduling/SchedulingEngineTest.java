package com.taskscheduler.scheduling;

import com.taskscheduler.domain.entity.Role;
import com.taskscheduler.domain.entity.TaskPriority;
import com.taskscheduler.exception.ValidationException;
import com.taskscheduler.scheduling.model.Allocation;
import com.taskscheduler.scheduling.model.SchedulingFailureReason;
import com.taskscheduler.scheduling.model.SchedulingOptions;
import com.taskscheduler.scheduling.model.SchedulingRequest;
import com.taskscheduler.scheduling.model.SchedulingResult;
import com.taskscheduler.scheduling.model.SchedulingTask;
import com.taskscheduler.scheduling.model.SchedulingUser;
import com.taskscheduler.scheduling.model.TaskSchedule;
import com.taskscheduler.scheduling.model.TimeInterval;
import com.taskscheduler.scheduling.model.UnscheduledTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulingEngineTest {

    private static final int DAY1 = 1;
    private static final int DAY2 = 2;
    private static final int DAY3 = 3;

    private static final LocalDateTime WINDOW_START =
            LocalDateTime.of(2026, 9, DAY1, 8, 0);
    private static final LocalDateTime WINDOW_END =
            LocalDateTime.of(2026, 9, 5, 18, 0);

    private SchedulingEngine engine;

    @BeforeEach
    void setUp() {
        engine = new SchedulingEngineImpl();
    }

    private LocalDateTime at(int day, int hour, int minute) {
        return LocalDateTime.of(2026, 9, day, hour, minute);
    }

    private TimeInterval ti(int day, int startHour, int startMinute, int endHour, int endMinute) {
        return new TimeInterval(at(day, startHour, startMinute), at(day, endHour, endMinute));
    }

    private SchedulingTask task(long id, TaskPriority priority, int minutes) {
        return new SchedulingTask(id, priority, minutes, null);
    }

    private SchedulingTask task(
            long id,
            TaskPriority priority,
            int minutes,
            LocalDateTime deadline
    ) {
        return new SchedulingTask(id, priority, minutes, deadline);
    }

    private SchedulingUser user(long id, Role role, TimeInterval... availability) {
        return new SchedulingUser(id, role, List.of(availability), List.of(), List.of());
    }

    private SchedulingUser user(
            long id,
            Role role,
            List<TimeInterval> availability,
            List<TimeInterval> unavailability,
            List<TimeInterval> existingAssignments
    ) {
        return new SchedulingUser(id, role, availability, unavailability, existingAssignments);
    }

    private SchedulingRequest request(List<SchedulingTask> tasks, List<SchedulingUser> users) {
        return new SchedulingRequest(
                WINDOW_START,
                WINDOW_END,
                tasks,
                users,
                SchedulingOptions.defaults()
        );
    }

    private SchedulingResult schedule(SchedulingRequest request) {
        return engine.schedule(request);
    }

    @Test
    void shouldScheduleOneTaskForOneAvailableUser() {
        SchedulingResult result = schedule(request(
                List.of(task(1, TaskPriority.MEDIUM, 60)),
                List.of(user(10, Role.OPERATOR, ti(DAY1, 9, 0, 17, 0)))
        ));

        assertEquals(1, result.scheduledTasks().size());
        assertTrue(result.unscheduledTasks().isEmpty());

        TaskSchedule schedule = result.scheduledTasks().get(0);
        assertEquals(1L, schedule.taskId());
        assertEquals(10L, schedule.userId());
        assertEquals(60, schedule.totalMinutes());
        assertEquals(1, schedule.allocations().size());
        assertEquals(at(DAY1, 9, 0), schedule.allocations().get(0).startDateTime());
        assertEquals(at(DAY1, 10, 0), schedule.allocations().get(0).endDateTime());
    }

    @Test
    void shouldAllocateMultipleHoursOnTheSameDay() {
        SchedulingResult result = schedule(request(
                List.of(task(1, TaskPriority.MEDIUM, 240)),
                List.of(user(10, Role.OPERATOR, ti(DAY1, 9, 0, 17, 0)))
        ));

        Allocation allocation = onlyAllocation(result);
        assertEquals(at(DAY1, 9, 0), allocation.startDateTime());
        assertEquals(at(DAY1, 13, 0), allocation.endDateTime());
    }

    @Test
    void shouldDistributeTaskAcrossMultipleWorkingDays() {
        SchedulingResult result = schedule(request(
                List.of(task(1, TaskPriority.MEDIUM, 600)),
                List.of(user(
                        10,
                        Role.OPERATOR,
                        ti(DAY1, 9, 0, 17, 0),
                        ti(DAY2, 9, 0, 17, 0)
                ))
        ));

        TaskSchedule schedule = onlySchedule(result);
        assertEquals(2, schedule.allocations().size());

        Allocation first = schedule.allocations().get(0);
        assertEquals(at(DAY1, 9, 0), first.startDateTime());
        assertEquals(at(DAY1, 17, 0), first.endDateTime());

        Allocation second = schedule.allocations().get(1);
        assertEquals(at(DAY2, 9, 0), second.startDateTime());
        assertEquals(at(DAY2, 11, 0), second.endDateTime());
    }

    @Test
    void shouldScheduleMultipleTasksSequentially() {
        SchedulingResult result = schedule(request(
                List.of(
                        task(1, TaskPriority.MEDIUM, 120),
                        task(2, TaskPriority.MEDIUM, 120)
                ),
                List.of(user(10, Role.OPERATOR, ti(DAY1, 9, 0, 17, 0)))
        ));

        assertEquals(2, result.scheduledTasks().size());

        TaskSchedule first = result.scheduledTasks().get(0);
        assertEquals(1L, first.taskId());
        assertEquals(at(DAY1, 9, 0), first.allocations().get(0).startDateTime());

        TaskSchedule second = result.scheduledTasks().get(1);
        assertEquals(2L, second.taskId());
        assertEquals(at(DAY1, 11, 0), second.allocations().get(0).startDateTime());
        assertEquals(at(DAY1, 13, 0), second.allocations().get(0).endDateTime());
    }

    @Test
    void shouldFailWhenUserHasNoAvailability() {
        SchedulingResult result = schedule(request(
                List.of(task(1, TaskPriority.MEDIUM, 60)),
                List.of(user(10, Role.OPERATOR))
        ));

        assertTrue(result.scheduledTasks().isEmpty());
        assertEquals(1, result.unscheduledTasks().size());
        assertEquals(
                SchedulingFailureReason.NO_AVAILABLE_CAPACITY,
                result.unscheduledTasks().get(0).reason()
        );
    }

    @Test
    void shouldUsePartialDailyAvailabilityWindows() {
        SchedulingResult result = schedule(request(
                List.of(task(1, TaskPriority.MEDIUM, 120)),
                List.of(user(
                        10,
                        Role.OPERATOR,
                        ti(DAY1, 9, 0, 10, 0),
                        ti(DAY1, 11, 0, 13, 0)
                ))
        ));

        TaskSchedule schedule = onlySchedule(result);
        assertEquals(2, schedule.allocations().size());
        assertEquals(120, schedule.totalMinutes());

        assertEquals(at(DAY1, 9, 0), schedule.allocations().get(0).startDateTime());
        assertEquals(at(DAY1, 10, 0), schedule.allocations().get(0).endDateTime());
        assertEquals(at(DAY1, 11, 0), schedule.allocations().get(1).startDateTime());
        assertEquals(at(DAY1, 12, 0), schedule.allocations().get(1).endDateTime());
    }

    @Test
    void shouldAllocateInsideAvailabilitySpanningMultipleDays() {
        SchedulingResult result = schedule(request(
                List.of(task(1, TaskPriority.MEDIUM, 600)),
                List.of(user(
                        10,
                        Role.OPERATOR,
                        new TimeInterval(at(DAY1, 9, 0), at(DAY3, 17, 0))
                ))
        ));

        TaskSchedule schedule = onlySchedule(result);
        assertEquals(2, schedule.allocations().size());
        assertEquals(600, schedule.totalMinutes());

        assertEquals(at(DAY1, 9, 0), schedule.allocations().get(0).startDateTime());
        assertEquals(at(DAY1, 17, 0), schedule.allocations().get(0).endDateTime());
        assertEquals(at(DAY2, 0, 0), schedule.allocations().get(1).startDateTime());
        assertEquals(at(DAY2, 2, 0), schedule.allocations().get(1).endDateTime());
    }

    @Test
    void shouldMergeOverlappingAvailabilityPeriods() {
        SchedulingResult result = schedule(request(
                List.of(task(1, TaskPriority.MEDIUM, 300)),
                List.of(user(
                        10,
                        Role.OPERATOR,
                        ti(DAY1, 9, 0, 13, 0),
                        ti(DAY1, 11, 0, 17, 0)
                ))
        ));

        TaskSchedule schedule = onlySchedule(result);
        assertEquals(1, schedule.allocations().size());
        assertEquals(300, schedule.totalMinutes());
        assertEquals(at(DAY1, 14, 0), schedule.allocations().get(0).endDateTime());
    }

    @Test
    void shouldBlockWorkDuringUnavailability() {
        SchedulingResult result = schedule(request(
                List.of(task(1, TaskPriority.MEDIUM, 240)),
                List.of(user(
                        10,
                        Role.OPERATOR,
                        List.of(ti(DAY1, 9, 0, 17, 0)),
                        List.of(ti(DAY1, 11, 0, 13, 0)),
                        List.of()
                ))
        ));

        TaskSchedule schedule = onlySchedule(result);
        assertEquals(2, schedule.allocations().size());
        assertEquals(240, schedule.totalMinutes());

        assertEquals(at(DAY1, 9, 0), schedule.allocations().get(0).startDateTime());
        assertEquals(at(DAY1, 11, 0), schedule.allocations().get(0).endDateTime());
        assertEquals(at(DAY1, 13, 0), schedule.allocations().get(1).startDateTime());
        assertEquals(at(DAY1, 15, 0), schedule.allocations().get(1).endDateTime());
    }

    @Test
    void shouldAccountExistingAssignmentsInCapacity() {
        SchedulingResult result = schedule(request(
                List.of(task(1, TaskPriority.MEDIUM, 300)),
                List.of(user(
                        10,
                        Role.OPERATOR,
                        List.of(ti(DAY1, 9, 0, 17, 0)),
                        List.of(),
                        List.of(ti(DAY1, 9, 0, 12, 0))
                ))
        ));

        Allocation allocation = onlyAllocation(result);
        assertEquals(at(DAY1, 12, 0), allocation.startDateTime());
        assertEquals(at(DAY1, 17, 0), allocation.endDateTime());
    }

    @Test
    void shouldFailWhenExistingAssignmentLeavesInsufficientCapacity() {
        SchedulingResult result = schedule(request(
                List.of(task(1, TaskPriority.MEDIUM, 360)),
                List.of(user(
                        10,
                        Role.OPERATOR,
                        List.of(ti(DAY1, 9, 0, 17, 0)),
                        List.of(),
                        List.of(ti(DAY1, 9, 0, 14, 0))
                ))
        ));

        assertTrue(result.scheduledTasks().isEmpty());
        UnscheduledTask unscheduled = onlyUnscheduled(result);
        assertEquals(SchedulingFailureReason.INSUFFICIENT_CAPACITY, unscheduled.reason());
        assertTrue(unscheduled.detail().contains("180"));
    }

    @Test
    void shouldNotOverlapExistingAssignment() {
        SchedulingResult result = schedule(request(
                List.of(task(1, TaskPriority.MEDIUM, 180)),
                List.of(user(
                        10,
                        Role.OPERATOR,
                        List.of(ti(DAY1, 9, 0, 17, 0)),
                        List.of(),
                        List.of(ti(DAY1, 13, 0, 15, 0))
                ))
        ));

        Allocation allocation = onlyAllocation(result);
        assertEquals(at(DAY1, 9, 0), allocation.startDateTime());
        assertEquals(at(DAY1, 12, 0), allocation.endDateTime());

        TimeInterval existing = new TimeInterval(at(DAY1, 13, 0), at(DAY1, 15, 0));
        assertTrue(!new TimeInterval(
                allocation.startDateTime(),
                allocation.endDateTime()
        ).overlaps(existing));
    }

    @Test
    void shouldScheduleHigherPriorityTaskFirst() {
        SchedulingResult result = schedule(request(
                List.of(
                        task(1, TaskPriority.LOW, 480),
                        task(2, TaskPriority.CRITICAL, 480)
                ),
                List.of(user(
                        10,
                        Role.OPERATOR,
                        ti(DAY1, 8, 0, 18, 0),
                        ti(DAY2, 8, 0, 18, 0)
                ))
        ));

        assertEquals(2, result.scheduledTasks().size());

        TaskSchedule critical = result.scheduledTasks().get(0);
        assertEquals(2L, critical.taskId());
        assertEquals(at(DAY1, 8, 0), critical.allocations().get(0).startDateTime());

        TaskSchedule low = result.scheduledTasks().get(1);
        assertEquals(1L, low.taskId());
        assertEquals(at(DAY2, 8, 0), low.allocations().get(0).startDateTime());
    }

    @Test
    void shouldOrderSamePriorityTasksByDeadline() {
        SchedulingResult result = schedule(request(
                List.of(
                        task(1, TaskPriority.MEDIUM, 240, at(DAY2, 12, 0)),
                        task(2, TaskPriority.MEDIUM, 240, at(DAY1, 12, 0))
                ),
                List.of(user(
                        10,
                        Role.OPERATOR,
                        ti(DAY1, 8, 0, 18, 0),
                        ti(DAY2, 8, 0, 18, 0)
                ))
        ));

        assertEquals(2, result.scheduledTasks().size());

        TaskSchedule earliestDeadline = result.scheduledTasks().get(0);
        assertEquals(2L, earliestDeadline.taskId());
        assertEquals(at(DAY1, 8, 0), earliestDeadline.allocations().get(0).startDateTime());
        assertEquals(at(DAY1, 12, 0), earliestDeadline.allocations().get(0).endDateTime());

        TaskSchedule laterDeadline = result.scheduledTasks().get(1);
        assertEquals(1L, laterDeadline.taskId());
        assertEquals(at(DAY1, 12, 0), laterDeadline.allocations().get(0).startDateTime());
    }

    @Test
    void shouldOrderSamePriorityTasksWithoutDeadlinesByTaskId() {
        SchedulingResult result = schedule(request(
                List.of(
                        task(5, TaskPriority.HIGH, 60),
                        task(3, TaskPriority.HIGH, 60)
                ),
                List.of(user(10, Role.OPERATOR, ti(DAY1, 9, 0, 17, 0)))
        ));

        assertEquals(2, result.scheduledTasks().size());
        assertEquals(3L, result.scheduledTasks().get(0).taskId());
        assertEquals(at(DAY1, 9, 0), result.scheduledTasks().get(0).allocations().get(0).startDateTime());
        assertEquals(5L, result.scheduledTasks().get(1).taskId());
        assertEquals(at(DAY1, 10, 0), result.scheduledTasks().get(1).allocations().get(0).startDateTime());
    }

    @Test
    void shouldRespectFullPriorityRanking() {
        SchedulingResult result = schedule(request(
                List.of(
                        task(1, TaskPriority.LOW, 60),
                        task(2, TaskPriority.MEDIUM, 60),
                        task(3, TaskPriority.HIGH, 60),
                        task(4, TaskPriority.CRITICAL, 60)
                ),
                List.of(user(10, Role.OPERATOR, ti(DAY1, 9, 0, 10, 0)))
        ));

        assertEquals(1, result.scheduledTasks().size());
        assertEquals(4L, result.scheduledTasks().get(0).taskId());
        assertEquals(3, result.unscheduledTasks().size());
    }

    @Test
    void shouldNotScheduleWorkBeforeDeadlineConstrainedWindow() {
        SchedulingResult result = schedule(request(
                List.of(task(1, TaskPriority.MEDIUM, 600, at(DAY2, 12, 0))),
                List.of(user(
                        10,
                        Role.OPERATOR,
                        ti(DAY1, 8, 0, 18, 0),
                        ti(DAY2, 8, 0, 18, 0)
                ))
        ));

        TaskSchedule schedule = onlySchedule(result);
        assertEquals(2, schedule.allocations().size());
        assertEquals(600, schedule.totalMinutes());
        assertEquals(at(DAY2, 10, 0), schedule.allocations().get(1).endDateTime());
    }

    @Test
    void shouldFailWhenTaskCannotFinishBeforeDeadline() {
        SchedulingResult result = schedule(request(
                List.of(task(1, TaskPriority.MEDIUM, 480, at(DAY1, 12, 0))),
                List.of(user(10, Role.OPERATOR, ti(DAY1, 8, 0, 18, 0)))
        ));

        assertTrue(result.scheduledTasks().isEmpty());
        UnscheduledTask unscheduled = onlyUnscheduled(result);
        assertEquals(SchedulingFailureReason.INSUFFICIENT_CAPACITY, unscheduled.reason());
        assertTrue(unscheduled.detail().contains("240"));
    }

    @Test
    void shouldFillAvailableWindowExactly() {
        SchedulingResult result = schedule(request(
                List.of(task(1, TaskPriority.MEDIUM, 480)),
                List.of(user(10, Role.OPERATOR, ti(DAY1, 9, 0, 17, 0)))
        ));

        Allocation allocation = onlyAllocation(result);
        assertEquals(at(DAY1, 9, 0), allocation.startDateTime());
        assertEquals(at(DAY1, 17, 0), allocation.endDateTime());
    }

    @Test
    void shouldSkipUnavailableUserAndSelectNextEligibleUser() {
        SchedulingResult result = schedule(request(
                List.of(task(1, TaskPriority.MEDIUM, 60)),
                List.of(
                        user(10, Role.OPERATOR),
                        user(20, Role.OPERATOR, ti(DAY1, 9, 0, 17, 0))
                )
        ));

        assertEquals(20L, onlySchedule(result).userId());
    }

    @Test
    void shouldSelectEligibleUsersDeterministicallyByUserId() {
        SchedulingResult result = schedule(request(
                List.of(task(1, TaskPriority.MEDIUM, 60)),
                List.of(
                        user(30, Role.OPERATOR, ti(DAY1, 9, 0, 17, 0)),
                        user(20, Role.REVIEWER, ti(DAY1, 9, 0, 17, 0))
                )
        ));

        assertEquals(20L, onlySchedule(result).userId());
    }

    @Test
    void shouldProduceIdenticalResultsForIdenticalRequests() {
        SchedulingRequest request = request(
                List.of(
                        task(1, TaskPriority.HIGH, 120),
                        task(2, TaskPriority.MEDIUM, 60)
                ),
                List.of(
                        user(20, Role.REVIEWER, ti(DAY1, 9, 0, 17, 0)),
                        user(10, Role.OPERATOR, ti(DAY1, 9, 0, 17, 0))
                )
        );

        SchedulingResult first = schedule(request);
        SchedulingResult second = schedule(request);

        assertEquals(first, second);
    }

    @Test
    void shouldSkipUserWithInsufficientCapacityAndUseNextUser() {
        SchedulingResult result = schedule(request(
                List.of(task(1, TaskPriority.MEDIUM, 240)),
                List.of(
                        user(10, Role.OPERATOR, ti(DAY1, 9, 0, 11, 0)),
                        user(20, Role.OPERATOR, ti(DAY1, 9, 0, 17, 0))
                )
        ));

        TaskSchedule schedule = onlySchedule(result);
        assertEquals(20L, schedule.userId());
        assertEquals(at(DAY1, 9, 0), schedule.allocations().get(0).startDateTime());
        assertEquals(at(DAY1, 13, 0), schedule.allocations().get(0).endDateTime());
    }

    @Test
    void shouldFailWhenTaskCannotBeFullyScheduled() {
        SchedulingResult result = schedule(request(
                List.of(task(1, TaskPriority.MEDIUM, 500)),
                List.of(user(10, Role.OPERATOR, ti(DAY1, 9, 0, 17, 0)))
        ));

        assertTrue(result.scheduledTasks().isEmpty());
        assertEquals(SchedulingFailureReason.INSUFFICIENT_CAPACITY, onlyUnscheduled(result).reason());
    }

    @Test
    void shouldFailWhenNoEligibleUserExists() {
        SchedulingResult result = schedule(request(
                List.of(task(1, TaskPriority.MEDIUM, 60)),
                List.of(user(10, Role.ADMIN, ti(DAY1, 9, 0, 17, 0)))
        ));

        assertTrue(result.scheduledTasks().isEmpty());
        assertEquals(SchedulingFailureReason.NO_ELIGIBLE_USER, onlyUnscheduled(result).reason());
    }

    @Test
    void shouldFailWhenTotalCapacityIsInsufficient() {
        SchedulingResult result = schedule(request(
                List.of(task(1, TaskPriority.MEDIUM, 1000)),
                List.of(user(
                        10,
                        Role.OPERATOR,
                        ti(DAY1, 9, 0, 17, 0),
                        ti(DAY2, 9, 0, 17, 0)
                ))
        ));

        assertTrue(result.scheduledTasks().isEmpty());
        assertEquals(SchedulingFailureReason.INSUFFICIENT_CAPACITY, onlyUnscheduled(result).reason());
    }

    @Test
    void shouldFailWhenCapacityExistsOnlyOutsideTaskWindow() {
        SchedulingResult result = schedule(request(
                List.of(task(1, TaskPriority.MEDIUM, 60, at(DAY1, 12, 0))),
                List.of(user(10, Role.OPERATOR, ti(DAY3, 9, 0, 17, 0)))
        ));

        assertTrue(result.scheduledTasks().isEmpty());
        assertEquals(SchedulingFailureReason.NO_AVAILABLE_CAPACITY, onlyUnscheduled(result).reason());
    }

    @Test
    void shouldScheduleTaskRequiringExactlyOneAvailableHour() {
        SchedulingResult result = schedule(request(
                List.of(task(1, TaskPriority.MEDIUM, 60)),
                List.of(user(10, Role.OPERATOR, ti(DAY1, 9, 0, 10, 0)))
        ));

        Allocation allocation = onlyAllocation(result);
        assertEquals(at(DAY1, 9, 0), allocation.startDateTime());
        assertEquals(at(DAY1, 10, 0), allocation.endDateTime());
    }

    @Test
    void shouldEndTaskExactlyOnLastAvailableMoment() {
        SchedulingResult result = schedule(request(
                List.of(task(1, TaskPriority.MEDIUM, 720)),
                List.of(user(
                        10,
                        Role.OPERATOR,
                        ti(DAY1, 9, 0, 17, 0),
                        ti(DAY2, 9, 0, 13, 0)
                ))
        ));

        TaskSchedule schedule = onlySchedule(result);
        assertEquals(2, schedule.allocations().size());
        assertEquals(720, schedule.totalMinutes());
        assertEquals(at(DAY2, 13, 0), schedule.allocations().get(1).endDateTime());
    }

    @Test
    void shouldFailWhenRemainingCapacityIsZero() {
        SchedulingResult result = schedule(request(
                List.of(task(1, TaskPriority.MEDIUM, 60)),
                List.of(user(
                        10,
                        Role.OPERATOR,
                        List.of(ti(DAY1, 9, 0, 17, 0)),
                        List.of(),
                        List.of(ti(DAY1, 9, 0, 17, 0))
                ))
        ));

        assertTrue(result.scheduledTasks().isEmpty());
        assertEquals(SchedulingFailureReason.NO_AVAILABLE_CAPACITY, onlyUnscheduled(result).reason());
    }

    @Test
    void shouldAllowAssignmentsTouchingUnavailabilityBoundaries() {
        SchedulingResult result = schedule(request(
                List.of(task(1, TaskPriority.MEDIUM, 240)),
                List.of(user(
                        10,
                        Role.OPERATOR,
                        List.of(ti(DAY1, 9, 0, 17, 0)),
                        List.of(ti(DAY1, 12, 0, 13, 0)),
                        List.of()
                ))
        ));

        TaskSchedule schedule = onlySchedule(result);
        assertEquals(2, schedule.allocations().size());

        assertEquals(at(DAY1, 12, 0), schedule.allocations().get(0).endDateTime());
        assertEquals(at(DAY1, 13, 0), schedule.allocations().get(1).startDateTime());
    }

    @Test
    void shouldOrderIdenticalTasksDeterministically() {
        SchedulingResult result = schedule(request(
                List.of(
                        task(11, TaskPriority.MEDIUM, 60),
                        task(10, TaskPriority.MEDIUM, 60)
                ),
                List.of(user(10, Role.OPERATOR, ti(DAY1, 9, 0, 17, 0)))
        ));

        assertEquals(2, result.scheduledTasks().size());
        assertEquals(10L, result.scheduledTasks().get(0).taskId());
        assertEquals(11L, result.scheduledTasks().get(1).taskId());
    }

    @Test
    void shouldPreserveAllSchedulingInvariants() {
        SchedulingResult result = schedule(request(
                List.of(
                        task(1, TaskPriority.CRITICAL, 700),
                        task(2, TaskPriority.MEDIUM, 200),
                        task(3, TaskPriority.LOW, 90)
                ),
                List.of(
                        user(
                                10,
                                Role.OPERATOR,
                                List.of(
                                        ti(DAY1, 9, 0, 17, 0),
                                        ti(DAY2, 9, 0, 17, 0)
                                ),
                                List.of(ti(DAY1, 13, 0, 14, 0)),
                                List.of(ti(DAY1, 9, 0, 10, 0))
                        ),
                        user(
                                20,
                                Role.REVIEWER,
                                List.of(new TimeInterval(at(DAY1, 8, 0), at(DAY2, 18, 0))),
                                List.of(),
                                List.of()
                        )
                )
        ));

        assertEquals(3, result.scheduledTasks().size());

        for (TaskSchedule schedule : result.scheduledTasks()) {
            assertTrue(schedule.totalMinutes() > 0);

            for (Allocation allocation : schedule.allocations()) {
                assertTrue(!allocation.startDateTime().isBefore(WINDOW_START));
                assertTrue(!allocation.endDateTime().isAfter(WINDOW_END));
            }

            for (int i = 1; i < schedule.allocations().size(); i++) {
                assertTrue(schedule.allocations().get(i - 1).endDateTime()
                        .compareTo(schedule.allocations().get(i).startDateTime()) <= 0);
            }
        }

        for (SchedulingUser user : List.of(
                user(10, Role.OPERATOR),
                user(20, Role.REVIEWER)
        )) {
            List<Allocation> allocations = result.scheduledTasks().stream()
                    .filter(schedule -> schedule.userId().equals(user.userId()))
                    .flatMap(schedule -> schedule.allocations().stream())
                    .toList();

            for (int i = 1; i < allocations.size(); i++) {
                assertTrue(allocations.get(i - 1).endDateTime()
                        .compareTo(allocations.get(i).startDateTime()) <= 0);
            }
        }
    }

    @Test
    void shouldRejectInvalidRequests() {
        List<SchedulingTask> tasks = List.of(task(1, TaskPriority.MEDIUM, 60));
        List<SchedulingUser> users = List.of(user(10, Role.OPERATOR, ti(DAY1, 9, 0, 17, 0)));

        assertThrows(ValidationException.class,
                () -> engine.schedule(null));

        assertThrows(ValidationException.class,
                () -> engine.schedule(new SchedulingRequest(
                        WINDOW_END, WINDOW_START, tasks, users, SchedulingOptions.defaults())));

        assertThrows(ValidationException.class,
                () -> engine.schedule(new SchedulingRequest(
                        null, WINDOW_END, tasks, users, SchedulingOptions.defaults())));

        assertThrows(ValidationException.class,
                () -> engine.schedule(new SchedulingRequest(
                        WINDOW_START, WINDOW_END,
                        List.of(task(1, TaskPriority.MEDIUM, 0)),
                        users,
                        SchedulingOptions.defaults())));

        assertThrows(ValidationException.class,
                () -> engine.schedule(new SchedulingRequest(
                        WINDOW_START, WINDOW_END,
                        tasks,
                        users,
                        new SchedulingOptions(480, Set.of()))));

        assertThrows(ValidationException.class,
                () -> engine.schedule(new SchedulingRequest(
                        WINDOW_START, WINDOW_END, tasks, users, null)));
    }

    @Test
    void shouldReturnEmptyResultForEmptyTaskList() {
        SchedulingResult result = schedule(request(
                List.of(),
                List.of(user(10, Role.OPERATOR, ti(DAY1, 9, 0, 17, 0)))
        ));

        assertTrue(result.scheduledTasks().isEmpty());
        assertTrue(result.unscheduledTasks().isEmpty());
    }

    private TaskSchedule onlySchedule(SchedulingResult result) {
        assertEquals(1, result.scheduledTasks().size());
        return result.scheduledTasks().get(0);
    }

    private Allocation onlyAllocation(SchedulingResult result) {
        TaskSchedule schedule = onlySchedule(result);
        assertEquals(1, schedule.allocations().size());
        return schedule.allocations().get(0);
    }

    private UnscheduledTask onlyUnscheduled(SchedulingResult result) {
        assertEquals(1, result.unscheduledTasks().size());
        return result.unscheduledTasks().get(0);
    }
}
