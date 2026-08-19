package com.taskscheduler.controller;

import com.taskscheduler.controller.dto.CreateUnavailabilityRequest;
import com.taskscheduler.controller.dto.UnavailabilityResponse;
import com.taskscheduler.controller.dto.UpdateUnavailabilityRequest;
import com.taskscheduler.service.UnavailabilityService;
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
@RequestMapping("/api/unavailability")
public class UnavailabilityController {

    private final UnavailabilityService unavailabilityService;

    public UnavailabilityController(UnavailabilityService unavailabilityService) {
        this.unavailabilityService = unavailabilityService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UnavailabilityResponse create(
            @Valid @RequestBody CreateUnavailabilityRequest request
    ) {
        return UnavailabilityResponse.from(
                unavailabilityService.create(request.toEntity())
        );
    }

    @GetMapping
    public List<UnavailabilityResponse> getAll() {
        return unavailabilityService.getAll()
                .stream()
                .map(UnavailabilityResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public UnavailabilityResponse getById(@PathVariable Long id) {
        return UnavailabilityResponse.from(
                unavailabilityService.getById(id)
        );
    }

    @PutMapping("/{id}")
    public UnavailabilityResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUnavailabilityRequest request
    ) {
        return UnavailabilityResponse.from(
                unavailabilityService.update(id, request.toEntity())
        );
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        unavailabilityService.delete(id);
    }
}