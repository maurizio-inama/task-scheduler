package com.taskscheduler.controller;

import com.taskscheduler.domain.entity.Task;
import com.taskscheduler.domain.entity.TaskPriority;
import com.taskscheduler.domain.entity.TaskStatus;
import com.taskscheduler.exception.BusinessRuleException;
import com.taskscheduler.exception.EntityNotFoundException;
import com.taskscheduler.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
@AutoConfigureMockMvc(addFilters = false)
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskService taskService;

    private Task task(Long id) {
        Task task = new Task(id);
        task.setTitle("Prepare monthly report");
        task.setDescription("Compile the numbers");
        task.setStatus(TaskStatus.PENDING);
        task.setPriority(TaskPriority.MEDIUM);
        task.setEstimatedDurationMinutes(60);
        return task;
    }

    @Test
    void shouldCreateTask() throws Exception {
        when(taskService.create(any(Task.class))).thenReturn(task(1L));

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Prepare monthly report",
                                  "description": "Compile the numbers",
                                  "status": "PENDING",
                                  "priority": "MEDIUM",
                                  "estimatedDurationMinutes": 60
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Prepare monthly report"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.estimatedDurationMinutes").value(60));
    }

    @Test
    void shouldGetAllTasks() throws Exception {
        when(taskService.getAll()).thenReturn(List.of(task(1L), task(2L)));

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("Prepare monthly report"));
    }

    @Test
    void shouldGetTaskById() throws Exception {
        when(taskService.getById(1L)).thenReturn(task(1L));

        mockMvc.perform(get("/api/tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Prepare monthly report"));
    }

    @Test
    void shouldReturnNotFoundWhenTaskMissing() throws Exception {
        when(taskService.getById(99L))
                .thenThrow(new EntityNotFoundException("Task not found: 99"));

        mockMvc.perform(get("/api/tasks/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("ENTITY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Task not found: 99"))
                .andExpect(jsonPath("$.path").value("/api/tasks/99"));
    }

    @Test
    void shouldUpdateTask() throws Exception {
        when(taskService.update(eq(1L), any(Task.class))).thenReturn(task(1L));

        mockMvc.perform(put("/api/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Prepare monthly report",
                                  "description": "Compile the numbers",
                                  "status": "SCHEDULED",
                                  "priority": "HIGH",
                                  "estimatedDurationMinutes": 90
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void shouldDeleteTask() throws Exception {
        mockMvc.perform(delete("/api/tasks/1"))
                .andExpect(status().isNoContent());

        verify(taskService).delete(1L);
    }

    @Test
    void shouldRejectBlankTitle() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "",
                                  "status": "PENDING",
                                  "priority": "MEDIUM",
                                  "estimatedDurationMinutes": 60
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("title: must not be blank"));
    }

    @Test
    void shouldRejectNonPositiveDuration() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Prepare monthly report",
                                  "status": "PENDING",
                                  "priority": "MEDIUM",
                                  "estimatedDurationMinutes": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(
                        "estimatedDurationMinutes: must be greater than 0"
                ));
    }

    @Test
    void shouldRejectMalformedRequestBody() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void shouldReturnConflictOnBusinessRuleViolation() throws Exception {
        when(taskService.create(any(Task.class)))
                .thenThrow(new BusinessRuleException(
                        "Invalid task status transition: COMPLETED -> PENDING"
                ));

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Prepare monthly report",
                                  "status": "PENDING",
                                  "priority": "MEDIUM",
                                  "estimatedDurationMinutes": 60
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value(
                        "Invalid task status transition: COMPLETED -> PENDING"
                ));
    }
}