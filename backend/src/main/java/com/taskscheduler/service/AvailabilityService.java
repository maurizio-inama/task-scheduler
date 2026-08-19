package com.taskscheduler.service;

import com.taskscheduler.domain.entity.Availability;

import java.util.List;

public interface AvailabilityService {

    Availability create(Availability availability);

    Availability getById(Long id);

    List<Availability> getAll();

    Availability update(Long id, Availability availability);

    void delete(Long id);
}