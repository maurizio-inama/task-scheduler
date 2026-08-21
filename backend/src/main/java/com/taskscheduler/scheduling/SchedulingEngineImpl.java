package com.taskscheduler.scheduling;

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
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Component
public class SchedulingEngineImpl implements SchedulingEngine {

    @Override
    public SchedulingResult schedule(SchedulingRequest request) {
        validate(request);

        List<SchedulingTask> orderedTasks = orderTasks(request.tasks());
        Map<Long, UserState> userStates = initUserStates(request);
        SchedulingOptions options = request.options();

        List<TaskSchedule> scheduled = new ArrayList<>();
        List<UnscheduledTask> unscheduled = new ArrayList<>();

        for (SchedulingTask task : orderedTasks) {
            LocalDateTime windowStart = request.windowStart();
            LocalDateTime windowEnd = effectiveWindowEnd(request, task);

            if (!windowStart.isBefore(windowEnd)) {
                unscheduled.add(new UnscheduledTask(
                        task.taskId(),
                        SchedulingFailureReason.OUTSIDE_TASK_WINDOW,
                        "The schedulable window of the task is empty: deadline "
                                + task.deadline()
                                + " is not after the window start "
                                + windowStart
                ));
                continue;
            }

            scheduleTask(task, windowStart, windowEnd, userStates, options, scheduled, unscheduled);
        }

        return new SchedulingResult(List.copyOf(scheduled), List.copyOf(unscheduled));
    }

    private void scheduleTask(
            SchedulingTask task,
            LocalDateTime windowStart,
            LocalDateTime windowEnd,
            Map<Long, UserState> userStates,
            SchedulingOptions options,
            List<TaskSchedule> scheduled,
            List<UnscheduledTask> unscheduled
    ) {
        if (userStates.isEmpty()) {
            unscheduled.add(new UnscheduledTask(
                    task.taskId(),
                    SchedulingFailureReason.NO_ELIGIBLE_USER,
                    "No enabled user has an eligible role"
            ));
            return;
        }

        int bestAvailableMinutes = 0;

        for (Map.Entry<Long, UserState> entry : userStates.entrySet()) {
            UserState trialState = entry.getValue().copy();
            Attempt attempt = allocate(
                    trialState,
                    task.estimatedDurationMinutes(),
                    windowStart,
                    windowEnd,
                    options
            );

            bestAvailableMinutes = Math.max(
                    bestAvailableMinutes,
                    totalMinutes(attempt.allocations())
            );

            if (attempt.remainingMinutes() == 0) {
                entry.getValue().absorb(trialState);
                scheduled.add(new TaskSchedule(
                        task.taskId(),
                        entry.getKey(),
                        attempt.allocations()
                ));
                return;
            }
        }

        if (bestAvailableMinutes == 0) {
            unscheduled.add(new UnscheduledTask(
                    task.taskId(),
                    SchedulingFailureReason.NO_AVAILABLE_CAPACITY,
                    "No eligible user has available capacity inside the task window"
            ));
        } else {
            unscheduled.add(new UnscheduledTask(
                    task.taskId(),
                    SchedulingFailureReason.INSUFFICIENT_CAPACITY,
                    "Best eligible user offers "
                            + bestAvailableMinutes
                            + " of the required "
                            + task.estimatedDurationMinutes()
                            + " minutes"
            ));
        }
    }

    private Attempt allocate(
            UserState state,
            int requiredMinutes,
            LocalDateTime windowStart,
            LocalDateTime windowEnd,
            SchedulingOptions options
    ) {
        int remaining = requiredMinutes;
        List<Allocation> allocations = new ArrayList<>();

        for (TimeInterval freeInterval : List.copyOf(state.freeIntervals())) {
            if (remaining == 0) {
                break;
            }

            LocalDateTime segmentStart = max(freeInterval.start(), windowStart);
            LocalDateTime segmentEnd = min(freeInterval.end(), windowEnd);

            if (!segmentStart.isBefore(segmentEnd)) {
                continue;
            }

            remaining -= allocateWithinSegment(
                    state,
                    allocations,
                    segmentStart,
                    segmentEnd,
                    remaining,
                    options
            );
        }

        for (Allocation allocation : allocations) {
            state.replaceFreeIntervals(subtract(
                    state.freeIntervals(),
                    new TimeInterval(allocation.startDateTime(), allocation.endDateTime())
            ));
        }

        return new Attempt(allocations, remaining);
    }

