package com.taskscheduler.controller;

import com.taskscheduler.controller.dto.ImportResult;
import com.taskscheduler.controller.dto.ScenarioPreview;
import com.taskscheduler.controller.dto.ScenarioSummary;
import com.taskscheduler.controller.dto.ValidationReport;
import com.taskscheduler.exception.EntityNotFoundException;
import com.taskscheduler.importer.ScenarioCatalog;
import com.taskscheduler.importer.ScenarioDocument;
import com.taskscheduler.service.ScenarioImportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin/import")
public class AdminImportController {

    private final ScenarioImportService importService;
    private final ScenarioCatalog scenarioCatalog;

    public AdminImportController(
            ScenarioImportService importService,
            ScenarioCatalog scenarioCatalog
    ) {
        this.importService = importService;
        this.scenarioCatalog = scenarioCatalog;
    }

    @PostMapping("/validate")
    public ValidationReport validateFile(
            @RequestParam("file") MultipartFile file
    ) {
        return importService.validate(file);
    }

    @PostMapping
    public ImportResult importFile(
            @RequestParam("file") MultipartFile file
    ) {
        return importService.importScenario(file);
    }

    @GetMapping("/scenarios")
    public List<ScenarioSummary> scenarios() {
        return scenarioCatalog.fileNames().stream()
                .map(this::summaryOf)
                .toList();
    }

    @GetMapping("/scenarios/{fileName}")
    public ScenarioPreview preview(@PathVariable String fileName) {
        requireKnown(fileName);
        ScenarioDocument document = scenarioCatalog.load(fileName);
        return new ScenarioPreview(
                fileName,
                document.scenario() == null ? null : document.scenario().id(),
                document.scenario() == null ? null : document.scenario().name(),
                document.scenario() == null
                        ? null
                        : document.scenario().description(),
                scenarioCatalog.prettyJson(fileName)
        );
    }

    @PostMapping("/scenarios/{fileName}/validate")
    public ValidationReport validateBuiltIn(@PathVariable String fileName) {
        requireKnown(fileName);
        return importService.validate(scenarioCatalog.load(fileName));
    }

    @PostMapping("/scenarios/{fileName}/import")
    public ImportResult importBuiltIn(@PathVariable String fileName) {
        requireKnown(fileName);
        return importService.importScenario(scenarioCatalog.load(fileName));
    }

    private ScenarioSummary summaryOf(String fileName) {
        ScenarioDocument document = scenarioCatalog.load(fileName);
        return new ScenarioSummary(
                fileName,
                document.scenario() == null ? null : document.scenario().id(),
                document.scenario() == null ? null : document.scenario().name(),
                document.scenario() == null
                        ? null
                        : document.scenario().description()
        );
    }

    private void requireKnown(String fileName) {
        if (!scenarioCatalog.contains(fileName)) {
            throw new EntityNotFoundException(
                    "Unknown scenario: " + fileName
            );
        }
    }
}
