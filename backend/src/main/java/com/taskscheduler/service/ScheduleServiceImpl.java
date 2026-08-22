package com.taskscheduler.service;

import com.taskscheduler.domain.entity.Assignment;
import com.taskscheduler.domain.entity.Schedule;
import com.taskscheduler.domain.entity.ScheduleStatus;
import com.taskscheduler.domain.entity.Task;
import com.taskscheduler.domain.entity.TaskStatus;
import com.taskscheduler.domain.repository.AssignmentRepository;
import com.taskscheduler.domain.repository.ScheduleRepository;
import com.taskscheduler.domain.repository.TaskRepository;
import com.taskscheduler.exception.BusinessRuleException;
import com.taskscheduler.exception.EntityNotFoundException;
import com.taskscheduler.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ScheduleServiceImpl implements ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final AssignmentRepository assignmentRepository;
    private final TaskRepository taskRepository;

    public ScheduleServiceImpl(
            ScheduleRepository scheduleRepository,
            AssignmentRepository assignmentRepository,
            TaskRepository taskRepository
    ) {
        this.scheduleRepository = scheduleRepository;
        this.assignmentRepository = assignmentRepository;
        this.taskRepository = taskRepository;
    }

    @Override
    public Schedule create(Schedule schedule) {
        validate(schedule);

        return scheduleRepository.save(schedule);
    }

    @Override
    public Schedule getById(Long id) {
        return scheduleRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Schedule not found: " + id
                        )
                );
    }

    @Override
    public List<Schedule> getAll() {
        return scheduleRepository.findAll();
    }

    @Override
    public Schedule update(Long id, Schedule schedule) {
        Schedule existing = getById(id);

        validate(schedule);

        validateStatusTransition(
                existing.getStatus(),
                schedule.getStatus()
        );

        existing.setStartDateTime(schedule.getStartDateTime());
        existing.setEndDateTime(schedule.getEndDateTime());
        existing.setStatus(schedule.getStatus());

        return scheduleRepository.save(existing);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Schedule schedule = getById(id);

        List<Assignment> assignments =
                assignmentRepository.findByScheduleId(id);

        assignments.stream()
                .map(Assignment::getTask)
                .filter(task -> task.getStatus() == TaskStatus.SCHEDULED)
                .distinct()
                .forEach(task -> {
                    task.setStatus(TaskStatus.PENDING);
                    taskRepository.save(task);
                });

        assignmentRepository.deleteAll(assignments);
        scheduleRepository.delete(schedule);
    }

    private void validate(Schedule schedule) {

        if (schedule == null) {
            throw new ValidationException(
                    "Schedule must not be null"
            );
        }

        if (schedule.getStartDateTime() == null) {
            throw new ValidationException(
                    "Start date/time must not be null"
            );
        }

        if (schedule.getEndDateTime() == null) {
            throw new ValidationException(
                    "End date/time must not be null"
            );
        }

        if (schedule.getStatus() == null) {
            throw new ValidationException(
                    "Status must not be null"
            );
        }

        if (!schedule.getStartDateTime()
                .isBefore(schedule.getEndDateTime())) {

            throw new ValidationException(
                    "Start date/time must be before end date/time"
            );
        }
    }

    private void validateStatusTransition(
            ScheduleStatus current,
            ScheduleStatus requested
    ) {
        if (current == requested) {
            return;
        }

        boolean valid = switch (current) {
            case DRAFT ->
                    requested == ScheduleStatus.PUBLISHED
                            || requested == ScheduleStatus.CANCELLED;

            case PUBLISHED ->
                    requested == ScheduleStatus.COMPLETED
                            || requested == ScheduleStatus.CANCELLED;

            case COMPLETED, CANCELLED ->
                    false;
        };

        if (!valid) {
            throw new BusinessRuleException(
                    "Invalid schedule status transition: "
                            + current + " -> " + requested
            );
        }
    }
}