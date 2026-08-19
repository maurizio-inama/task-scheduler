package com.taskscheduler.controller;

import com.taskscheduler.domain.entity.Unavailability;
import com.taskscheduler.domain.entity.User;
import com.taskscheduler.exception.BusinessRuleException;
import com.taskscheduler.exception.EntityNotFoundException;
import com.taskscheduler.service.UnavailabilityService;
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

@WebMvcTest(UnavailabilityController.class)
class UnavailabilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UnavailabilityService unavailabilityService;

    private Unavailability unavailability(Long id) {
        Unavailability unavailability = mock(Unavailability.class);
        when(unavailability.getId()).thenReturn(id);
        when(unavailability.getUser()).thenReturn(new User(1L));
        when(unavailability.getStartDateTime())
                .thenReturn(LocalDateTime.of(2026, 8, 19, 13, 0));
        when(unavailability.getEndDateTime())
                .thenReturn(LocalDateTime.of(2026, 8, 19, 14, 0));
        when(unavailability.getReason()).thenReturn("Personal");
        return unavailability;
    }

    @Test
    void shouldCreateUnavailability() throws Exception {
        Unavailability created = unavailability(1L);
        when(unavailabilityService.create(any(Unavailability.class)))
                .thenReturn(created);

        mockMvc.perform(post("/api/unavailability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 1,
                                  "startDateTime": "2026-08-19T13:00:00",
                                  "endDateTime": "2026-08-19T14:00:00",
                                  "reason": "Personal"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.startDateTime").value("2026-08-19T13:00:00"))
                .andExpect(jsonPath("$.endDateTime").value("2026-08-19T14:00:00"))
                .andExpect(jsonPath("$.reason").value("Personal"));
    }

    @Test
    void shouldGetAllUnavailability() throws Exception {
        Unavailability first = unavailability(1L);
        Unavailability second = unavailability(2L);
        when(unavailabilityService.getAll())
                .thenReturn(List.of(first, second));

        mockMvc.perform(get("/api/unavailability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userId").value(1));
    }

    @Test
    void shouldGetUnavailabilityById() throws Exception {
        Unavailability found = unavailability(1L);
        when(unavailabilityService.getById(1L)).thenReturn(found);

        mockMvc.perform(get("/api/unavailability/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.reason").value("Personal"));
    }

    @Test
    void shouldReturnNotFoundWhenUnavailabilityMissing() throws Exception {
        when(unavailabilityService.getById(99L))
                .thenThrow(new EntityNotFoundException("Unavailability not found: 99"));

        mockMvc.perform(get("/api/unavailability/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("ENTITY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Unavailability not found: 99"))
                .andExpect(jsonPath("$.path").value("/api/unavailability/99"));
    }

    @Test
    void shouldUpdateUnavailability() throws Exception {
        Unavailability updated = unavailability(1L);
        when(unavailabilityService.update(eq(1L), any(Unavailability.class)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/unavailability/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 1,
                                  "startDateTime": "2026-08-19T15:00:00",
                                  "endDateTime": "2026-08-19T16:00:00",
                                  "reason": "Doctor"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.reason").value("Personal"));
    }

    @Test
    void shouldDeleteUnavailability() throws Exception {
        mockMvc.perform(delete("/api/unavailability/1"))
                .andExpect(status().isNoContent());

        verify(unavailabilityService).delete(1L);
    }

    @Test
    void shouldRejectMissingStartDateTime() throws Exception {
        mockMvc.perform(post("/api/unavailability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 1,
                                  "endDateTime": "2026-08-19T14:00:00",
                                  "reason": "Personal"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("startDateTime: must not be null"));
    }

    @Test
    void shouldRejectMalformedDate() throws Exception {
        mockMvc.perform(post("/api/unavailability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 1,
                                  "startDateTime": "2026-08-19T13:00:00",
                                  "endDateTime": "garbage"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void shouldReturnConflictOnOverlap() throws Exception {
        when(unavailabilityService.create(any(Unavailability.class)))
                .thenThrow(new BusinessRuleException(
                        "Unavailability overlaps an existing unavailability"
                ));

        mockMvc.perform(post("/api/unavailability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 1,
                                  "startDateTime": "2026-08-19T13:00:00",
                                  "endDateTime": "2026-08-19T14:00:00",
                                  "reason": "Personal"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value(
                        "Unavailability overlaps an existing unavailability"
                ));
    }
}