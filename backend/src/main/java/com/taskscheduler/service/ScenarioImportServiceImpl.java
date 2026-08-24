package com.taskscheduler.service;

import com.taskscheduler.controller.dto.ImportResult;
import com.taskscheduler.controller.dto.ValidationReport;
import com.taskscheduler.domain.entity.Availability;
import com.taskscheduler.domain.entity.Role;
import com.taskscheduler.domain.entity.Schedule;
import com.taskscheduler.domain.entity.ScheduleStatus;
import com.taskscheduler.domain.entity.Task;
import com.taskscheduler.domain.entity.TaskPriority;
import com.taskscheduler.domain.entity.TaskStatus;
import com.taskscheduler.domain.entity.Unavailability;
import com.taskscheduler.domain.entity.User;
import com.taskscheduler.domain.repository.UnavailabilityRepository;
import com.taskscheduler.domain.repository.UserRepository;
import com.taskscheduler.exception.BusinessRuleException;
import com.taskscheduler.exception.EntityNotFoundException;
import com.taskscheduler.exception.ValidationException;
import com.taskscheduler.importer.ImportRejectedException;
import com.taskscheduler.importer.ScenarioDocument;
import com.taskscheduler.scheduling.model.SchedulingResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Imports complete demo scenarios described as JSON documents.
 *
 * <p>The import reuses the existing domain services (UserService hashes
 * passwords and enforces uniqueness, UnavailabilityService enforces the
 * overlap rule, the scheduling engine fills imported schedules) and runs in
 * a single transaction: any rejection aborts every write.</p>
 */
@Service
public class ScenarioImportServiceImpl implements ScenarioImportService {

    private static final String EMAIL_PATTERN = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final UserRepository userRepository;
    private final TaskService taskService;
    private final AvailabilityService availabilityService;
    private final UnavailabilityService unavailabilityService;
    private final UnavailabilityRepository unavailabilityRepository;
    private final ScheduleService scheduleService;
    private final SchedulingService schedulingService;

    public ScenarioImportServiceImpl(
            ObjectMapper objectMapper,
            UserService userService,
            UserRepository userRepository,
            TaskService taskService,
            AvailabilityService availabilityService,
            UnavailabilityService unavailabilityService,
            UnavailabilityRepository unavailabilityRepository,
            ScheduleService scheduleService,
            SchedulingService schedulingService
    ) {
        this.objectMapper = objectMapper;
        this.userService = userService;
        this.userRepository = userRepository;
        this.taskService = taskService;
        this.availabilityService = availabilityService;
        this.unavailabilityService = unavailabilityService;
        this.unavailabilityRepository = unavailabilityRepository;
        this.scheduleService = scheduleService;
        this.schedulingService = schedulingService;
    }

    @Override
    public ValidationReport validate(MultipartFile file) {
        return report(parse(file));
    }

    @Override
    public ValidationReport validate(ScenarioDocument document) {
        return report(requireDocument(document));
    }

    @Override
    @Transactional
    public ImportResult importScenario(MultipartFile file) {
        return persist(parse(file));
    }

    @Override
    @Transactional
    public ImportResult importScenario(ScenarioDocument document) {
        return persist(requireDocument(document));
    }

    private ScenarioDocument requireDocument(ScenarioDocument document) {
        if (document == null) {
            throw new ImportRejectedException(
                    ImportRejectedException.Kind.MALFORMED,
                    null,
                    List.of("Scenario document must not be null")
            );
        }
        return document;
    }

