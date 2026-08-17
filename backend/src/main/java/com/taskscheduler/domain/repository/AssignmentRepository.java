package com.taskscheduler.domain.repository;

import com.taskscheduler.domain.entity.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
}