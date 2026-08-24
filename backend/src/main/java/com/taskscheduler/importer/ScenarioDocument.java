package com.taskscheduler.importer;

import java.time.LocalDateTime;
import java.util.List;

/**
 * JSON document describing a complete demo scenario for the admin data
 * import feature.
 *
 * <p>Entities reference each other through natural business keys (username
 * for users) instead of database-generated ids. Imported tasks always start
 * in {@code PENDING} state; schedules are created and filled by the real
 * scheduling engine ({@code generate}), never by hand-written assignments.</p>
 *
 * <p>Roles and priorities are kept as raw strings so that invalid enum values
 * can be reported as precise validation problems instead of parse failures.</p>
 */
public record ScenarioDocument(
        ScenarioMeta scenario,
        List<UserEntry> users,
        List<TaskEntry> tasks,
        List<AvailabilityEntry> availabilities,
        List<UnavailabilityEntry> unavailabilities,
        List<ScheduleEntry> schedules
) {

    public record ScenarioMeta(
            String id,
            String name,
            String description
    ) {
    }

    public record UserEntry(
            String username,
            String password,
            String firstName,
            String lastName,
            String email,
            String role,
            Boolean enabled
    ) {
    }

    public record TaskEntry(
            String title,
            String description,
            String priority,
            Integer estimatedDurationMinutes,
            LocalDateTime deadline
    ) {
    }

    public record AvailabilityEntry(
            String username,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime
    ) {
    }

    public record UnavailabilityEntry(
            String username,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            String reason
    ) {
    }

    public record ScheduleEntry(
            LocalDateTime startDateTime,
            LocalDateTime endDateTime
    ) {
    }

    public List<UserEntry> safeUsers() {
        return users == null ? List.of() : users;
    }

    public List<TaskEntry> safeTasks() {
        return tasks == null ? List.of() : tasks;
    }

    public List<AvailabilityEntry> safeAvailabilities() {
        return availabilities == null ? List.of() : availabilities;
    }

    public List<UnavailabilityEntry> safeUnavailabilities() {
        return unavailabilities == null ? List.of() : unavailabilities;
    }

    public List<ScheduleEntry> safeSchedules() {
        return schedules == null ? List.of() : schedules;
    }
}
