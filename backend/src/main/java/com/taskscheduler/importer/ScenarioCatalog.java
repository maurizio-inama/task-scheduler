package com.taskscheduler.importer;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the built-in demo scenario documents shipped on the classpath under
 * {@code scenarios/*.json}. Files are parsed once at startup; a malformed
 * built-in scenario is a programming error and fails fast.
 */
@Component
public class ScenarioCatalog {

    private static final String LOCATION = "classpath:scenarios/*.json";

    private final ObjectMapper objectMapper;

    private final Map<String, ScenarioDocument> documents = new LinkedHashMap<>();
    private final Map<String, String> prettyJson = new LinkedHashMap<>();

    public ScenarioCatalog(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        loadAll();
    }

    public List<String> fileNames() {
        return List.copyOf(documents.keySet());
    }

    public boolean contains(String fileName) {
        return fileName != null && documents.containsKey(fileName);
    }

    public ScenarioDocument load(String fileName) {
        ScenarioDocument document = documents.get(fileName);
        if (document == null) {
            throw new IllegalArgumentException(
                    "Unknown scenario: " + fileName
            );
        }
        return document;
    }

    public String prettyJson(String fileName) {
        String json = prettyJson.get(fileName);
        if (json == null) {
            throw new IllegalArgumentException(
                    "Unknown scenario: " + fileName
            );
        }
        return json;
    }

    private void loadAll() {
        PathMatchingResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver();
        try {
            for (Resource resource : resolver.getResources(LOCATION)) {
                String fileName = resource.getFilename();
                if (fileName == null) {
                    continue;
                }
                byte[] bytes = resource.getContentAsByteArray();
                ScenarioDocument document =
                        objectMapper.readValue(bytes, ScenarioDocument.class);
                documents.put(
                        fileName,
                        document
                );
                prettyJson.put(
                        fileName,
                        objectMapper.writerWithDefaultPrettyPrinter()
                                .writeValueAsString(document)
                );
            }
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Cannot read built-in scenarios", e
            );
        }
    }
}
