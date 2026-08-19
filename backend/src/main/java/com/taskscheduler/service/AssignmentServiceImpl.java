package com.taskscheduler.service;

import com.taskscheduler.domain.entity.Assignment;
import com.taskscheduler.domain.entity.Task;
import com.taskscheduler.domain.entity.TaskStatus;
import com.taskscheduler.domain.entity.Schedule;
import com.taskscheduler.domain.entity.ScheduleStatus;
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
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AssignmentServiceImpl implements AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final ScheduleRepository scheduleRepository;
    private final AvailabilityRepository availabilityRepository;
    private final UnavailabilityRepository unavailabilityRepository;

    public AssignmentServiceImpl(
            AssignmentRepository assignmentRepository,
            UserRepository userRepository,
            TaskRepository taskRepository,
            ScheduleRepository scheduleRepository,
            AvailabilityRepository availabilityRepository,
            UnavailabilityRepository unavailabilityRepository
    ) {
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.scheduleRepository = scheduleRepository;
        this.availabilityRepository = availabilityRepository;
        this.unavailabilityRepository = unavailabilityRepository;
    }

    @Override
    public Assignment create(Assignment assignment) {
        validate(assignment);
        checkUser(assignment);
        checkTask(assignment);
        checkSchedule(assignment);
        checkAvailability(assignment);
        checkUnavailability(assignment);
        checkConflict(assignment, null);

        return assignmentRepository.save(assignment);
    }

    @Override
    public Assignment getById(Long id) {
        return assignmentRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Assignment not found: " + id
                        )
                );
    }

    @Override
    public List<Assignment> getAll() {
        return assignmentRepository.findAll();
    }

    @Override
    public Assignment update(Long id, Assignment assignment) {
        Assignment existing = getById(id);

        validate(assignment);
        checkUser(assignment);
        checkTask(assignment);
        checkSchedule(assignment);
        checkAvailability(assignment);
        checkUnavailability(assignment);
        checkConflict(assignment, id);

        existing.setUser(assignment.getUser());
        existing.setTask(assignment.getTask());
        existing.setSchedule(assignment.getSchedule());
        existing.setStartDateTime(assignment.getStartDateTime());
        existing.setEndDateTime(assignment.getEndDateTime());
        existing.setStatus(assignment.getStatus());

        return assignmentRepository.save(existing);
    }

    @Override
    public void delete(Long id) {
        Assignment assignment = getById(id);
        assignmentRepository.delete(assignment);
    }

    private void checkUser(Assignment assignment) {
        Long userId = assignment.getUser().getId();

        if (userId == null) {
            throw new EntityNotFoundException("User not found: null");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "User not found: " + userId
                        )
                );

        if (!user.isEnabled()) {
            throw new BusinessRuleException(
                    "User is not enabled: " + userId
            );
        }
    }

    private void checkTask(Assignment assignment) {
        Long taskId = assignment.getTask().getId();

        if (taskId == null) {
            throw new EntityNotFoundException("Task not found: null");
        }

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Task not found: " + taskId
                        )
                );

        if (task.getStatus() == TaskStatus.COMPLETED
                || task.getStatus() == TaskStatus.CANCELLED) {
            throw new BusinessRuleException(
                    "Task must not be completed or cancelled: " + taskId
            );
        }
    }

    private void checkSchedule(Assignment assignment) {
        Long scheduleId = assignment.getSchedule().getId();

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

        if (assignment.getStartDateTime()
                .isBefore(schedule.getStartDateTime())
                || assignment.getEndDateTime()
                .isAfter(schedule.getEndDateTime())) {
            throw new BusinessRuleException(
                    "Assignment must be contained within the schedule period"
            );
        }
    }

    private void checkAvailability(Assignment assignment) {
        Long userId = assignment.getUser().getId();
        LocalDateTime start = assignment.getStartDateTime();
        LocalDateTime end = assignment.getEndDateTime();

        boolean covered = availabilityRepository
                .existsByUserIdAndStartDateTimeLessThanEqualAndEndDateTimeGreaterThanEqual(
                        userId,
                        start,
                        end
                );

        if (!covered) {
            throw new BusinessRuleException(
                    "Assignment must be covered by user availability"
            );
        }
    }

    private void checkUnavailability(Assignment assignment) {
        Long userId = assignment.getUser().getId();
        LocalDateTime start = assignment.getStartDateTime();
        LocalDateTime end = assignment.getEndDateTime();

        boolean overlaps = unavailabilityRepository
                .existsByUserIdAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
                        userId,
                        end,
                        start
                );

        if (overlaps) {
            throw new BusinessRuleException(
                    "Assignment overlaps an existing unavailability"
            );
        }
    }

    private void checkConflict(Assignment assignment, Long excludedId) {
        Long userId = assignment.getUser().getId();
        LocalDateTime start = assignment.getStartDateTime();
        LocalDateTime end = assignment.getEndDateTime();

        boolean overlaps = (excludedId == null)
                ? assignmentRepository
                        .existsByUserIdAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
                                userId,
                                end,
                                start
                        )
                : assignmentRepository
                        .existsByUserIdAndStartDateTimeLessThanAndEndDateTimeGreaterThanAndIdNot(
                                userId,
                                end,
                                start,
                                excludedId
                        );

        if (overlaps) {
            throw new BusinessRuleException(
                    "Assignment overlaps an existing assignment"
            );
        }
    }

    private void validate(Assignment assignment) {

        if (assignment == null) {
            throw new ValidationException(
                    "Assignment must not be null"
            );
        }

        if (assignment.getUser() == null) {
            throw new ValidationException(
                    "User must not be null"
            );
        }

        if (assignment.getTask() == null) {
            throw new ValidationException(
                    "Task must not be null"
            );
        }

        if (assignment.getSchedule() == null) {
            throw new ValidationException(
                    "Schedule must not be null"
            );
        }

        LocalDateTime start = assignment.getStartDateTime();
        LocalDateTime end = assignment.getEndDateTime();

        if (start == null) {
            throw new ValidationException(
                    "Start date/time must not be null"
            );
        }

        if (end == null) {
            throw new ValidationException(
                    "End date/time must not be null"
            );
        }

        if (!start.isBefore(end)) {
            throw new ValidationException(
                    "Start date/time must be before end date/time"
            );
        }

        if (assignment.getStatus() == null) {
            throw new ValidationException(
                    "Status must not be null"
            );
        }
    }
}