package com.taskscheduler.service;

import com.taskscheduler.domain.entity.Unavailability;

import java.util.List;

public interface UnavailabilityService {

    Unavailability create(Unavailability unavailability);

    Unavailability getById(Long id);

    List<Unavailability> getAll();

    Unavailability update(Long id, Unavailability unavailability);

    void delete(Long id);
}
