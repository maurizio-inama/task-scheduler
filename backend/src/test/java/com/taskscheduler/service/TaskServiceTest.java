package com.taskscheduler.service;

import com.taskscheduler.domain.entity.Task;
import com.taskscheduler.domain.entity.TaskPriority;
import com.taskscheduler.domain.entity.TaskStatus;
import com.taskscheduler.domain.repository.AssignmentRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private AssignmentRepository assignmentRepository;

    @InjectMocks
    private TaskServiceImpl taskService;

    private Task task;

    @BeforeEach
    void setUp() {
        task = new Task(
                "Test task",
                "Test description",
                TaskStatus.PENDING,
                TaskPriority.MEDIUM,
                120,
                LocalDateTime.of(2026, 8, 30, 17, 0)
        );
    }

    @Test
    void shouldCreateTask() {
        when(taskRepository.save(task)).thenReturn(task);

        Task result = taskService.create(task);

        assertSame(task, result);

        verify(taskRepository).save(task);
    }

    @Test
    void shouldRejectNullTask() {
        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> taskService.create(null)
        );

        assertEquals(
                "Task must not be null",
                exception.getMessage()
        );

        verifyNoInteractions(taskRepository);
    }

    @Test
    void shouldRejectBlankTitle() {
        task.setTitle("   ");

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> taskService.create(task)
        );

        assertEquals(
                "Title must not be blank",
                exception.getMessage()
        );

        verifyNoInteractions(taskRepository);
    }

    @Test
    void shouldRejectNullStatus() {
        task.setStatus(null);

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> taskService.create(task)
        );

        assertEquals(
                "Status must not be null",
                exception.getMessage()
        );

        verifyNoInteractions(taskRepository);
    }

    @Test
    void shouldRejectNullPriority() {
        task.setPriority(null);

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> taskService.create(task)
        );

        assertEquals(
                "Priority must not be null",
                exception.getMessage()
        );

        verifyNoInteractions(taskRepository);
    }

    @Test
    void shouldRejectNullEstimatedDuration() {
        task.setEstimatedDurationMinutes(null);

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> taskService.create(task)
        );

        assertEquals(
                "Estimated duration must not be null",
                exception.getMessage()
        );

        verifyNoInteractions(taskRepository);
    }

    @Test
    void shouldRejectNonPositiveEstimatedDuration() {
        task.setEstimatedDurationMinutes(0);

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> taskService.create(task)
        );

        assertEquals(
                "Estimated duration must be greater than zero",
                exception.getMessage()
        );

        verifyNoInteractions(taskRepository);
    }

    @Test
    void shouldGetTaskById() {
        when(taskRepository.findById(1L))
                .thenReturn(Optional.of(task));

        Task result = taskService.getById(1L);

        assertSame(task, result);
    }

    @Test
    void shouldThrowWhenTaskDoesNotExist() {
        when(taskRepository.findById(1L))
                .thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(
                EntityNotFoundException.class,
                () -> taskService.getById(1L)
        );

        assertEquals(
                "Task not found: 1",
                exception.getMessage()
        );
    }

    @Test
    void shouldGetAllTasks() {
        List<Task> tasks = List.of(task);

        when(taskRepository.findAll())
                .thenReturn(tasks);

        List<Task> result = taskService.getAll();

        assertSame(tasks, result);
    }

    @Test
    void shouldUpdateTaskWithoutChangingStatus() {
        Task existing = new Task(
                "Old title",
                "Old description",
                TaskStatus.PENDING,
                TaskPriority.LOW,
                60,
                null
        );

        when(taskRepository.findById(1L))
                .thenReturn(Optional.of(existing));

        when(taskRepository.save(existing))
                .thenReturn(existing);

        task.setTitle("Updated title");
        task.setPriority(TaskPriority.HIGH);

        Task result = taskService.update(1L, task);

        assertEquals("Updated title", result.getTitle());
        assertEquals(TaskPriority.HIGH, result.getPriority());

        verify(taskRepository).save(existing);
    }

    @Test
    void shouldUpdateTaskWithValidStatusTransition() {
        Task existing = new Task(
                "Test task",
                "Description",
                TaskStatus.PENDING,
                TaskPriority.MEDIUM,
                120,
                null
        );

        when(taskRepository.findById(1L))
                .thenReturn(Optional.of(existing));

        when(taskRepository.save(existing))
                .thenReturn(existing);

        task.setStatus(TaskStatus.SCHEDULED);

        Task result = taskService.update(1L, task);

        assertEquals(
                TaskStatus.SCHEDULED,
                result.getStatus()
        );

        verify(taskRepository).save(existing);
    }

    @Test
    void shouldRejectInvalidStatusTransition() {
        Task existing = new Task(
                "Test task",
                "Description",
                TaskStatus.PENDING,
                TaskPriority.MEDIUM,
                120,
                null
        );

        when(taskRepository.findById(1L))
                .thenReturn(Optional.of(existing));

        task.setStatus(TaskStatus.COMPLETED);

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> taskService.update(1L, task)
        );

        assertEquals(
                "Invalid task status transition: PENDING -> COMPLETED",
                exception.getMessage()
        );

        verify(taskRepository, never()).save(any());
    }

    @Test
    void shouldDeleteTask() {
        when(taskRepository.findById(1L))
                .thenReturn(Optional.of(task));

        taskService.delete(1L);

        verify(taskRepository).delete(task);
    }

    @Test
    void shouldRejectDeleteWhenTaskDoesNotExist() {
        when(taskRepository.findById(1L))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> taskService.delete(1L)
        );

        verify(taskRepository, never()).delete(any());
    }

    @Test
    void shouldRejectDeleteWhenTaskHasAssignments() {
        when(taskRepository.findById(1L))
                .thenReturn(Optional.of(task));
        when(assignmentRepository.existsByTaskId(1L)).thenReturn(true);

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> taskService.delete(1L)
        );

        assertThat(exception.getMessage()).contains("cannot be deleted");
        verify(taskRepository, never()).delete(any());
    }
}