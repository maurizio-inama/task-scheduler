package com.taskscheduler.service;

import com.taskscheduler.domain.entity.Schedule;

import java.util.List;

public interface ScheduleService {

    Schedule create(Schedule schedule);

    Schedule getById(Long id);

    List<Schedule> getAll();

    Schedule update(Long id, Schedule schedule);

    void delete(Long id);
}