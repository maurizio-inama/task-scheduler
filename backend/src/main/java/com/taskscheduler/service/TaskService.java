package com.taskscheduler.service;

import com.taskscheduler.domain.entity.Task;

import java.util.List;

public interface TaskService {

    Task create(Task task);

    Task getById(Long id);

    List<Task> getAll();

    Task update(Long id, Task task);

    void delete(Long id);
}