    private int allocateWithinSegment(
            UserState state,
            List<Allocation> allocations,
            LocalDateTime segmentStart,
            LocalDateTime segmentEnd,
            int remainingMinutes,
            SchedulingOptions options
    ) {
        int remaining = remainingMinutes;
        LocalDateTime cursor = segmentStart;

        while (cursor.isBefore(segmentEnd) && remaining > 0) {
            LocalDate day = cursor.toLocalDate();
            LocalDateTime dayEnd = day.plusDays(1).atStartOfDay();
            LocalDateTime chunkEnd = min(segmentEnd, dayEnd);

            int dailyRemaining =
                    options.maxMinutesPerDay() - state.usedMinutesPerDay.getOrDefault(day, 0);
            int chunkMinutes = (int) Duration.between(cursor, chunkEnd).toMinutes();
            int take = Math.min(Math.min(remaining, chunkMinutes), dailyRemaining);

            if (take > 0) {
                LocalDateTime allocationEnd = cursor.plusMinutes(take);
                allocations.add(new Allocation(cursor, allocationEnd));
                state.usedMinutesPerDay.merge(day, take, Integer::sum);
                cursor = allocationEnd;
                remaining -= take;
            } else if (dailyRemaining <= 0) {
                cursor = dayEnd;
            } else {
                cursor = chunkEnd;
            }
        }

        return remainingMinutes - remaining;
    }

    private Map<Long, UserState> initUserStates(SchedulingRequest request) {
        Map<Long, UserState> states = new TreeMap<>();

        for (SchedulingUser user : request.users()) {
            if (!request.options().eligibleRoles().contains(user.role())) {
                continue;
            }

            states.put(user.userId(), initUserState(user));
        }

        return states;
    }

    private UserState initUserState(SchedulingUser user) {
        List<TimeInterval> free = merge(user.availabilities());

        for (TimeInterval unavailability : merge(user.unavailabilities())) {
            free = subtract(free, unavailability);
        }

        Map<LocalDate, Integer> usedMinutesPerDay = new HashMap<>();

        for (TimeInterval assignment : user.existingAssignments()) {
            free = subtract(free, assignment);
            addUsedMinutesPerDay(usedMinutesPerDay, assignment);
        }

        free.sort(Comparator.comparing(TimeInterval::start));

        return new UserState(free, usedMinutesPerDay);
    }

    private void addUsedMinutesPerDay(Map<LocalDate, Integer> usedMinutesPerDay, TimeInterval interval) {
        LocalDateTime cursor = interval.start();

        while (cursor.isBefore(interval.end())) {
            LocalDate day = cursor.toLocalDate();
            LocalDateTime dayEnd = day.plusDays(1).atStartOfDay();
            LocalDateTime chunkEnd = min(interval.end(), dayEnd);

            usedMinutesPerDay.merge(
                    day,
                    (int) Duration.between(cursor, chunkEnd).toMinutes(),
                    Integer::sum
            );

            cursor = chunkEnd;
        }
    }

    private List<TimeInterval> merge(List<TimeInterval> intervals) {
        List<TimeInterval> sorted = new ArrayList<>(intervals);
        sorted.sort(Comparator.comparing(TimeInterval::start));

        List<TimeInterval> merged = new ArrayList<>();

        for (TimeInterval interval : sorted) {
            if (interval.isEmpty()) {
                continue;
            }

            if (merged.isEmpty()) {
                merged.add(interval);
                continue;
            }

            TimeInterval last = merged.get(merged.size() - 1);

            if (!interval.start().isAfter(last.end())) {
                merged.set(
                        merged.size() - 1,
                        new TimeInterval(last.start(), max(last.end(), interval.end()))
                );
            } else {
                merged.add(interval);
            }
        }

        return merged;
    }

    private List<TimeInterval> subtract(List<TimeInterval> intervals, TimeInterval cut) {
        List<TimeInterval> result = new ArrayList<>();

        for (TimeInterval interval : intervals) {
            if (!interval.overlaps(cut)) {
                result.add(interval);
                continue;
            }

            if (interval.start().isBefore(cut.start())) {
                result.add(new TimeInterval(interval.start(), cut.start()));
            }

            if (cut.end().isBefore(interval.end())) {
                result.add(new TimeInterval(cut.end(), interval.end()));
            }
        }

        result.removeIf(TimeInterval::isEmpty);

        return result;
    }

    private List<SchedulingTask> orderTasks(List<SchedulingTask> tasks) {
        List<SchedulingTask> ordered = new ArrayList<>(tasks);

        ordered.sort(
                Comparator
                        .comparingInt((SchedulingTask task) -> -priorityRank(task.priority()))
                        .thenComparing(
                                task -> task.deadline() == null
                                        ? LocalDateTime.MAX
                                        : task.deadline()
                        )
                        .thenComparing(SchedulingTask::taskId)
        );

        return ordered;
    }

