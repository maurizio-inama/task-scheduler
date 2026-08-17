package com.taskscheduler.domain.repository;

import com.taskscheduler.domain.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
}