    private ScenarioDocument parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ImportRejectedException(
                    ImportRejectedException.Kind.MALFORMED,
                    null,
                    List.of("Uploaded file is empty")
            );
        }
        try {
            return objectMapper.readValue(
                    file.getBytes(),
                    ScenarioDocument.class
            );
        } catch (JacksonException e) {
            throw new ImportRejectedException(
                    ImportRejectedException.Kind.MALFORMED,
                    null,
                    List.of(
                            "File is not a valid scenario document: "
                                    + e.getLocalizedMessage()
                    )
            );
        } catch (java.io.IOException e) {
            throw new ImportRejectedException(
                    ImportRejectedException.Kind.MALFORMED,
                    null,
                    List.of("Uploaded file could not be read")
            );
        }
    }

    private Analysis analyze(ScenarioDocument document) {

        List<String> invalid = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();

        String scenarioId = null;
        String scenarioName = null;

        ScenarioDocument.ScenarioMeta meta = document.scenario();
        if (meta == null) {
            invalid.add("scenario: section must not be missing");
        } else {
            scenarioId = meta.id();
            scenarioName = meta.name();
            if (isBlank(meta.id())) {
                invalid.add("scenario.id: must not be blank");
            }
            if (isBlank(meta.name())) {
                invalid.add("scenario.name: must not be blank");
            }
        }

        List<ScenarioDocument.UserEntry> users = document.safeUsers();
        List<ScenarioDocument.TaskEntry> tasks = document.safeTasks();
        List<ScenarioDocument.AvailabilityEntry> availabilities =
                document.safeAvailabilities();
        List<ScenarioDocument.UnavailabilityEntry> unavailabilities =
                document.safeUnavailabilities();
        List<ScenarioDocument.ScheduleEntry> schedules =
                document.safeSchedules();

        if (users.isEmpty()) {
            invalid.add("users[]: must contain at least one entry");
        }

        Set<String> seenUsernames = new HashSet<>();
        Set<String> seenEmails = new HashSet<>();

        for (int i = 0; i < users.size(); i++) {
            ScenarioDocument.UserEntry user = users.get(i);
            String label = "users[" + i + "] (" + user.username() + ")";

            if (isBlank(user.username())) {
                invalid.add("users[" + i + "].username: must not be blank");
            } else if (!seenUsernames.add(user.username())) {
                conflicts.add(label + ": duplicate username '"
                        + user.username() + "' within the document");
            }

            if (isBlank(user.password())) {
                invalid.add("users[" + i + "].password: must not be blank");
            }
            if (isBlank(user.firstName())) {
                invalid.add("users[" + i + "].firstName: must not be blank");
            }
            if (isBlank(user.lastName())) {
                invalid.add("users[" + i + "].lastName: must not be blank");
            }

            if (isBlank(user.email())) {
                invalid.add("users[" + i + "].email: must not be blank");
            } else {
                if (!user.email().matches(EMAIL_PATTERN)) {
                    invalid.add("users[" + i + "].email: invalid format '"
                            + user.email() + "'");
                }
                if (!seenEmails.add(user.email())) {
                    conflicts.add(label + ": duplicate email '"
                            + user.email() + "' within the document");
                }
            }

            if (parseRole(user.role()) == null) {
                invalid.add("users[" + i + "].role: unknown value '"
                        + user.role()
                        + "' (expected ADMIN, OPERATOR or REVIEWER)");
            }
        }

        for (int i = 0; i < tasks.size(); i++) {
            ScenarioDocument.TaskEntry task = tasks.get(i);
            String label = "tasks[" + i + "] (" + task.title() + ")";

            if (isBlank(task.title())) {
                invalid.add("tasks[" + i + "].title: must not be blank");
            }
            if (parsePriority(task.priority()) == null) {
                invalid.add("tasks[" + i + "].priority: unknown value '"
                        + task.priority()
                        + "' (expected HIGH, MEDIUM or LOW)");
            }
            if (task.estimatedDurationMinutes() == null
                    || task.estimatedDurationMinutes() <= 0) {
                invalid.add(label
                        + ".estimatedDurationMinutes: must be greater than zero");
            }
        }

        for (int i = 0; i < availabilities.size(); i++) {
            ScenarioDocument.AvailabilityEntry window = availabilities.get(i);
            collectWindowProblems(
                    invalid,
                    "availabilities[" + i + "]",
                    window.username(),
                    window.startDateTime(),
                    window.endDateTime()
            );
        }

        for (int i = 0; i < unavailabilities.size(); i++) {
            ScenarioDocument.UnavailabilityEntry window = unavailabilities.get(i);
            collectWindowProblems(
                    invalid,
                    "unavailabilities[" + i + "]",
                    window.username(),
                    window.startDateTime(),
                    window.endDateTime()
            );
        }

        for (int i = 0; i < schedules.size(); i++) {
            ScenarioDocument.ScheduleEntry schedule = schedules.get(i);
            if (schedule.startDateTime() == null
                    || schedule.endDateTime() == null) {
                invalid.add("schedules[" + i
                        + "]: start and end date/time are required");
            } else if (!schedule.startDateTime()
                    .isBefore(schedule.endDateTime())) {
                invalid.add("schedules[" + i + "]: start must be before end");
            }
        }

        for (int i = 0; i < users.size(); i++) {
            ScenarioDocument.UserEntry user = users.get(i);
            if (isBlank(user.username())) {
                continue;
            }
            if (userRepository.existsByUsername(user.username())) {
                conflicts.add("users[" + i + "]: username '"
                        + user.username() + "' already exists");
            }
            if (!isBlank(user.email())
                    && userRepository.existsByEmail(user.email())) {
                conflicts.add("users[" + i + "]: email '"
                        + user.email() + "' already exists");
            }
        }

        Set<String> documentedUsernames = new HashSet<>(seenUsernames);

        for (int i = 0; i < availabilities.size(); i++) {
            ScenarioDocument.AvailabilityEntry window = availabilities.get(i);
            if (isBlank(window.username())
                    || documentedUsernames.contains(window.username())) {
                continue;
            }
            Optional<User> existing =
                    userRepository.findByUsername(window.username());
            if (existing.isEmpty()) {
                invalid.add("availabilities[" + i
                        + "]: references unknown user '"
                        + window.username() + "'");
            }
        }

        for (int i = 0; i < unavailabilities.size(); i++) {
            ScenarioDocument.UnavailabilityEntry window = unavailabilities.get(i);
            if (isBlank(window.username())
                    || window.startDateTime() == null
                    || window.endDateTime() == null) {
                continue;
            }
            if (documentedUsernames.contains(window.username())) {
                continue;
            }
            Optional<User> existing =
                    userRepository.findByUsername(window.username());
            if (existing.isEmpty()) {
                invalid.add("unavailabilities[" + i
                        + "]: references unknown user '"
                        + window.username() + "'");
                continue;
            }
            boolean overlapsDatabase = unavailabilityRepository
                    .existsByUserIdAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
                            existing.get().getId(),
                            window.endDateTime(),
                            window.startDateTime()
                    );
            if (overlapsDatabase) {
                conflicts.add("unavailabilities[" + i
                        + "]: overlaps an existing unavailability of user '"
                        + window.username() + "'");
            }
        }

        for (int i = 0; i < unavailabilities.size(); i++) {
            for (int j = i + 1; j < unavailabilities.size(); j++) {
                ScenarioDocument.UnavailabilityEntry first =
                        unavailabilities.get(i);
                ScenarioDocument.UnavailabilityEntry second =
                        unavailabilities.get(j);
                if (first.username() == null
                        || !first.username().equals(second.username())) {
                    continue;
                }
                if (overlaps(
                        first.startDateTime(),
                        first.endDateTime(),
                        second.startDateTime(),
                        second.endDateTime()
                )) {
                    conflicts.add("unavailabilities[" + j
                            + "]: overlaps unavailabilities[" + i
                            + "] of user '" + first.username() + "'");
                }
            }
        }

        List<String> problems = new ArrayList<>(invalid);
        problems.addAll(conflicts);

        return new Analysis(
                scenarioId,
                scenarioName,
                problems,
                !conflicts.isEmpty(),
                new ValidationReport.Counts(
                        users.size(),
                        tasks.size(),
                        availabilities.size(),
                        unavailabilities.size(),
                        schedules.size()
                )
        );
    }

    private void collectWindowProblems(
            List<String> problems,
            String label,
            String username,
            LocalDateTime start,
            LocalDateTime end
    ) {
        if (isBlank(username)) {
            problems.add(label + ".username: must not be blank");
        }
        if (start == null || end == null) {
            problems.add(label + ": start and end date/time are required");
        } else if (!start.isBefore(end)) {
            problems.add(label + ": start must be before end");
        }
    }

    private ValidationReport report(ScenarioDocument document) {
        Analysis analysis = analyze(document);
        boolean valid = analysis.problems().isEmpty();
        String status = valid
                ? "VALID"
                : (analysis.conflict() ? "CONFLICT" : "INVALID");
        return new ValidationReport(
                valid,
                status,
                analysis.scenarioId(),
                analysis.scenarioName(),
                analysis.counts(),
                analysis.problems()
        );
    }

    private ImportResult persist(ScenarioDocument document) {

        Analysis analysis = analyze(document);
        if (!analysis.problems().isEmpty()) {
            throw new ImportRejectedException(
                    analysis.conflict()
                            ? ImportRejectedException.Kind.CONFLICT
                            : ImportRejectedException.Kind.INVALID,
                    analysis.scenarioId(),
                    analysis.problems()
            );
        }

        String scenarioId = analysis.scenarioId();

        int usersCreated = 0;
        int tasksCreated = 0;
        int availabilitiesCreated = 0;
        int unavailabilitiesCreated = 0;
        int schedulesCreated = 0;
        int tasksScheduled = 0;

        String step = "scenario";
        try {
            Map<String, User> createdUsers = new HashMap<>();

            int index = 0;
            for (ScenarioDocument.UserEntry entry : document.safeUsers()) {
                step = "users[" + index + "] (" + entry.username() + ")";
                User saved = userService.create(new User(
                        entry.username(),
                        entry.password(),
                        entry.firstName(),
                        entry.lastName(),
                        entry.email(),
                        Role.valueOf(entry.role()),
                        !Boolean.FALSE.equals(entry.enabled())
                ));
                createdUsers.put(entry.username(), saved);
                usersCreated++;
                index++;
            }

            index = 0;
            for (ScenarioDocument.AvailabilityEntry entry
                    : document.safeAvailabilities()) {
                step = "availabilities[" + index + "]";
                availabilityService.create(new Availability(
                        resolveUser(createdUsers, entry.username()),
                        entry.startDateTime(),
                        entry.endDateTime()
                ));
                availabilitiesCreated++;
                index++;
            }

            index = 0;
            for (ScenarioDocument.UnavailabilityEntry entry
                    : document.safeUnavailabilities()) {
                step = "unavailabilities[" + index + "]";
                unavailabilityService.create(new Unavailability(
                        resolveUser(createdUsers, entry.username()),
                        entry.startDateTime(),
                        entry.endDateTime(),
                        entry.reason()
                ));
                unavailabilitiesCreated++;
                index++;
            }

            index = 0;
            for (ScenarioDocument.TaskEntry entry : document.safeTasks()) {
                step = "tasks[" + index + "] (" + entry.title() + ")";
                taskService.create(new Task(
                        entry.title(),
                        entry.description(),
                        TaskStatus.PENDING,
                        TaskPriority.valueOf(entry.priority()),
                        entry.estimatedDurationMinutes(),
                        entry.deadline()
                ));
                tasksCreated++;
                index++;
            }

            index = 0;
            for (ScenarioDocument.ScheduleEntry entry
                    : document.safeSchedules()) {
                step = "schedules[" + index + "]";
                Schedule schedule = new Schedule();
                schedule.setStartDateTime(entry.startDateTime());
                schedule.setEndDateTime(entry.endDateTime());
                schedule.setStatus(ScheduleStatus.DRAFT);
                Schedule saved = scheduleService.create(schedule);
                schedulesCreated++;

                SchedulingResult result =
                        schedulingService.generate(saved.getId());
                tasksScheduled += result.scheduledTasks().size();
                index++;
            }

            return new ImportResult(
                    scenarioId,
                    analysis.scenarioName(),
                    usersCreated,
                    tasksCreated,
                    availabilitiesCreated,
                    unavailabilitiesCreated,
                    schedulesCreated,
                    tasksScheduled
            );
        } catch (BusinessRuleException e) {
            throw new ImportRejectedException(
                    ImportRejectedException.Kind.CONFLICT,
                    scenarioId,
                    List.of(step + ": " + e.getMessage())
            );
        } catch (EntityNotFoundException | ValidationException e) {
            throw new ImportRejectedException(
                    ImportRejectedException.Kind.INVALID,
                    scenarioId,
                    List.of(step + ": " + e.getMessage())
            );
        }
    }

    private User resolveUser(Map<String, User> createdUsers, String username) {
        User created = createdUsers.get(username);
        if (created != null) {
            return created;
        }
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Unknown user: " + username
                ));
    }

    private boolean overlaps(
            LocalDateTime startA,
            LocalDateTime endA,
            LocalDateTime startB,
            LocalDateTime endB
    ) {
        if (startA == null || endA == null || startB == null || endB == null) {
            return false;
        }
        return startA.isBefore(endB) && startB.isBefore(endA);
    }

    private Role parseRole(String value) {
        try {
            return Role.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    private TaskPriority parsePriority(String value) {
        try {
            return TaskPriority.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record Analysis(
            String scenarioId,
            String scenarioName,
            List<String> problems,
            boolean conflict,
            ValidationReport.Counts counts
    ) {
    }
}
