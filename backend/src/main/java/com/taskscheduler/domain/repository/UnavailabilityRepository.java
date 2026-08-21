package com.taskscheduler.domain.repository;

import com.taskscheduler.domain.entity.Unavailability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface UnavailabilityRepository extends JpaRepository<Unavailability, Long> {

    List<Unavailability> findByUserId(Long userId);

    boolean existsByUserIdAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
            Long userId,
            LocalDateTime endDateTime,
            LocalDateTime startDateTime
    );

    boolean existsByUserIdAndStartDateTimeLessThanAndEndDateTimeGreaterThanAndIdNot(
            Long userId,
            LocalDateTime endDateTime,
            LocalDateTime startDateTime,
            Long id
    );
}