package com.taskscheduler.scheduling;

import com.taskscheduler.scheduling.model.SchedulingRequest;
import com.taskscheduler.scheduling.model.SchedulingResult;

public interface SchedulingEngine {

    SchedulingResult schedule(SchedulingRequest request);
}
