package com.taskscheduler.controller;

import com.taskscheduler.controller.dto.AssignmentResponse;
import com.taskscheduler.controller.dto.CreateAssignmentRequest;
import com.taskscheduler.controller.dto.UpdateAssignmentRequest;
import com.taskscheduler.service.AssignmentService;
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
@RequestMapping("/api/assignments")
public class AssignmentController {

    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AssignmentResponse create(
            @Valid @RequestBody CreateAssignmentRequest request
    ) {
        return AssignmentResponse.from(
                assignmentService.create(request.toEntity())
        );
    }

    @GetMapping
    public List<AssignmentResponse> getAll() {
        return assignmentService.getAll()
                .stream()
                .map(AssignmentResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public AssignmentResponse getById(@PathVariable Long id) {
        return AssignmentResponse.from(assignmentService.getById(id));
    }

    @PutMapping("/{id}")
    public AssignmentResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAssignmentRequest request
    ) {
        return AssignmentResponse.from(
                assignmentService.update(id, request.toEntity())
        );
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        assignmentService.delete(id);
    }
}