    private int priorityRank(TaskPriority priority) {
        return switch (priority) {
            case LOW -> 1;
            case MEDIUM -> 2;
            case HIGH -> 3;
            case CRITICAL -> 4;
        };
    }

    private LocalDateTime effectiveWindowEnd(SchedulingRequest request, SchedulingTask task) {
        if (task.deadline() == null) {
            return request.windowEnd();
        }

        return task.deadline().isBefore(request.windowEnd())
                ? task.deadline()
                : request.windowEnd();
    }

    private int totalMinutes(List<Allocation> allocations) {
        return (int) allocations.stream()
                .mapToLong(Allocation::durationMinutes)
                .sum();
    }

    private void validate(SchedulingRequest request) {
        if (request == null) {
            throw new ValidationException("Scheduling request must not be null");
        }

        if (request.windowStart() == null || request.windowEnd() == null) {
            throw new ValidationException("Scheduling window bounds must not be null");
        }

        if (!request.windowStart().isBefore(request.windowEnd())) {
            throw new ValidationException(
                    "Scheduling window start must be before scheduling window end"
            );
        }

        if (request.tasks() == null || request.users() == null) {
            throw new ValidationException("Tasks and users must not be null");
        }

        validateOptions(request.options());

        for (SchedulingTask task : request.tasks()) {
            validateTask(task);
        }

        for (SchedulingUser user : request.users()) {
            validateUser(user);
        }
    }

    private void validateOptions(SchedulingOptions options) {
        if (options == null) {
            throw new ValidationException("Scheduling options must not be null");
        }

        if (options.maxMinutesPerDay() <= 0) {
            throw new ValidationException("Maximum minutes per day must be positive");
        }

        if (options.eligibleRoles() == null || options.eligibleRoles().isEmpty()) {
            throw new ValidationException("Eligible roles must not be empty");
        }
    }

    private void validateTask(SchedulingTask task) {
        if (task == null) {
            throw new ValidationException("Task must not be null");
        }

        if (task.taskId() == null) {
            throw new ValidationException("Task id must not be null");
        }

        if (task.priority() == null) {
            throw new ValidationException("Task priority must not be null");
        }

        if (task.estimatedDurationMinutes() <= 0) {
            throw new ValidationException("Task duration must be positive: " + task.taskId());
        }
    }

    private void validateUser(SchedulingUser user) {
        if (user == null) {
            throw new ValidationException("User must not be null");
        }

        if (user.userId() == null) {
            throw new ValidationException("User id must not be null");
        }

        if (user.role() == null) {
            throw new ValidationException("User role must not be null");
        }

        validateIntervals(user.availabilities(), "availability", user.userId());
        validateIntervals(user.unavailabilities(), "unavailability", user.userId());
        validateIntervals(user.existingAssignments(), "existing assignment", user.userId());
    }

    private void validateIntervals(List<TimeInterval> intervals, String name, Long userId) {
        if (intervals == null) {
            throw new ValidationException(
                    "User " + userId + " has null " + name + " intervals"
            );
        }

        for (TimeInterval interval : intervals) {
            if (interval == null
                    || interval.start() == null
                    || interval.end() == null) {
                throw new ValidationException(
                        "User " + userId + " has an invalid " + name + " interval"
                );
            }

            if (interval.end().isBefore(interval.start())) {
                throw new ValidationException(
                        "User " + userId + " has an " + name
                                + " interval ending before it starts"
                );
            }
        }
    }

    private LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private static final class UserState {

        private List<TimeInterval> freeIntervals;
        private final Map<LocalDate, Integer> usedMinutesPerDay;

        private UserState(List<TimeInterval> freeIntervals, Map<LocalDate, Integer> usedMinutesPerDay) {
            this.freeIntervals = freeIntervals;
            this.usedMinutesPerDay = usedMinutesPerDay;
        }

        private List<TimeInterval> freeIntervals() {
            return freeIntervals;
        }

        private void replaceFreeIntervals(List<TimeInterval> intervals) {
            freeIntervals = intervals;
        }

        private UserState copy() {
            return new UserState(
                    new ArrayList<>(freeIntervals),
                    new HashMap<>(usedMinutesPerDay)
            );
        }

        private void absorb(UserState other) {
            freeIntervals = new ArrayList<>(other.freeIntervals);
            usedMinutesPerDay.clear();
            usedMinutesPerDay.putAll(other.usedMinutesPerDay);
        }
    }

    private record Attempt(List<Allocation> allocations, int remainingMinutes) {
    }
}
