package com.taskscheduler.service;

import com.taskscheduler.scheduling.model.SchedulingResult;

public interface SchedulingService {

    SchedulingResult generate(Long scheduleId);
}
