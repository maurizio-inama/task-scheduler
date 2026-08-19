package com.taskscheduler.controller;

import com.taskscheduler.domain.entity.Assignment;
import com.taskscheduler.domain.entity.AssignmentStatus;
import com.taskscheduler.domain.entity.Schedule;
import com.taskscheduler.domain.entity.Task;
import com.taskscheduler.domain.entity.User;
import com.taskscheduler.exception.BusinessRuleException;
import com.taskscheduler.exception.EntityNotFoundException;
import com.taskscheduler.service.AssignmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AssignmentController.class)
class AssignmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AssignmentService assignmentService;

    private Assignment assignment(Long id) {
        Assignment assignment = mock(Assignment.class);
        when(assignment.getId()).thenReturn(id);
        when(assignment.getUser()).thenReturn(new User(1L));
        when(assignment.getTask()).thenReturn(new Task(2L));
        when(assignment.getSchedule()).thenReturn(new Schedule(3L));
        when(assignment.getStartDateTime())
                .thenReturn(LocalDateTime.of(2026, 8, 19, 10, 0));
        when(assignment.getEndDateTime())
                .thenReturn(LocalDateTime.of(2026, 8, 19, 12, 0));
        when(assignment.getStatus()).thenReturn(AssignmentStatus.ASSIGNED);
        return assignment;
    }

    @Test
    void shouldCreateAssignment() throws Exception {
        Assignment created = assignment(1L);
        when(assignmentService.create(any(Assignment.class))).thenReturn(created);

        mockMvc.perform(post("/api/assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 1,
                                  "taskId": 2,
                                  "scheduleId": 3,
                                  "startDateTime": "2026-08-19T10:00:00",
                                  "endDateTime": "2026-08-19T12:00:00",
                                  "status": "ASSIGNED"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.taskId").value(2))
                .andExpect(jsonPath("$.scheduleId").value(3))
                .andExpect(jsonPath("$.startDateTime").value("2026-08-19T10:00:00"))
                .andExpect(jsonPath("$.endDateTime").value("2026-08-19T12:00:00"))
                .andExpect(jsonPath("$.status").value("ASSIGNED"));
    }

    @Test
    void shouldGetAllAssignments() throws Exception {
        Assignment first = assignment(1L);
        Assignment second = assignment(2L);
        when(assignmentService.getAll())
                .thenReturn(List.of(first, second));

        mockMvc.perform(get("/api/assignments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].taskId").value(2));
    }

    @Test
    void shouldGetAssignmentById() throws Exception {
        Assignment found = assignment(1L);
        when(assignmentService.getById(1L)).thenReturn(found);

        mockMvc.perform(get("/api/assignments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("ASSIGNED"));
    }

    @Test
    void shouldReturnNotFoundWhenAssignmentMissing() throws Exception {
        when(assignmentService.getById(99L))
                .thenThrow(new EntityNotFoundException("Assignment not found: 99"));

        mockMvc.perform(get("/api/assignments/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("ENTITY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Assignment not found: 99"))
                .andExpect(jsonPath("$.path").value("/api/assignments/99"));
    }

    @Test
    void shouldUpdateAssignment() throws Exception {
        Assignment updated = assignment(1L);
        when(assignmentService.update(eq(1L), any(Assignment.class)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/assignments/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 1,
                                  "taskId": 2,
                                  "scheduleId": 3,
                                  "startDateTime": "2026-08-19T13:00:00",
                                  "endDateTime": "2026-08-19T14:00:00",
                                  "status": "IN_PROGRESS"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("ASSIGNED"));
    }

    @Test
    void shouldDeleteAssignment() throws Exception {
        mockMvc.perform(delete("/api/assignments/1"))
                .andExpect(status().isNoContent());

        verify(assignmentService).delete(1L);
    }

    @Test
    void shouldRejectMissingTaskId() throws Exception {
        mockMvc.perform(post("/api/assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 1,
                                  "scheduleId": 3,
                                  "startDateTime": "2026-08-19T10:00:00",
                                  "endDateTime": "2026-08-19T12:00:00",
                                  "status": "ASSIGNED"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("taskId: must not be null"));
    }

    @Test
    void shouldRejectMissingStatus() throws Exception {
        mockMvc.perform(post("/api/assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 1,
                                  "taskId": 2,
                                  "scheduleId": 3,
                                  "startDateTime": "2026-08-19T10:00:00",
                                  "endDateTime": "2026-08-19T12:00:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("status: must not be null"));
    }

    @Test
    void shouldRejectMalformedDate() throws Exception {
        mockMvc.perform(post("/api/assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 1,
                                  "taskId": 2,
                                  "scheduleId": 3,
                                  "startDateTime": "2026-08-19T10:00:00",
                                  "endDateTime": "later",
                                  "status": "ASSIGNED"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void shouldReturnConflictOnOverlappingAssignment() throws Exception {
        when(assignmentService.create(any(Assignment.class)))
                .thenThrow(new BusinessRuleException(
                        "Assignment overlaps an existing assignment"
                ));

        mockMvc.perform(post("/api/assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 1,
                                  "taskId": 2,
                                  "scheduleId": 3,
                                  "startDateTime": "2026-08-19T10:00:00",
                                  "endDateTime": "2026-08-19T12:00:00",
                                  "status": "ASSIGNED"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value(
                        "Assignment overlaps an existing assignment"
                ));
    }

    @Test
    void shouldReturnUnprocessableEntityOnServiceValidation() throws Exception {
        when(assignmentService.create(any(Assignment.class)))
                .thenThrow(new com.taskscheduler.exception.ValidationException(
                        "Start date/time must be before end date/time"
                ));

        mockMvc.perform(post("/api/assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 1,
                                  "taskId": 2,
                                  "scheduleId": 3,
                                  "startDateTime": "2026-08-19T10:00:00",
                                  "endDateTime": "2026-08-19T12:00:00",
                                  "status": "ASSIGNED"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(
                        "Start date/time must be before end date/time"
                ));
    }
}