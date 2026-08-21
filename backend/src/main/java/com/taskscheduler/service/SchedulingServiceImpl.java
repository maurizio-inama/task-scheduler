package com.taskscheduler.service;

import com.taskscheduler.domain.entity.Assignment;
import com.taskscheduler.domain.entity.AssignmentStatus;
import com.taskscheduler.domain.entity.Availability;
import com.taskscheduler.domain.entity.Schedule;
import com.taskscheduler.domain.entity.ScheduleStatus;
import com.taskscheduler.domain.entity.Task;
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
import com.taskscheduler.scheduling.model.TaskSchedule;
import com.taskscheduler.scheduling.model.TimeInterval;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SchedulingServiceImpl implements SchedulingService {

    private final ScheduleRepository scheduleRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final AvailabilityRepository availabilityRepository;
    private final UnavailabilityRepository unavailabilityRepository;
    private final AssignmentRepository assignmentRepository;
    private final SchedulingEngine schedulingEngine;

    public SchedulingServiceImpl(
            ScheduleRepository scheduleRepository,
            TaskRepository taskRepository,
            UserRepository userRepository,
            AvailabilityRepository availabilityRepository,
            UnavailabilityRepository unavailabilityRepository,
            AssignmentRepository assignmentRepository,
            SchedulingEngine schedulingEngine
    ) {
        this.scheduleRepository = scheduleRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.availabilityRepository = availabilityRepository;
        this.unavailabilityRepository = unavailabilityRepository;
        this.assignmentRepository = assignmentRepository;
        this.schedulingEngine = schedulingEngine;
    }

    @Override
    @Transactional
    public SchedulingResult generate(Long scheduleId) {
        Schedule schedule = loadSchedule(scheduleId);

        List<Task> pendingTasks = taskRepository.findByStatus(TaskStatus.PENDING);
        List<Assignment> activeAssignments =
                assignmentRepository.findByStatusNot(AssignmentStatus.CANCELLED);

        checkNoDuplicateAssignments(pendingTasks, activeAssignments);

        List<SchedulingTask> tasks = toSchedulingTasks(pendingTasks);
        List<SchedulingUser> users = toSchedulingUsers(schedule, activeAssignments);

        SchedulingRequest request = new SchedulingRequest(
                schedule.getStartDateTime(),
                schedule.getEndDateTime(),
                tasks,
                users,
                SchedulingOptions.defaults()
        );

        SchedulingResult result = schedulingEngine.schedule(request);

        persistScheduledTasks(schedule, result);

        return result;
    }

    private Schedule loadSchedule(Long scheduleId) {
        if (scheduleId == null) {
            throw new EntityNotFoundException("Schedule not found: null");
        }

        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Schedule not found: " + scheduleId
                        )
                );

        if (schedule.getStatus() == ScheduleStatus.CANCELLED) {
            throw new BusinessRuleException(
                    "Schedule is cancelled: " + scheduleId
            );
        }

        return schedule;
    }

    private void checkNoDuplicateAssignments(
            List<Task> pendingTasks,
            List<Assignment> activeAssignments
    ) {
        for (Task task : pendingTasks) {
            boolean alreadyAssigned = activeAssignments.stream()
                    .anyMatch(assignment ->
                            assignment.getTask().getId().equals(task.getId()));

            if (alreadyAssigned) {
                throw new BusinessRuleException(
                        "Task already has assignments: " + task.getId()
                );
            }
        }
    }

    private List<SchedulingTask> toSchedulingTasks(List<Task> tasks) {
        return tasks.stream()
                .map(task -> new SchedulingTask(
                        task.getId(),
                        task.getPriority(),
                        task.getEstimatedDurationMinutes(),
                        task.getDeadline()
                ))
                .toList();
    }

    private List<SchedulingUser> toSchedulingUsers(
            Schedule schedule,
            List<Assignment> activeAssignments
    ) {
        Map<Long, List<TimeInterval>> existingByUser =
                groupExistingAssignments(schedule, activeAssignments);

        List<SchedulingUser> users = new ArrayList<>();

        for (User user : userRepository.findAll()) {
            if (!user.isEnabled()) {
                continue;
            }

            users.add(new SchedulingUser(
                    user.getId(),
                    user.getRole(),
                    availabilityToIntervals(
                            availabilityRepository.findByUserId(user.getId())),
                    unavailabilityToIntervals(
                            unavailabilityRepository.findByUserId(user.getId())),
                    existingByUser.getOrDefault(user.getId(), List.of())
            ));
        }

        return users;
    }

    private Map<Long, List<TimeInterval>> groupExistingAssignments(
            Schedule schedule,
            List<Assignment> activeAssignments
    ) {
        Map<Long, List<TimeInterval>> existingByUser = new HashMap<>();

        for (Assignment assignment : activeAssignments) {
            TimeInterval interval = new TimeInterval(
                    assignment.getStartDateTime(),
                    assignment.getEndDateTime()
            );

            if (!interval.overlaps(new TimeInterval(
                    schedule.getStartDateTime(),
                    schedule.getEndDateTime()
            ))) {
                continue;
            }

            Long userId = assignment.getUser().getId();
            existingByUser
                    .computeIfAbsent(userId, key -> new ArrayList<>())
                    .add(interval);
        }

        return existingByUser;
    }

    private List<TimeInterval> availabilityToIntervals(List<Availability> availabilities) {
        return availabilities.stream()
                .map(availability -> new TimeInterval(
                        availability.getStartDateTime(),
                        availability.getEndDateTime()
                ))
                .toList();
    }

    private List<TimeInterval> unavailabilityToIntervals(List<Unavailability> unavailabilities) {
        return unavailabilities.stream()
                .map(unavailability -> new TimeInterval(
                        unavailability.getStartDateTime(),
                        unavailability.getEndDateTime()
                ))
                .toList();
    }

    private void persistScheduledTasks(Schedule schedule, SchedulingResult result) {
        for (TaskSchedule taskSchedule : result.scheduledTasks()) {
            Task task = taskRepository.findById(taskSchedule.taskId())
                    .orElseThrow(() ->
                            new EntityNotFoundException(
                                    "Task not found: " + taskSchedule.taskId()
                            )
                    );

            for (Allocation allocation : taskSchedule.allocations()) {
                Assignment assignment = new Assignment();
                assignment.setUser(new User(taskSchedule.userId()));
                assignment.setTask(task);
                assignment.setSchedule(schedule);
                assignment.setStartDateTime(allocation.startDateTime());
                assignment.setEndDateTime(allocation.endDateTime());
                assignment.setStatus(AssignmentStatus.ASSIGNED);

                assignmentRepository.save(assignment);
            }

            task.setStatus(TaskStatus.SCHEDULED);
            taskRepository.save(task);
        }
    }
}
