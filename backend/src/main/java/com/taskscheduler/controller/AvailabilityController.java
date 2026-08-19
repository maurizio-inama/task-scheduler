package com.taskscheduler.controller;

import com.taskscheduler.controller.dto.AvailabilityResponse;
import com.taskscheduler.controller.dto.CreateAvailabilityRequest;
import com.taskscheduler.controller.dto.UpdateAvailabilityRequest;
import com.taskscheduler.service.AvailabilityService;
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
@RequestMapping("/api/availability")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AvailabilityResponse create(
            @Valid @RequestBody CreateAvailabilityRequest request
    ) {
        return AvailabilityResponse.from(
                availabilityService.create(request.toEntity())
        );
    }

    @GetMapping
    public List<AvailabilityResponse> getAll() {
        return availabilityService.getAll()
                .stream()
                .map(AvailabilityResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public AvailabilityResponse getById(@PathVariable Long id) {
        return AvailabilityResponse.from(availabilityService.getById(id));
    }

    @PutMapping("/{id}")
    public AvailabilityResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAvailabilityRequest request
    ) {
        return AvailabilityResponse.from(
                availabilityService.update(id, request.toEntity())
        );
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        availabilityService.delete(id);
    }
}