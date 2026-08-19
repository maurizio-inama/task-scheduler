package com.taskscheduler.domain.repository;

import com.taskscheduler.domain.entity.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

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