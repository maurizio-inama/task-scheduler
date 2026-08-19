package com.taskscheduler.service;

import com.taskscheduler.domain.entity.Task;
import com.taskscheduler.domain.entity.TaskStatus;
import com.taskscheduler.domain.repository.TaskRepository;
import com.taskscheduler.exception.BusinessRuleException;
import com.taskscheduler.exception.EntityNotFoundException;
import com.taskscheduler.exception.ValidationException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;

    public TaskServiceImpl(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    public Task create(Task task) {
        validate(task);

        return taskRepository.save(task);
    }

    @Override
    public Task getById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException("Task not found: " + id)
                );
    }

    @Override
    public List<Task> getAll() {
        return taskRepository.findAll();
    }

    @Override
    public Task update(Long id, Task task) {
        Task existing = getById(id);

        validate(task);
        validateStatusTransition(
                existing.getStatus(),
                task.getStatus()
        );

        existing.setTitle(task.getTitle());
        existing.setDescription(task.getDescription());
        existing.setStatus(task.getStatus());
        existing.setPriority(task.getPriority());
        existing.setEstimatedDurationMinutes(
                task.getEstimatedDurationMinutes()
        );
        existing.setDeadline(task.getDeadline());

        return taskRepository.save(existing);
    }

    @Override
    public void delete(Long id) {
        Task task = getById(id);
        taskRepository.delete(task);
    }

    private void validate(Task task) {

        if (task == null) {
            throw new ValidationException("Task must not be null");
        }

        if (isBlank(task.getTitle())) {
            throw new ValidationException("Title must not be blank");
        }

        if (task.getStatus() == null) {
            throw new ValidationException("Status must not be null");
        }

        if (task.getPriority() == null) {
            throw new ValidationException("Priority must not be null");
        }

        if (task.getEstimatedDurationMinutes() == null) {
            throw new ValidationException(
                    "Estimated duration must not be null"
            );
        }

        if (task.getEstimatedDurationMinutes() <= 0) {
            throw new ValidationException(
                    "Estimated duration must be greater than zero"
            );
        }
    }

    private void validateStatusTransition(
            TaskStatus current,
            TaskStatus requested
    ) {
        if (current == requested) {
            return;
        }

        boolean valid = switch (current) {
            case PENDING ->
                    requested == TaskStatus.SCHEDULED
                            || requested == TaskStatus.CANCELLED;

            case SCHEDULED ->
                    requested == TaskStatus.IN_PROGRESS
                            || requested == TaskStatus.CANCELLED;

            case IN_PROGRESS ->
                    requested == TaskStatus.COMPLETED;

            case COMPLETED, CANCELLED ->
                    false;
        };

        if (!valid) {
            throw new BusinessRuleException(
                    "Invalid task status transition: "
                            + current + " -> " + requested
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
