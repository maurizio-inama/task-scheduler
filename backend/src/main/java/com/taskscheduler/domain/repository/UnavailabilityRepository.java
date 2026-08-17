package com.taskscheduler.domain.repository;

import com.taskscheduler.domain.entity.Unavailability;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnavailabilityRepository extends JpaRepository<Unavailability, Long> {
}