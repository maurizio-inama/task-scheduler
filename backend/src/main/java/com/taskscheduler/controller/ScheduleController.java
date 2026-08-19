package com.taskscheduler.controller;

import com.taskscheduler.controller.dto.CreateScheduleRequest;
import com.taskscheduler.controller.dto.ScheduleResponse;
import com.taskscheduler.controller.dto.UpdateScheduleRequest;
import com.taskscheduler.service.ScheduleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleResponse create(
            @Valid @RequestBody CreateScheduleRequest request
    ) {
        return ScheduleResponse.from(
                scheduleService.create(request.toEntity())
        );
    }

    @GetMapping
    public List<ScheduleResponse> getAll() {
        return scheduleService.getAll()
                .stream()
                .map(ScheduleResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ScheduleResponse getById(@PathVariable Long id) {
        return ScheduleResponse.from(scheduleService.getById(id));
    }

    @PutMapping("/{id}")
    public ScheduleResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateScheduleRequest request
    ) {
        return ScheduleResponse.from(
                scheduleService.update(id, request.toEntity())
        );
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        scheduleService.delete(id);
    }
}