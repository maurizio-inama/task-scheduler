import { useRef, useState } from 'react';
import { ApiRequestError } from '../api/client';
import { importApi } from '../api/importApi';
import { Badge } from '../components/Badge';
import { EmptyState } from '../components/EmptyState';
import { Loading } from '../components/Loading';
import { useFetch } from '../hooks/useFetch';
import type {
  ImportResult,
  ScenarioSummary,
  ValidationReport,
} from '../types/api';

type Phase = 'IDLE' | 'VALID' | 'INVALID' | 'CONFLICT' | 'IMPORTED';

const PHASE_BADGE: Record<Phase, string> = {
  IDLE: '',
  VALID: 'VALID',
  INVALID: 'INVALID',
  CONFLICT: 'CONFLICT',
  IMPORTED: 'IMPORTED',
};

export function AdminImportPage() {
  const [file, setFile] = useState<File | null>(null);
  const [phase, setPhase] = useState<Phase>('IDLE');
  const [report, setReport] = useState<ValidationReport | null>(null);
  const [result, setResult] = useState<ImportResult | null>(null);
  const [problems, setProblems] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const {
    data: scenarios,
    loading: scenariosLoading,
  } = useFetch(() => importApi.listScenarios(), []);

  const [scenarioResults, setScenarioResults] = useState<
    Record<string, { phase: Phase; report?: ValidationReport; result?: ImportResult }>
  >({});
  const fileInputRef = useRef<HTMLInputElement>(null);

  const resetOutcome = () => {
    setPhase('IDLE');
    setReport(null);
    setResult(null);
    setProblems([]);
    setError(null);
  };

  const selectFile = (selected: File | null) => {
    setFile(selected);
    resetOutcome();
  };

  const handleValidate = async () => {
    if (!file) return;
    setBusy(true);
    setError(null);
    try {
      const validationReport = await importApi.validateFile(file);
      setReport(validationReport);
      setResult(null);
      setPhase(validationReport.valid ? 'VALID' : validationReport.status);
      if (!validationReport.valid) {
        setProblems(validationReport.problems);
      }
    } catch (cause) {
      describeFailure(cause);
    } finally {
      setBusy(false);
    }
  };

  const handleImport = async () => {
    if (!file || (report !== null && !report.valid)) return;
    setBusy(true);
    setError(null);
    try {
      const importResult = await importApi.importFile(file);
      setResult(importResult);
      setPhase('IMPORTED');
    } catch (cause) {
      describeFailure(cause);
    } finally {
      setBusy(false);
    }
  };

  const describeFailure = (cause: unknown) => {
    if (cause instanceof ApiRequestError) {
      setPhase(
        cause.code === 'IMPORT_REJECTED'
          ? cause.status === 409
            ? 'CONFLICT'
            : 'INVALID'
          : 'IDLE',
      );
      setProblems(cause.problems);
      setError(cause.message);
      return;
    }
    setError(cause instanceof Error ? cause.message : 'Unexpected error.');
  };

  const runScenario = async (
    scenario: ScenarioSummary,
    action: 'validate' | 'import',
  ) => {
    setBusy(true);
    setError(null);
    try {
      if (action === 'validate') {
        const validationReport = await importApi.validateScenario(
          scenario.fileName,
        );
        setScenarioResults((previous) => ({
          ...previous,
          [scenario.fileName]: {
            phase: validationReport.valid
              ? 'VALID'
              : validationReport.status,
            report: validationReport,
          },
        }));
      } else {
        const importResult = await importApi.importScenario(scenario.fileName);
        setScenarioResults((previous) => ({
          ...previous,
          [scenario.fileName]: { phase: 'IMPORTED', result: importResult },
        }));
      }
    } catch (cause) {
      if (cause instanceof ApiRequestError) {
        setError(
          cause.problems.length > 0
            ? `${scenario.name ?? scenario.fileName}: ${cause.problems.join(' ')}`
            : cause.message,
        );
        return;
      }
      setError(cause instanceof Error ? cause.message : 'Unexpected error.');
    } finally {
      setBusy(false);
    }
  };

  const counts = report?.counts;

  return (
    <div className="page">
      <div className="page-header">
        <h1>Data Import</h1>
      </div>

      <p>
        Upload a JSON scenario file to validate it against the current database
        and import a complete scheduling scenario. Imports are atomic: if
        validation or persistence fails, no partial data is committed.
      </p>

      {error && (
        <div className="alert alert-error" role="alert">
          {error}
        </div>
      )}

      <section aria-label="Upload JSON scenario" className="card">
        <h2>Upload scenario file</h2>
        <div className="import-panel">
          <label className="import-file-label" htmlFor="import-file">
            Scenario JSON
          </label>
          <input
            ref={fileInputRef}
            id="import-file"
            type="file"
            accept=".json,application/json"
            onChange={(event) =>
              selectFile(event.target.files?.[0] ?? null)
            }
            disabled={busy}
          />
          {file && (
            <span data-testid="selected-file">{file.name}</span>
          )}
          <button
            type="button"
            className="btn btn-secondary"
            onClick={handleValidate}
            disabled={!file || busy}
          >
            {busy ? 'Working…' : 'Validate'}
          </button>
          <button
            type="button"
            className="btn btn-primary"
            onClick={handleImport}
            disabled={!file || busy || (report !== null && !report.valid)}
          >
            Import
          </button>
          {phase !== 'IDLE' && phase !== 'IMPORTED' && (
            <Badge value={phase}>{PHASE_BADGE[phase]}</Badge>
          )}
          {phase === 'IMPORTED' && <Badge value="imported">IMPORTED</Badge>}
        </div>

        {report && !result && (
          <div data-testid="validation-report">
            <h3>
              {report.valid ? 'Validation successful' : 'Validation failed'}
            </h3>
            {report.scenarioName && <p>{report.scenarioName}</p>}
            <p>
              Users: {counts?.users ?? 0} · Tasks: {counts?.tasks ?? 0} ·
              Availabilities: {counts?.availabilities ?? 0} · Unavailabilities:{' '}
              {counts?.unavailabilities ?? 0} · Schedules:{' '}
              {counts?.schedules ?? 0}
            </p>
            {!report.valid && (
              <>
                <p>No data was imported.</p>
                <ul className="import-problems">
                  {problems.map((problem) => (
                    <li key={problem}>{problem}</li>
                  ))}
                </ul>
              </>
            )}
          </div>
        )}

        {result && (
          <div data-testid="import-result">
            <h3>Import completed</h3>
            <div className="import-summary">
              <div className="import-summary-item">
                <span className="import-summary-value">
                  {result.usersCreated}
                </span>
                Users created
              </div>
              <div className="import-summary-item">
                <span className="import-summary-value">
                  {result.tasksCreated}
                </span>
                Tasks created
              </div>
              <div className="import-summary-item">
                <span className="import-summary-value">
                  {result.availabilitiesCreated}
                </span>
                Availabilities created
              </div>
              <div className="import-summary-item">
                <span className="import-summary-value">
                  {result.unavailabilitiesCreated}
                </span>
                Unavailabilities created
              </div>
              <div className="import-summary-item">
                <span className="import-summary-value">
                  {result.schedulesCreated}
                </span>
                Schedules created
              </div>
              <div className="import-summary-item">
                <span className="import-summary-value">
                  {result.tasksScheduled}
                </span>
                Tasks scheduled
              </div>
            </div>
          </div>
        )}
      </section>

      <section aria-label="Built-in demo scenarios" className="card">
        <h2>Built-in demo scenarios</h2>
        {scenariosLoading ? (
          <Loading label="Loading demo scenarios…" />
        ) : !scenarios || scenarios.length === 0 ? (
          <EmptyState message="No built-in scenarios found." />
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>Scenario</th>
                <th>Description</th>
                <th>Status</th>
                <th aria-label="Actions" />
              </tr>
            </thead>
            <tbody>
              {scenarios.map((scenario) => {
                const outcome = scenarioResults[scenario.fileName];
                return (
                  <tr key={scenario.fileName}>
                    <td>
                      {scenario.name ?? scenario.fileName}
                      <span className="cell-subtitle">
                        {scenario.fileName}
                      </span>
                    </td>
                    <td className="cell-muted">
                      {scenario.description ?? '—'}
                    </td>
                    <td>
                      {outcome && (
                        <Badge value={outcome.phase}>
                          {PHASE_BADGE[outcome.phase]}
                        </Badge>
                      )}
                    </td>
                    <td className="table-actions">
                      <button
                        type="button"
                        className="btn btn-secondary"
                        onClick={() => runScenario(scenario, 'validate')}
                        disabled={busy}
                      >
                        Validate
                      </button>
                      <button
                        type="button"
                        className="btn btn-primary"
                        onClick={() => runScenario(scenario, 'import')}
                        disabled={
                          busy ||
                          (outcome?.report !== undefined &&
                            !outcome.report.valid)
                        }
                      >
                        Import
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
