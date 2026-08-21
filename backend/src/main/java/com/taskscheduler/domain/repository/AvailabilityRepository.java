package com.taskscheduler.domain.repository;

import com.taskscheduler.domain.entity.Availability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AvailabilityRepository extends JpaRepository<Availability, Long> {

    List<Availability> findByUserId(Long userId);

    boolean existsByUserIdAndStartDateTimeLessThanEqualAndEndDateTimeGreaterThanEqual(
            Long userId,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime
    );
}