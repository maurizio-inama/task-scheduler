package com.taskscheduler.controller;

import com.taskscheduler.controller.dto.ImportResult;
import com.taskscheduler.controller.dto.ValidationReport;
import com.taskscheduler.importer.ImportRejectedException;
import com.taskscheduler.importer.ScenarioCatalog;
import com.taskscheduler.importer.ScenarioDocument;
import com.taskscheduler.service.ScenarioImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminImportController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScenarioImportService importService;

    @MockitoBean
    private ScenarioCatalog scenarioCatalog;

    private final ScenarioDocument demoDocument =
            new ScenarioDocument(
                    new ScenarioDocument.ScenarioMeta(
                            "demo-basic",
                            "Basic Scheduling Demo",
                            "Balanced scenario"
                    ),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of()
            );

    @BeforeEach
    void setUp() {
        when(scenarioCatalog.fileNames())
                .thenReturn(List.of("demo-basic.json"));
        when(scenarioCatalog.load("demo-basic.json")).thenReturn(demoDocument);
        when(scenarioCatalog.contains("demo-basic.json")).thenReturn(true);
    }

    @Test
    void shouldValidateUploadedScenarioFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "scenario.json",
                MediaType.APPLICATION_JSON_VALUE,
                "{}".getBytes()
        );
        when(importService.validate(any(org.springframework.web.multipart.MultipartFile.class)))
                .thenReturn(new ValidationReport(
                        true,
                        "VALID",
                        "demo-basic",
                        "Basic Scheduling Demo",
                        new ValidationReport.Counts(5, 8, 6, 2, 1),
                        List.of()
                ));

        mockMvc.perform(multipart("/api/admin/import/validate").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.scenarioId").value("demo-basic"))
                .andExpect(jsonPath("$.counts.users").value(5))
                .andExpect(jsonPath("$.counts.schedules").value(1));
    }

    @Test
    void shouldImportUploadedScenarioFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "scenario.json",
                MediaType.APPLICATION_JSON_VALUE,
                "{}".getBytes()
        );
        when(importService.importScenario(any(org.springframework.web.multipart.MultipartFile.class)))
                .thenReturn(new ImportResult(
                        "demo-basic", "Basic Scheduling Demo",
                        5, 8, 6, 2, 1, 7
                ));

        mockMvc.perform(multipart("/api/admin/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenarioId").value("demo-basic"))
                .andExpect(jsonPath("$.usersCreated").value(5))
                .andExpect(jsonPath("$.tasksCreated").value(8))
                .andExpect(jsonPath("$.tasksScheduled").value(7));
    }

    @Test
    void shouldRejectMalformedUploadWithProblems() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "scenario.json",
                MediaType.APPLICATION_JSON_VALUE,
                "broken".getBytes()
        );
        when(importService.validate(any(org.springframework.web.multipart.MultipartFile.class)))
                .thenThrow(new ImportRejectedException(
                        ImportRejectedException.Kind.MALFORMED,
                        null,
                        List.of("File is not a valid scenario document: oops")
                ));

        mockMvc.perform(multipart("/api/admin/import/validate").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("IMPORT_REJECTED"))
                .andExpect(jsonPath("$.kind").value("MALFORMED"))
                .andExpect(jsonPath("$.problems[0]")
                        .value("File is not a valid scenario document: oops"));
    }

    @Test
    void shouldRequireFilePart() throws Exception {
        mockMvc.perform(multipart("/api/admin/import/validate"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Required request part 'file' is missing"));
    }

    @Test
    void shouldListBuiltInScenarios() throws Exception {
        mockMvc.perform(get("/api/admin/import/scenarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileName").value("demo-basic.json"))
                .andExpect(jsonPath("$[0].scenarioId").value("demo-basic"))
                .andExpect(jsonPath("$[0].name").value("Basic Scheduling Demo"));
    }

    @Test
    void shouldPreviewBuiltInScenario() throws Exception {
        when(scenarioCatalog.prettyJson("demo-basic.json"))
                .thenReturn("{\n  \"scenario\": {}\n}");

        mockMvc.perform(get("/api/admin/import/scenarios/demo-basic.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("demo-basic.json"))
                .andExpect(jsonPath("$.content").isNotEmpty());
    }

    @Test
    void shouldReturnNotFoundForUnknownScenario() throws Exception {
        when(scenarioCatalog.contains("nope.json")).thenReturn(false);
        when(scenarioCatalog.load("nope.json"))
                .thenThrow(new IllegalArgumentException("Unknown scenario"));

        mockMvc.perform(get("/api/admin/import/scenarios/nope.json"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ENTITY_NOT_FOUND"));
    }

    @Test
    void shouldValidateAndImportBuiltInScenario() throws Exception {
        when(importService.validate(demoDocument))
                .thenReturn(new ValidationReport(
                        true, "VALID", "demo-basic",
                        "Basic Scheduling Demo",
                        new ValidationReport.Counts(5, 8, 6, 2, 1),
                        List.of()
                ));
        when(importService.importScenario(demoDocument))
                .thenReturn(new ImportResult(
                        "demo-basic", "Basic Scheduling Demo",
                        5, 8, 6, 2, 1, 8
                ));

        mockMvc.perform(postBuiltIn("/scenarios/demo-basic.json/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"));

        mockMvc.perform(postBuiltIn("/scenarios/demo-basic.json/import"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedulesCreated").value(1));
    }

    private org.springframework.test.web.servlet.RequestBuilder postBuiltIn(
            String path
    ) {
        return multipart("/api/admin/import" + path);
    }

    @Test
    void shouldReturnConflictWhenBuiltInImportConflicts() throws Exception {
        when(importService.importScenario(demoDocument))
                .thenThrow(new ImportRejectedException(
                        ImportRejectedException.Kind.CONFLICT,
                        "demo-basic",
                        List.of("users[0]: username 'alice_basic' already exists")
                ));

        mockMvc.perform(postBuiltIn("/scenarios/demo-basic.json/import"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.kind").value("CONFLICT"))
                .andExpect(jsonPath("$.problems[0]").value(
                        "users[0]: username 'alice_basic' already exists"));
    }

    @Test
    void entityNotFoundFromCatalogMapsTo404() throws Exception {
        when(scenarioCatalog.contains("ghost.json")).thenReturn(false);

        mockMvc.perform(get("/api/admin/import/scenarios/ghost.json"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ENTITY_NOT_FOUND"));
    }
}
