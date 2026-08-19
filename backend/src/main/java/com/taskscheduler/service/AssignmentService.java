package com.taskscheduler.service;

import com.taskscheduler.domain.entity.Assignment;

import java.util.List;

public interface AssignmentService {

    Assignment create(Assignment assignment);

    Assignment getById(Long id);

    List<Assignment> getAll();

    Assignment update(Long id, Assignment assignment);

    void delete(Long id);
}