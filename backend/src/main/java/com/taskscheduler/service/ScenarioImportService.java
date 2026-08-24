package com.taskscheduler.service;

import com.taskscheduler.controller.dto.ImportResult;
import com.taskscheduler.controller.dto.ValidationReport;
import com.taskscheduler.importer.ScenarioDocument;
import org.springframework.web.multipart.MultipartFile;

public interface ScenarioImportService {

    /**
     * Validates an uploaded scenario file without touching the database.
     */
    ValidationReport validate(MultipartFile file);

    /**
     * Validates a built-in scenario document without touching the database.
     */
    ValidationReport validate(ScenarioDocument document);

    /**
     * Imports an uploaded scenario file atomically: either every entity is
     * created or the database is left unchanged.
     */
    ImportResult importScenario(MultipartFile file);

    /**
     * Imports a built-in scenario document atomically.
     */
    ImportResult importScenario(ScenarioDocument document);
}
