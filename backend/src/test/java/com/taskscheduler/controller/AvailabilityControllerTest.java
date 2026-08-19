package com.taskscheduler.controller;

import com.taskscheduler.domain.entity.Availability;
import com.taskscheduler.domain.entity.User;
import com.taskscheduler.exception.BusinessRuleException;
import com.taskscheduler.exception.EntityNotFoundException;
import com.taskscheduler.service.AvailabilityService;
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

@WebMvcTest(AvailabilityController.class)
class AvailabilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AvailabilityService availabilityService;

    private Availability availability(Long id) {
        Availability availability = mock(Availability.class);
        when(availability.getId()).thenReturn(id);
        when(availability.getUser()).thenReturn(new User(1L));
        when(availability.getStartDateTime())
                .thenReturn(LocalDateTime.of(2026, 8, 19, 9, 0));
        when(availability.getEndDateTime())
                .thenReturn(LocalDateTime.of(2026, 8, 19, 12, 0));
        return availability;
    }

    @Test
    void shouldCreateAvailability() throws Exception {
        Availability created = availability(1L);
        when(availabilityService.create(any(Availability.class)))
                .thenReturn(created);

        mockMvc.perform(post("/api/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 1,
                                  "startDateTime": "2026-08-19T09:00:00",
                                  "endDateTime": "2026-08-19T12:00:00"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.startDateTime").value("2026-08-19T09:00:00"))
                .andExpect(jsonPath("$.endDateTime").value("2026-08-19T12:00:00"));
    }

    @Test
    void shouldGetAllAvailability() throws Exception {
        Availability first = availability(1L);
        Availability second = availability(2L);
        when(availabilityService.getAll())
                .thenReturn(List.of(first, second));

        mockMvc.perform(get("/api/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userId").value(1));
    }

    @Test
    void shouldGetAvailabilityById() throws Exception {
        Availability found = availability(1L);
        when(availabilityService.getById(1L)).thenReturn(found);

        mockMvc.perform(get("/api/availability/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.startDateTime").value("2026-08-19T09:00:00"));
    }

    @Test
    void shouldReturnNotFoundWhenAvailabilityMissing() throws Exception {
        when(availabilityService.getById(99L))
                .thenThrow(new EntityNotFoundException("Availability not found: 99"));

        mockMvc.perform(get("/api/availability/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("ENTITY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Availability not found: 99"))
                .andExpect(jsonPath("$.path").value("/api/availability/99"));
    }

    @Test
    void shouldUpdateAvailability() throws Exception {
        Availability updated = availability(1L);
        when(availabilityService.update(eq(1L), any(Availability.class)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/availability/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 1,
                                  "startDateTime": "2026-08-19T14:00:00",
                                  "endDateTime": "2026-08-19T18:00:00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.endDateTime").value("2026-08-19T12:00:00"));
    }

    @Test
    void shouldDeleteAvailability() throws Exception {
        mockMvc.perform(delete("/api/availability/1"))
                .andExpect(status().isNoContent());

        verify(availabilityService).delete(1L);
    }

    @Test
    void shouldRejectMissingUserId() throws Exception {
        mockMvc.perform(post("/api/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDateTime": "2026-08-19T09:00:00",
                                  "endDateTime": "2026-08-19T12:00:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("userId: must not be null"));
    }

    @Test
    void shouldRejectMalformedDate() throws Exception {
        mockMvc.perform(post("/api/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 1,
                                  "startDateTime": "not-a-date",
                                  "endDateTime": "2026-08-19T12:00:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void shouldReturnConflictOnBusinessRuleViolation() throws Exception {
        when(availabilityService.create(any(Availability.class)))
                .thenThrow(new BusinessRuleException(
                        "Availability overlaps an existing unavailability"
                ));

        mockMvc.perform(post("/api/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 1,
                                  "startDateTime": "2026-08-19T09:00:00",
                                  "endDateTime": "2026-08-19T12:00:00"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value(
                        "Availability overlaps an existing unavailability"
                ));
    }
}