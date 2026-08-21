package com.taskscheduler.domain.repository;

import com.taskscheduler.domain.entity.Task;
import com.taskscheduler.domain.entity.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByStatus(TaskStatus status);
}