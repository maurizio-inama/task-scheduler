package com.taskscheduler.domain.repository;

import com.taskscheduler.domain.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {
}