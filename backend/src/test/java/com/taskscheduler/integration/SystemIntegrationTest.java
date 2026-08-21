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

        transactionTemplate.executeWithoutResult(status ->
                assertThat(assignmentRepository.findByScheduleId(schedule.getId()))
                        .isEmpty());
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
