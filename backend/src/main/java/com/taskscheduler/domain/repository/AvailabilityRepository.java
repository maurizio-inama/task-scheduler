package com.taskscheduler.domain.repository;

import com.taskscheduler.domain.entity.Availability;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailabilityRepository extends JpaRepository<Availability, Long> {
}