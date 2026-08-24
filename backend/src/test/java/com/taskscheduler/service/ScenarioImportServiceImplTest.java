package com.taskscheduler.service;

import com.taskscheduler.controller.dto.ImportResult;
import com.taskscheduler.controller.dto.ValidationReport;
import com.taskscheduler.domain.entity.Role;
import com.taskscheduler.domain.entity.Schedule;
import com.taskscheduler.domain.entity.ScheduleStatus;
import com.taskscheduler.domain.entity.Task;
import com.taskscheduler.domain.entity.TaskStatus;
import com.taskscheduler.domain.entity.Unavailability;
import com.taskscheduler.domain.entity.User;
import com.taskscheduler.domain.repository.UnavailabilityRepository;
import com.taskscheduler.domain.repository.UserRepository;
import com.taskscheduler.exception.BusinessRuleException;
import com.taskscheduler.importer.ImportRejectedException;
import com.taskscheduler.importer.ScenarioDocument;
import com.taskscheduler.scheduling.model.SchedulingResult;
import com.taskscheduler.scheduling.model.TaskSchedule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScenarioImportServiceImplTest {

    @Mock
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TaskService taskService;

    @Mock
    private AvailabilityService availabilityService;

    @Mock
    private UnavailabilityService unavailabilityService;

    @Mock
    private UnavailabilityRepository unavailabilityRepository;

    @Mock
    private ScheduleService scheduleService;

    @Mock
    private SchedulingService schedulingService;

    private final ObjectMapper objectMapper = new JsonMapper();

    private ScenarioImportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ScenarioImportServiceImpl(
                objectMapper,
                userService,
                userRepository,
                taskService,
                availabilityService,
                unavailabilityService,
                unavailabilityRepository,
                scheduleService,
                schedulingService
        );
    }

    private ScenarioDocument document(
            List<ScenarioDocument.UserEntry> users,
            List<ScenarioDocument.TaskEntry> tasks,
            List<ScenarioDocument.AvailabilityEntry> availabilities,
            List<ScenarioDocument.UnavailabilityEntry> unavailabilities,
            List<ScenarioDocument.ScheduleEntry> schedules
    ) {
        return new ScenarioDocument(
                new ScenarioDocument.ScenarioMeta(
                        "test-scenario",
                        "Test Scenario",
                        "Used by automated tests"
                ),
                users,
                tasks,
                availabilities,
                unavailabilities,
                schedules
        );
    }

    private ScenarioDocument.UserEntry user(String username) {
        return new ScenarioDocument.UserEntry(
                username,
                "password",
                "First",
                "Last",
                username + "@example.com",
                "OPERATOR",
                true
        );
    }

    @Test
    void validateReturnsValidReportForWellFormedDocument() {
        ScenarioDocument document = document(
                List.of(user("alice")),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        ValidationReport report = service.validate(document);

        assertThat(report.valid()).isTrue();
        assertThat(report.status()).isEqualTo("VALID");
        assertThat(report.scenarioId()).isEqualTo("test-scenario");
        assertThat(report.counts().users()).isEqualTo(1);
        assertThat(report.problems()).isEmpty();
    }

    @Test
    void validateReportsConflictForDuplicateUsernameWithinDocument() {
        ScenarioDocument document = document(
                List.of(user("alice"), user("alice")),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        ValidationReport report = service.validate(document);

        assertThat(report.valid()).isFalse();
        assertThat(report.status()).isEqualTo("CONFLICT");
        assertThat(report.problems())
                .anyMatch(problem -> problem.contains("duplicate username"));
    }

    @Test
    void validateReportsConflictWhenUsernameAlreadyExists() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        ScenarioDocument document = document(
                List.of(user("alice")),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        ValidationReport report = service.validate(document);

        assertThat(report.status()).isEqualTo("CONFLICT");
        assertThat(report.problems())
                .anyMatch(problem -> problem.contains("already exists"));
    }

    @Test
    void validateRejectsInvalidTaskDuration() {
        ScenarioDocument document = document(
                List.of(user("alice")),
                List.of(new ScenarioDocument.TaskEntry(
                        "Prepare monthly sales report",
                        null,
                        "HIGH",
                        0,
                        LocalDateTime.of(2027, 1, 10, 17, 0)
                )),
                List.of(),
                List.of(),
                List.of()
        );

        ValidationReport report = service.validate(document);

        assertThat(report.status()).isEqualTo("INVALID");
        assertThat(report.problems())
                .anyMatch(problem -> problem.contains("greater than zero"));
    }

    @Test
    void validateRejectsUnknownRole() {
        ScenarioDocument.UserEntry invalid = new ScenarioDocument.UserEntry(
                "alice", "password", "First", "Last",
                "alice@example.com", "MANAGER", true
        );

        ValidationReport report = service.validate(document(
                List.of(invalid),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ));

        assertThat(report.status()).isEqualTo("INVALID");
        assertThat(report.problems())
                .anyMatch(problem -> problem.contains("unknown value 'MANAGER'"));
    }

    @Test
    void validateRejectsReferenceToUnknownUser() {
        when(userRepository.findByUsername("ghost"))
                .thenReturn(Optional.empty());

        ScenarioDocument document = document(
                List.of(user("alice")),
                List.of(),
                List.of(new ScenarioDocument.AvailabilityEntry(
                        "ghost",
                        LocalDateTime.of(2027, 1, 4, 9, 0),
                        LocalDateTime.of(2027, 1, 4, 17, 0)
                )),
                List.of(),
                List.of()
        );

        ValidationReport report = service.validate(document);

        assertThat(report.status()).isEqualTo("INVALID");
        assertThat(report.problems())
                .anyMatch(problem -> problem.contains("references unknown user 'ghost'"));
    }

    @Test
    void validateReportsConflictForOverlappingUnavailabilitiesOfSameUser() {
        ScenarioDocument document = document(
                List.of(user("alice")),
                List.of(),
                List.of(),
                List.of(
                        new ScenarioDocument.UnavailabilityEntry(
                                "alice",
                                LocalDateTime.of(2027, 1, 5, 9, 0),
                                LocalDateTime.of(2027, 1, 5, 13, 0),
                                "Morning off"
                        ),
                        new ScenarioDocument.UnavailabilityEntry(
                                "alice",
                                LocalDateTime.of(2027, 1, 5, 12, 0),
                                LocalDateTime.of(2027, 1, 5, 17, 0),
                                "Afternoon off"
                        )
                ),
                List.of()
        );

        ValidationReport report = service.validate(document);

        assertThat(report.valid()).isFalse();
        assertThat(report.status()).isEqualTo("CONFLICT");
        assertThat(report.problems())
                .anyMatch(problem -> problem.contains("overlaps unavailabilities[0]"));
    }

    @Test
    void importCreatesAllEntitiesInDependencyOrderAndReturnsResult() {
        User alice = new User(11L);
        User bob = new User(12L);
        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(userService.create(any(User.class)))
                .thenReturn(alice)
                .thenReturn(bob);

        Schedule schedule = mock(Schedule.class);
        when(schedule.getId()).thenReturn(77L);
        when(scheduleService.create(any(Schedule.class)))
                .thenReturn(schedule);
        when(schedulingService.generate(77L)).thenReturn(new SchedulingResult(
                List.of(
                        new TaskSchedule(101L, 11L, List.of()),
                        new TaskSchedule(102L, 12L, List.of())
                ),
                List.of()
        ));

        ScenarioDocument document = document(
                List.of(user("alice"), user("bob")),
                List.of(new ScenarioDocument.TaskEntry(
                        "Prepare monthly sales report",
                        "Summary",
                        "HIGH",
                        120,
                        LocalDateTime.of(2027, 1, 8, 17, 0)
                )),
                List.of(new ScenarioDocument.AvailabilityEntry(
                        "alice",
                        LocalDateTime.of(2027, 1, 4, 9, 0),
                        LocalDateTime.of(2027, 1, 4, 17, 0)
                )),
                List.of(new ScenarioDocument.UnavailabilityEntry(
                        "bob",
                        LocalDateTime.of(2027, 1, 5, 9, 0),
                        LocalDateTime.of(2027, 1, 5, 12, 0),
                        "Training"
                )),
                List.of(new ScenarioDocument.ScheduleEntry(
                        LocalDateTime.of(2027, 1, 4, 8, 0),
                        LocalDateTime.of(2027, 1, 8, 18, 0)
                ))
        );

        ImportResult result = service.importScenario(document);

        assertThat(result.scenarioId()).isEqualTo("test-scenario");
        assertThat(result.usersCreated()).isEqualTo(2);
        assertThat(result.tasksCreated()).isEqualTo(1);
        assertThat(result.availabilitiesCreated()).isEqualTo(1);
        assertThat(result.unavailabilitiesCreated()).isEqualTo(1);
        assertThat(result.schedulesCreated()).isEqualTo(1);
        assertThat(result.tasksScheduled()).isEqualTo(2);

        InOrder inOrder = inOrder(
                userService,
                availabilityService,
                unavailabilityService,
                taskService,
                scheduleService,
                schedulingService
        );
        inOrder.verify(userService, times(2)).create(any(User.class));
        inOrder.verify(availabilityService).create(any());
        inOrder.verify(unavailabilityService).create(any(Unavailability.class));
        inOrder.verify(taskService).create(any(Task.class));
        inOrder.verify(scheduleService).create(any(Schedule.class));
        inOrder.verify(schedulingService).generate(77L);
    }

    @Test
    void importedTasksArePendingAndScheduleIsDraft() {
        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(userService.create(any(User.class))).thenReturn(new User(1L));
        Schedule saved = mock(Schedule.class);
        when(saved.getId()).thenReturn(5L);
        when(scheduleService.create(any(Schedule.class))).thenReturn(saved);
        when(schedulingService.generate(5L))
                .thenReturn(new SchedulingResult(List.of(), List.of()));

        service.importScenario(document(
                List.of(user("alice")),
                List.of(new ScenarioDocument.TaskEntry(
                        "Validate invoice batch September",
                        null,
                        "MEDIUM",
                        90,
                        null
                )),
                List.of(),
                List.of(),
                List.of(new ScenarioDocument.ScheduleEntry(
                        LocalDateTime.of(2027, 2, 1, 8, 0),
                        LocalDateTime.of(2027, 2, 5, 18, 0)
                ))
        ));

        verify(taskService).create(org.mockito.ArgumentMatchers.argThat(
                task -> task.getStatus() == TaskStatus.PENDING));
        verify(scheduleService).create(org.mockito.ArgumentMatchers.argThat(
                schedule -> schedule.getStatus() == ScheduleStatus.DRAFT));
    }

    @Test
    void importRejectsInvalidDocumentWithoutWritingAnything() {
        ScenarioDocument.UserEntry invalid = new ScenarioDocument.UserEntry(
                "alice", "password", "First", "Last",
                "alice@example.com", "WIZARD", true
        );

        assertThatThrownBy(() -> service.importScenario(document(
                List.of(invalid),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        )))
                .isInstanceOf(ImportRejectedException.class)
                .satisfies(error -> assertThat(
                        ((ImportRejectedException) error).getKind()
                ).isEqualTo(ImportRejectedException.Kind.INVALID));

        verifyNoInteractions(userService, taskService, scheduleService);
    }

    @Test
    void importWrapsConflictingUserCreateAsConflictWithStepContext() {
        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(userService.create(any(User.class)))
                .thenThrow(new BusinessRuleException(
                        "Username already exists: alice"
                ));

        assertThatThrownBy(() -> service.importScenario(document(
                List.of(user("alice")),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        )))
                .isInstanceOf(ImportRejectedException.class)
                .satisfies(error -> {
                    ImportRejectedException rejected =
                            (ImportRejectedException) error;
                    assertThat(rejected.getKind())
                            .isEqualTo(ImportRejectedException.Kind.CONFLICT);
                    assertThat(rejected.getProblems().get(0))
                            .startsWith("users[0] (alice):");
                });
    }

    @Test
    void importFromMalformedFileThrowsRejectedWithoutTouchingDatabase() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "scenario.json",
                "application/json",
                "{ not valid json ".getBytes()
        );

        assertThatThrownBy(() -> service.importScenario(file))
                .isInstanceOf(ImportRejectedException.class)
                .satisfies(error -> {
                    ImportRejectedException rejected =
                            (ImportRejectedException) error;
                    assertThat(rejected.getKind())
                            .isEqualTo(ImportRejectedException.Kind.MALFORMED);
                    assertThat(rejected.getProblems().get(0))
                            .contains("not a valid scenario document");
                });

        verifyNoInteractions(userService);
        verify(userRepository, never()).existsByUsername(any());
    }

    @Test
    void validateEmptyFileThrowsRejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.json",
                "application/json",
                new byte[0]
        );

        assertThatThrownBy(() -> service.validate(file))
                .isInstanceOf(ImportRejectedException.class)
                .extracting("kind")
                .isEqualTo(ImportRejectedException.Kind.MALFORMED);
    }
}
