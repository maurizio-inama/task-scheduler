package com.taskscheduler.importer;

import java.util.List;

/**
 * Thrown when a scenario import or validation is rejected. Carries a
 * structured list of problems so the API can return a detailed report
 * instead of a generic error message.
 */
public class ImportRejectedException extends RuntimeException {

    public enum Kind {
        /** The file could not be parsed as JSON / scenario document. */
        MALFORMED,
        /** The document violates schema, reference or business rules. */
        INVALID,
        /** The document conflicts with data already in the database. */
        CONFLICT
    }

    private final Kind kind;
    private final String scenarioId;
    private final List<String> problems;

    public ImportRejectedException(
            Kind kind,
            String scenarioId,
            List<String> problems
    ) {
        super("Scenario import rejected (" + kind + "): " + problems.size()
                + " problem(s)");
        this.kind = kind;
        this.scenarioId = scenarioId;
        this.problems = List.copyOf(problems);
    }

    public Kind getKind() {
        return kind;
    }

    public String getScenarioId() {
        return scenarioId;
    }

    public List<String> getProblems() {
        return problems;
    }
}
