package com.taskscheduler.domain.repository;

import com.taskscheduler.domain.entity.Assignment;
import com.taskscheduler.domain.entity.AssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    List<Assignment> findByStatusNot(AssignmentStatus status);

    List<Assignment> findByScheduleId(Long scheduleId);

    boolean existsByTaskId(Long taskId);

    boolean existsByUserId(Long userId);

    boolean existsByTaskIdAndStatusNot(Long taskId, AssignmentStatus status);

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