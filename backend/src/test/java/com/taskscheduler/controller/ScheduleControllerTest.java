package com.taskscheduler.controller;

import com.taskscheduler.domain.entity.Schedule;
import com.taskscheduler.domain.entity.ScheduleStatus;
import com.taskscheduler.exception.BusinessRuleException;
import com.taskscheduler.exception.EntityNotFoundException;
import com.taskscheduler.service.AssignmentService;
import com.taskscheduler.service.ScheduleService;
import com.taskscheduler.service.SchedulingService;
import com.taskscheduler.scheduling.model.Allocation;
import com.taskscheduler.scheduling.model.SchedulingFailureReason;
import com.taskscheduler.scheduling.model.SchedulingResult;
import com.taskscheduler.scheduling.model.TaskSchedule;
import com.taskscheduler.scheduling.model.UnscheduledTask;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
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

@WebMvcTest(ScheduleController.class)
@AutoConfigureMockMvc(addFilters = false)
class ScheduleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScheduleService scheduleService;

    @MockitoBean
    private SchedulingService schedulingService;

    @MockitoBean
    private AssignmentService assignmentService;

    private Schedule schedule(Long id) {
        Schedule schedule = new Schedule(id);
        schedule.setStartDateTime(LocalDateTime.of(2026, 8, 19, 8, 0));
        schedule.setEndDateTime(LocalDateTime.of(2026, 8, 19, 18, 0));
        schedule.setStatus(ScheduleStatus.DRAFT);
        return schedule;
    }

    @Test
    void shouldCreateSchedule() throws Exception {
        when(scheduleService.create(any(Schedule.class))).thenReturn(schedule(1L));

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDateTime": "2026-08-19T08:00:00",
                                  "endDateTime": "2026-08-19T18:00:00",
                                  "status": "DRAFT"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.startDateTime").value("2026-08-19T08:00:00"))
                .andExpect(jsonPath("$.endDateTime").value("2026-08-19T18:00:00"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void shouldGetAllSchedules() throws Exception {
        when(scheduleService.getAll()).thenReturn(List.of(schedule(1L), schedule(2L)));

        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("DRAFT"));
    }

    @Test
    void shouldGetScheduleById() throws Exception {
        when(scheduleService.getById(1L)).thenReturn(schedule(1L));

        mockMvc.perform(get("/api/schedules/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void shouldReturnNotFoundWhenScheduleMissing() throws Exception {
        when(scheduleService.getById(99L))
                .thenThrow(new EntityNotFoundException("Schedule not found: 99"));

        mockMvc.perform(get("/api/schedules/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("ENTITY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Schedule not found: 99"))
                .andExpect(jsonPath("$.path").value("/api/schedules/99"));
    }

    @Test
    void shouldUpdateSchedule() throws Exception {
        when(scheduleService.update(eq(1L), any(Schedule.class)))
                .thenReturn(schedule(1L));

        mockMvc.perform(put("/api/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDateTime": "2026-08-19T08:00:00",
                                  "endDateTime": "2026-08-19T18:00:00",
                                  "status": "PUBLISHED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void shouldDeleteSchedule() throws Exception {
        mockMvc.perform(delete("/api/schedules/1"))
                .andExpect(status().isNoContent());

        verify(scheduleService).delete(1L);
    }

    @Test
    void shouldRejectMissingStatus() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDateTime": "2026-08-19T08:00:00",
                                  "endDateTime": "2026-08-19T18:00:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("status: must not be null"));
    }

    @Test
    void shouldRejectMalformedRequestBody() throws Exception {
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": [1,2]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void shouldReturnConflictOnInvalidTransition() throws Exception {
        when(scheduleService.create(any(Schedule.class)))
                .thenThrow(new BusinessRuleException(
                        "Invalid schedule status transition: DRAFT -> COMPLETED"
                ));

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDateTime": "2026-08-19T08:00:00",
                                  "endDateTime": "2026-08-19T18:00:00",
                                  "status": "DRAFT"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value(
                        "Invalid schedule status transition: DRAFT -> COMPLETED"
                ));
    }

    @Test
    void shouldGenerateAssignmentsForSchedule() throws Exception {
        Schedule existing = schedule(7L);
        when(scheduleService.getById(7L)).thenReturn(existing);

        SchedulingResult result = new SchedulingResult(
                List.of(new TaskSchedule(25L, 3L, List.of(
                        new Allocation(
                                LocalDateTime.of(2026, 8, 19, 8, 0),
                                LocalDateTime.of(2026, 8, 19, 10, 0)
                        )
                ))),
                List.of(new UnscheduledTask(
                        26L,
                        SchedulingFailureReason.NO_ELIGIBLE_USER,
                        "No eligible user has capacity"
                ))
        );
        when(schedulingService.generate(7L)).thenReturn(result);
        when(assignmentService.getByScheduleId(7L)).thenReturn(List.of());

        mockMvc.perform(post("/api/schedules/7/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleId").value(7))
                .andExpect(jsonPath("$.scheduledTaskCount").value(1))
                .andExpect(jsonPath("$.createdAssignmentCount").value(0))
                .andExpect(jsonPath("$.unscheduledTasks.length()").value(1))
                .andExpect(jsonPath("$.unscheduledTasks[0].taskId").value(26))
                .andExpect(jsonPath("$.unscheduledTasks[0].reason")
                        .value("NO_ELIGIBLE_USER"));
    }

    @Test
    void shouldReturnNotFoundWhenGeneratingForMissingSchedule() throws Exception {
        when(scheduleService.getById(99L))
                .thenThrow(new EntityNotFoundException("Schedule not found: 99"));

        mockMvc.perform(post("/api/schedules/99/generate"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ENTITY_NOT_FOUND"));
    }
}