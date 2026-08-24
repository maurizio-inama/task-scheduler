package com.taskscheduler.integration;

import com.taskscheduler.domain.entity.Assignment;
import com.taskscheduler.domain.entity.Availability;
import com.taskscheduler.domain.entity.Role;
import com.taskscheduler.domain.entity.Schedule;
import com.taskscheduler.domain.entity.ScheduleStatus;
import com.taskscheduler.domain.entity.Task;
import com.taskscheduler.domain.entity.TaskPriority;
import com.taskscheduler.domain.entity.TaskStatus;
import com.taskscheduler.domain.entity.Unavailability;
import com.taskscheduler.domain.entity.User;
import com.taskscheduler.domain.repository.AssignmentRepository;
import com.taskscheduler.domain.repository.AvailabilityRepository;
import com.taskscheduler.domain.repository.ScheduleRepository;
import com.taskscheduler.domain.repository.TaskRepository;
import com.taskscheduler.domain.repository.UnavailabilityRepository;
import com.taskscheduler.domain.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration tests: HTTP request → security filter chain →
 * controller → service → repository → PostgreSQL.
 *
 * Unlike the unit/slice tests, these run against the real database and the
 * real security configuration. Each test creates its own uniquely-suffixed
 * data and removes it afterwards, so the shared development database is left
 * untouched.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SystemIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private AvailabilityRepository availabilityRepository;

    @Autowired
    private UnavailabilityRepository unavailabilityRepository;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdTaskIds = new ArrayList<>();
    private final List<Long> createdScheduleIds = new ArrayList<>();
    private final List<Long> createdAvailabilityIds = new ArrayList<>();
    private final List<Long> createdUnavailabilityIds = new ArrayList<>();

    private String suffix;
    private String adminUsername;
    private String operatorUsername;

    @BeforeEach
    void setUp() {
        suffix = Long.toHexString(System.nanoTime());
        adminUsername = "admin-it-" + suffix;
        operatorUsername = "op-it-" + suffix;
    }

    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> {
            for (Long scheduleId : createdScheduleIds) {
                assignmentRepository.findByScheduleId(scheduleId)
                        .forEach(assignmentRepository::delete);
            }
            createdScheduleIds.forEach(scheduleRepository::deleteById);
            createdTaskIds.forEach(taskRepository::deleteById);
            createdAvailabilityIds.forEach(availabilityRepository::deleteById);
            createdUnavailabilityIds.forEach(unavailabilityRepository::deleteById);
            createdUserIds.forEach(userRepository::deleteById);
        });
        createdUserIds.clear();
        createdTaskIds.clear();
        createdScheduleIds.clear();
        createdAvailabilityIds.clear();
        createdUnavailabilityIds.clear();
    }

    private User newUser(String username, Role role, boolean enabled) {
        User saved = userRepository.save(new User(
                username,
                passwordEncoder.encode("password-" + username),
                "Integration",
                "Test",
                username + "@example.com",
                role,
                enabled
        ));
        createdUserIds.add(saved.getId());
        return saved;
    }

    private String login(String username) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "password": "%s"}
                                """.formatted(username, "password-" + username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        int index = response.indexOf("\"token\":\"") + "\"token\":\"".length();
        return response.substring(index, response.indexOf('"', index));
    }

    // ------------------------------------------------------------------
    // Authentication
    // ------------------------------------------------------------------

    @Test
    void invalidLoginIsRejectedWith401() throws Exception {
        newUser(adminUsername, Role.ADMIN, true);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "password": "wrong-password"}
                                """.formatted(adminUsername)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void disabledUserCannotLogIn() throws Exception {
        newUser(adminUsername, Role.ADMIN, false);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "password": "%s"}
                                """.formatted(adminUsername, "password-" + adminUsername)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsRejectMissingTokens() throws Exception {
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedTokenIsRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/tasks")
                        .header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // Authorization
    // ------------------------------------------------------------------

    @Test
    void operatorCannotCreateTasksButCanReadThem() throws Exception {
        newUser(operatorUsername, Role.OPERATOR, true);
        String operatorToken = login(operatorUsername);

        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Forbidden", "description": null,
                                 "status": "PENDING", "priority": "LOW",
                                 "estimatedDurationMinutes": 30, "deadline": null}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        mockMvc.perform(get("/api/tasks")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk());
    }

    @Test
    void nonAdminCannotListUsers() throws Exception {
        newUser(operatorUsername, Role.OPERATOR, true);
        String operatorToken = login(operatorUsername);

        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // CRUD flows through every layer into PostgreSQL
    // ------------------------------------------------------------------

    @Test
    void userFlowCreateRetrieveUpdateVerifiesPersistence() throws Exception {
        newUser(adminUsername, Role.ADMIN, true);
        String adminToken = login(adminUsername);

        String response = mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "flow-%s", "password": "secret-123",
                                 "firstName": "Flow", "lastName": "One",
                                 "email": "flow-%s@example.com",
                                 "role": "REVIEWER", "enabled": true}
                                """.formatted(suffix, suffix)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long userId = extractId(response);
        createdUserIds.add(userId);

        mockMvc.perform(put("/api/users/" + userId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "flow-%s", "password": "secret-123",
                                 "firstName": "Flow", "lastName": "Renamed",
                                 "email": "flow-%s@example.com",
                                 "role": "REVIEWER", "enabled": true}
                                """.formatted(suffix, suffix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Renamed"));

        transactionTemplate.executeWithoutResult(status -> {
            User persisted = userRepository.findById(userId).orElseThrow();
            assertThat(persisted.getLastName()).isEqualTo("Renamed");
            assertThat(persisted.getRole()).isEqualTo(Role.REVIEWER);
            assertThat(passwordEncoder.matches(
                    "secret-123", persisted.getPassword())).isTrue();
        });
    }

    @Test
    void taskFlowCreateUpdateVerifyPersistsToDatabase() throws Exception {
        newUser(adminUsername, Role.ADMIN, true);
        String adminToken = login(adminUsername);

        String response = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "integration-task-%s",
                                 "description": "created by SystemIntegrationTest",
                                 "status": "PENDING", "priority": "MEDIUM",
                                 "estimatedDurationMinutes": 45, "deadline": null}
                                """.formatted(suffix)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long taskId = extractId(response);
        createdTaskIds.add(taskId);

        mockMvc.perform(put("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "integration-task-%s",
                                 "description": "created by SystemIntegrationTest",
                                 "status": "PENDING", "priority": "CRITICAL",
                                 "estimatedDurationMinutes": 45, "deadline": null}
                                """.formatted(suffix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("CRITICAL"));

        transactionTemplate.executeWithoutResult(status -> {
            Task persisted = taskRepository.findById(taskId).orElseThrow();
            assertThat(persisted.getPriority()).isEqualTo(TaskPriority.CRITICAL);
            assertThat(persisted.getStatus()).isEqualTo(TaskStatus.PENDING);
        });
    }

    @Test
    void validationErrorReturns400WithFieldMessages() throws Exception {
        newUser(adminUsername, Role.ADMIN, true);
        String adminToken = login(adminUsername);

        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "", "description": null,
                                 "status": "PENDING", "priority": "MEDIUM",
                                 "estimatedDurationMinutes": -5, "deadline": null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("title")));
    }

    // ------------------------------------------------------------------
    // Scheduling flow: REST → service → engine → repository → REST
    // ------------------------------------------------------------------

    @Test
    void schedulingFlowGeneratesPersistedAssignmentsFromRest() throws Exception {
        User admin = newUser(adminUsername, Role.ADMIN, true);
        User operator = newUser(operatorUsername, Role.OPERATOR, true);
        String adminToken = login(adminUsername);

        LocalDateTime day = LocalDateTime.of(2026, 9, 7, 0, 0);
        LocalDateTime windowStart = day.withHour(8);
        LocalDateTime windowEnd = day.withHour(18);

        Schedule draft = new Schedule();
        draft.setStartDateTime(windowStart);
        draft.setEndDateTime(windowEnd);
        draft.setStatus(ScheduleStatus.DRAFT);
        final Schedule schedule = scheduleRepository.save(draft);
        createdScheduleIds.add(schedule.getId());

        Task first = persistTask("sched-a-" + suffix, 120, TaskPriority.HIGH);
        Task second = persistTask("sched-b-" + suffix, 60, TaskPriority.MEDIUM);

        Availability availability = availabilityRepository.save(
                new Availability(operator, windowStart, windowEnd));
        createdAvailabilityIds.add(availability.getId());

        String response = mockMvc.perform(
                        post("/api/schedules/" + schedule.getId() + "/generate")
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleId").value(schedule.getId()))
                .andExpect(jsonPath("$.scheduledTaskCount").value(2))
                .andExpect(jsonPath("$.createdAssignmentCount").value(2))
                .andExpect(jsonPath("$.unscheduledTasks.length()").value(0))
                .andExpect(jsonPath("$.assignments.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();

        transactionTemplate.executeWithoutResult(status -> {
            List<Assignment> persisted =
                    assignmentRepository.findByScheduleId(schedule.getId());
            assertThat(persisted).hasSize(2);
            assertThat(persisted.stream().mapToInt(a ->
                            (int) java.time.Duration.between(
                                    a.getStartDateTime(), a.getEndDateTime())
                                    .toMinutes())
                    .sum()).isEqualTo(180);
            assertThat(persisted.stream().allMatch(a ->
                    !a.getStartDateTime().isBefore(windowStart)
                            && !a.getEndDateTime().isAfter(windowEnd))).isTrue();

            Task persistedFirst = taskRepository.findById(first.getId()).orElseThrow();
            Task persistedSecond = taskRepository.findById(second.getId()).orElseThrow();
            assertThat(persistedFirst.getStatus()).isEqualTo(TaskStatus.SCHEDULED);
            assertThat(persistedSecond.getStatus()).isEqualTo(TaskStatus.SCHEDULED);
        });

        mockMvc.perform(get("/api/assignments")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(2)));

        mockMvc.perform(delete("/api/schedules/" + schedule.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        createdScheduleIds.remove(schedule.getId());

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(assignmentRepository.findByScheduleId(schedule.getId()))
                    .isEmpty();
            assertThat(taskRepository.findById(first.getId()).orElseThrow()
                    .getStatus()).isEqualTo(TaskStatus.PENDING);
            assertThat(taskRepository.findById(second.getId()).orElseThrow()
                    .getStatus()).isEqualTo(TaskStatus.PENDING);
        });
    }

    @Test
    void deletingScheduleReleasesTasksAndAllowsRescheduling() throws Exception {
        User admin = newUser(adminUsername, Role.ADMIN, true);
        User operator = newUser(operatorUsername, Role.OPERATOR, true);
        String adminToken = login(adminUsername);

        LocalDateTime windowStart = LocalDateTime.of(2026, 9, 21, 8, 0);
        LocalDateTime windowEnd = LocalDateTime.of(2026, 9, 21, 18, 0);

        Schedule firstDraft = new Schedule();
        firstDraft.setStartDateTime(windowStart);
        firstDraft.setEndDateTime(windowEnd);
        firstDraft.setStatus(ScheduleStatus.DRAFT);
        final Schedule firstSchedule = scheduleRepository.save(firstDraft);
        createdScheduleIds.add(firstSchedule.getId());

        Task task = persistTask("release-me-" + suffix, 120,
                TaskPriority.HIGH);
        Availability availability = availabilityRepository.save(
                new Availability(operator, windowStart, windowEnd));
        createdAvailabilityIds.add(availability.getId());

        mockMvc.perform(
                        post("/api/schedules/" + firstSchedule.getId() + "/generate")
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledTaskCount").value(1));

        transactionTemplate.executeWithoutResult(status ->
                assertThat(taskRepository.findById(task.getId()).orElseThrow()
                        .getStatus()).isEqualTo(TaskStatus.SCHEDULED));

        mockMvc.perform(get("/api/tasks/" + task.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"));

        mockMvc.perform(delete("/api/schedules/" + firstSchedule.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        createdScheduleIds.remove(firstSchedule.getId());

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(assignmentRepository.findByScheduleId(
                    firstSchedule.getId())).isEmpty();
            assertThat(taskRepository.findById(task.getId()).orElseThrow()
                    .getStatus()).isEqualTo(TaskStatus.PENDING);
        });

        Schedule secondDraft = new Schedule();
        secondDraft.setStartDateTime(windowStart.plusDays(1));
        secondDraft.setEndDateTime(windowEnd.plusDays(1));
        secondDraft.setStatus(ScheduleStatus.DRAFT);
        final Schedule secondSchedule = scheduleRepository.save(secondDraft);
        createdScheduleIds.add(secondSchedule.getId());
        Availability secondAvailability = availabilityRepository.save(
                new Availability(operator,
                        windowStart.plusDays(1), windowEnd.plusDays(1)));
        createdAvailabilityIds.add(secondAvailability.getId());

        String secondRun = mockMvc.perform(
                        post("/api/schedules/" + secondSchedule.getId() + "/generate")
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(secondRun).contains("\"scheduledTaskCount\":1");
        transactionTemplate.executeWithoutResult(status -> {
            Task released = taskRepository.findById(task.getId()).orElseThrow();
            assertThat(released.getStatus()).isEqualTo(TaskStatus.SCHEDULED);
            List<Assignment> assignments = assignmentRepository
                    .findByScheduleId(secondSchedule.getId());
            assertThat(assignments).hasSize(1);
            assertThat(assignments.get(0).getTask().getId())
                    .isEqualTo(task.getId());
        });
    }

    @Test
    void schedulingFlowReportsUnscheduledTasksWhenCapacityIsInsufficient()
            throws Exception {
        User admin = newUser(adminUsername, Role.ADMIN, true);
        User operator = newUser(operatorUsername, Role.OPERATOR, true);
        String adminToken = login(adminUsername);

        LocalDateTime windowStart = LocalDateTime.of(2026, 9, 8, 8, 0);
        LocalDateTime windowEnd = LocalDateTime.of(2026, 9, 8, 10, 0);

        Schedule draft = new Schedule();
        draft.setStartDateTime(windowStart);
        draft.setEndDateTime(windowEnd);
        draft.setStatus(ScheduleStatus.DRAFT);
        final Schedule schedule = scheduleRepository.save(draft);
        createdScheduleIds.add(schedule.getId());

        Task oversized = persistTask("too-big-" + suffix, 600, TaskPriority.LOW);

        Availability availability = availabilityRepository.save(
                new Availability(operator, windowStart, windowEnd));
        createdAvailabilityIds.add(availability.getId());

        mockMvc.perform(post("/api/schedules/" + schedule.getId() + "/generate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledTaskCount").value(0))
                .andExpect(jsonPath("$.createdAssignmentCount").value(0))
                .andExpect(jsonPath("$.unscheduledTasks[0].taskId")
                        .value(oversized.getId()))
                .andExpect(jsonPath("$.unscheduledTasks[0].reason")
                        .value("INSUFFICIENT_CAPACITY"));
    }

    @Test
    void schedulingFlowRespectsUnavailabilityWindows() throws Exception {
        User admin = newUser(adminUsername, Role.ADMIN, true);
        User operator = newUser(operatorUsername, Role.OPERATOR, true);
        String adminToken = login(adminUsername);

        LocalDateTime windowStart = LocalDateTime.of(2026, 9, 9, 8, 0);
        LocalDateTime windowEnd = LocalDateTime.of(2026, 9, 9, 18, 0);

        Schedule draft = new Schedule();
        draft.setStartDateTime(windowStart);
        draft.setEndDateTime(windowEnd);
        draft.setStatus(ScheduleStatus.DRAFT);
        final Schedule schedule = scheduleRepository.save(draft);
        createdScheduleIds.add(schedule.getId());

        Task task = persistTask("blocked-" + suffix, 60, TaskPriority.MEDIUM);

        Availability morning = availabilityRepository.save(
                new Availability(operator, windowStart, day(2026, 9, 9, 13, 0)));
        createdAvailabilityIds.add(morning.getId());

        Unavailability midday = unavailabilityRepository.save(new Unavailability(
                operator,
                LocalDateTime.of(2026, 9, 9, 10, 0),
                day(2026, 9, 9, 12, 0),
                "integration block"
        ));
        createdUnavailabilityIds.add(midday.getId());

        mockMvc.perform(post("/api/schedules/" + schedule.getId() + "/generate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledTaskCount").value(1));

        transactionTemplate.executeWithoutResult(status -> {
            List<Assignment> persisted =
                    assignmentRepository.findByScheduleId(schedule.getId());
            assertThat(persisted).hasSize(1);
            Assignment assignment = persisted.get(0);
            boolean placedInFreeWindow =
                    (!assignment.getEndDateTime().isAfter(LocalDateTime.of(2026, 9, 9, 10, 0)))
                            || (!assignment.getStartDateTime()
                            .isBefore(LocalDateTime.of(2026, 9, 9, 12, 0)));
            assertThat(placedInFreeWindow).isTrue();
        });
    }

    // ------------------------------------------------------------------
    // Admin JSON scenario import: REST → importer → services → PostgreSQL
    // ------------------------------------------------------------------

    private org.springframework.mock.web.MockMultipartFile scenario(
            String json) {
        return new org.springframework.mock.web.MockMultipartFile(
                "file",
                "scenario.json",
                MediaType.APPLICATION_JSON_VALUE,
                json.getBytes()
        );
    }

    private long countUsersWithPrefix(String prefix) {
        return userRepository.findAll().stream()
                .filter(user -> user.getUsername().startsWith(prefix))
                .count();
    }

    @Test
    void adminImportPersistsScenarioThroughRestAndSchedulesTasks()
            throws Exception {
        newUser(adminUsername, Role.ADMIN, true);
        String adminToken = login(adminUsername);

        String json = """
                {
                  "scenario": {
                    "id": "it-import-%s",
                    "name": "Integration Import",
                    "description": "created by SystemIntegrationTest"
                  },
                  "users": [
                    {"username": "imp-op1-%s", "password": "secret-123",
                     "firstName": "Imp", "lastName": "One",
                     "email": "imp-op1-%s@example.com",
                     "role": "OPERATOR", "enabled": true},
                    {"username": "imp-op2-%s", "password": "secret-123",
                     "firstName": "Imp", "lastName": "Two",
                     "email": "imp-op2-%s@example.com",
                     "role": "OPERATOR", "enabled": true}
                  ],
                  "tasks": [
                    {"title": "imported-task-%s", "description": null,
                     "priority": "HIGH", "estimatedDurationMinutes": 120,
                     "deadline": "2026-09-18T17:00:00"}
                  ],
                  "availabilities": [
                    {"username": "imp-op1-%s",
                     "startDateTime": "2026-09-14T08:00:00",
                     "endDateTime": "2026-09-14T18:00:00"},
                    {"username": "imp-op2-%s",
                     "startDateTime": "2026-09-14T08:00:00",
                     "endDateTime": "2026-09-14T18:00:00"}
                  ],
                  "unavailabilities": [],
                  "schedules": [
                    {"startDateTime": "2026-09-14T08:00:00",
                     "endDateTime": "2026-09-14T18:00:00"}
                  ]
                }
                """.formatted(suffix, suffix, suffix, suffix, suffix,
                suffix, suffix, suffix);

        mockMvc.perform(multipart("/api/admin/import")
                        .file(scenario(json))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenarioId").value("it-import-" + suffix))
                .andExpect(jsonPath("$.usersCreated").value(2))
                .andExpect(jsonPath("$.tasksCreated").value(1))
                .andExpect(jsonPath("$.availabilitiesCreated").value(2))
                .andExpect(jsonPath("$.unavailabilitiesCreated").value(0))
                .andExpect(jsonPath("$.schedulesCreated").value(1))
                .andExpect(jsonPath("$.tasksScheduled").value(1));

        transactionTemplate.executeWithoutResult(status -> {
            User imported = userRepository.findByUsername(
                    "imp-op1-" + suffix).orElseThrow();
            assertThat(passwordEncoder.matches(
                    "secret-123", imported.getPassword())).isTrue();

            Task importedTask = taskRepository.findAll().stream()
                    .filter(task ->
                            task.getTitle().equals("imported-task-" + suffix))
                    .findFirst().orElseThrow();
            createdTaskIds.add(importedTask.getId());
            assertThat(importedTask.getStatus())
                    .isEqualTo(TaskStatus.SCHEDULED);
        });

        User firstOperator = userRepository.findByUsername(
                "imp-op1-" + suffix).orElseThrow();
        User secondOperator = userRepository.findByUsername(
                "imp-op2-" + suffix).orElseThrow();
        createdUserIds.add(firstOperator.getId());
        createdUserIds.add(secondOperator.getId());
        availabilityRepository.findByUserId(firstOperator.getId())
                .forEach(availability ->
                        createdAvailabilityIds.add(availability.getId()));
        availabilityRepository.findByUserId(secondOperator.getId())
                .forEach(availability ->
                        createdAvailabilityIds.add(availability.getId()));

        Schedule importedSchedule = scheduleRepository.findAll().stream()
                .filter(schedule -> schedule.getStartDateTime()
                        .equals(LocalDateTime.of(2026, 9, 14, 8, 0)))
                .filter(schedule -> assignmentRepository
                        .findByScheduleId(schedule.getId()).size() == 1)
                .findFirst()
                .orElseThrow();
        createdScheduleIds.add(importedSchedule.getId());
    }

    @Test
    void conflictingScenarioImportIsRejectedAtomically()
            throws Exception {
        User existing = newUser("clash-" + suffix, Role.OPERATOR, true);
        newUser(adminUsername, Role.ADMIN, true);
        String adminToken = login(adminUsername);

        String json = """
                {
                  "scenario": {
                    "id": "it-clash-%s",
                    "name": "Conflicting Import"
                  },
                  "users": [
                    {"username": "fresh-%s", "password": "secret-123",
                     "firstName": "Fresh", "lastName": "One",
                     "email": "fresh-%s@example.com",
                     "role": "OPERATOR", "enabled": true}
                  ],
                  "tasks": [
                    {"title": "clash-task-%s", "description": null,
                     "priority": "LOW", "estimatedDurationMinutes": 60,
                     "deadline": null}
                  ],
                  "availabilities": [],
                  "unavailabilities": []
                }
                """.formatted(suffix, suffix, suffix, suffix);

        String conflictingJson = json.replace(
                "\"fresh-%s\"".formatted(suffix),
                "\"clash-" + suffix + "\""
        ).replace(
                "fresh-%s@example.com".formatted(suffix),
                existing.getEmail()
        );

        mockMvc.perform(multipart("/api/admin/import/validate")
                        .file(scenario(conflictingJson))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.status").value("CONFLICT"))
                .andExpect(jsonPath("$.problems[0]").value(
                        org.hamcrest.Matchers.containsString(
                                "'clash-" + suffix + "' already exists")));

        mockMvc.perform(multipart("/api/admin/import")
                        .file(scenario(conflictingJson))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("IMPORT_REJECTED"))
                .andExpect(jsonPath("$.kind").value("CONFLICT"))
                .andExpect(jsonPath("$.problems[0]").value(
                        org.hamcrest.Matchers.containsString(
                                "'clash-" + suffix + "' already exists")));

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(countUsersWithPrefix("fresh-" + suffix)).isZero();
            assertThat(taskRepository.findAll().stream()
                    .noneMatch(task -> task.getTitle()
                            .equals("clash-task-" + suffix))).isTrue();
        });
    }

    @Test
    void malformedScenarioImportIsRejectedWithoutSideEffects() throws Exception {
        newUser(adminUsername, Role.ADMIN, true);
        String adminToken = login(adminUsername);

        mockMvc.perform(multipart("/api/admin/import")
                        .file(scenario("{ this is not json "))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("IMPORT_REJECTED"))
                .andExpect(jsonPath("$.kind").value("MALFORMED"));
    }

    @Test
    void nonAdminCannotImportScenariosThroughRest() throws Exception {
        newUser(operatorUsername, Role.OPERATOR, true);
        String operatorToken = login(operatorUsername);

        String json = """
                {
                  "scenario": {"id": "nope-%s", "name": "Nope"},
                  "users": [
                    {"username": "blocked-%s", "password": "secret-123",
                     "firstName": "Blocked", "lastName": "User",
                     "email": "blocked-%s@example.com",
                     "role": "OPERATOR", "enabled": true}
                  ]
                }
                """.formatted(suffix, suffix, suffix);

        mockMvc.perform(multipart("/api/admin/import")
                        .file(scenario(json))
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        transactionTemplate.executeWithoutResult(status ->
                assertThat(countUsersWithPrefix("blocked-" + suffix)).isZero());
    }

    private Task persistTask(String title, int minutes, TaskPriority priority) {
        Task saved = taskRepository.save(new Task(
                title,
                null,
                TaskStatus.PENDING,
                priority,
                minutes,
                null
        ));
        createdTaskIds.add(saved.getId());
        return saved;
    }

    private LocalDateTime day(int year, int month, int dayOfMonth, int hour,
            int minute) {
        return LocalDateTime.of(year, month, dayOfMonth, hour, minute);
    }

    private long extractId(String json) {
        int index = json.indexOf("\"id\":") + "\"id\":".length();
        int end = index;
        while (end < json.length()
                && (Character.isDigit(json.charAt(end)))) {
            end++;
        }
        return Long.parseLong(json.substring(index, end));
    }
}
