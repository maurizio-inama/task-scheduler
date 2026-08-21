package com.taskscheduler.controller;

import com.taskscheduler.domain.entity.Schedule;
import com.taskscheduler.domain.entity.ScheduleStatus;
import com.taskscheduler.exception.BusinessRuleException;
import com.taskscheduler.exception.EntityNotFoundException;
import com.taskscheduler.service.ScheduleService;
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
}