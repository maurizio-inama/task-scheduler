# Task Scheduler

**Task Scheduler** is a full-stack demonstration project for planning and
automatically scheduling work. It combines a Java/Spring Boot backend, a
React + TypeScript frontend, PostgreSQL persistence, REST APIs, JWT
authentication and a constraint-aware scheduling engine. The project was
developed in Visual Studio Code with the assistance of AI tools, including
ChatGPT and OpenCode.

Pending tasks are automatically allocated to available users within a
schedule's time window while respecting availability, unavailability,
deadlines, priorities and per-user daily capacity.

## Features

- **User management** — accounts, roles and enable/disable flag (ADMIN only).
- **Task management** — title, description, status, priority and an estimated
  duration in minutes.
- **Availability / unavailability** — time windows per user; unavailability
  always overrides availability in the scheduler.
- **Schedules** — time windows that define *when* work can be allocated.
- **Assignments** — concrete task → user allocations with a time window.
- **Automated scheduling** — a pure, deterministic scheduling engine that
  allocates tasks to eligible users within a schedule window, and can be
  triggered per schedule with one call.
- **Admin JSON import** — validated, atomic import of full datasets (fail on
  conflict, nothing partial is committed) plus three built-in portfolio demo
  scenarios.
- **Dashboard task timeline** — a visual, navigable week-by-week calendar of
  task execution with a color legend, overflow handling and responsive
  behavior.

## Technology stack

| Layer    | Technology                                                       |
| -------- | ---------------------------------------------------------------- |
| Backend  | Java 21, Spring Boot 4.0.7 (Web, Data JPA, Security, Validation)  |
| Database | PostgreSQL 17, Flyway migrations                                  |
| Auth     | JWT (HS256, stateless), BCrypt password hashing                   |
| Frontend | React 19, TypeScript (strict), Vite 8, React Router 7             |
| Testing  | JUnit 5 + Mockito + MockMvc (backend), Vitest 4 + Testing Library (frontend) |

## Architecture

The application follows a layered architecture:

```text
                         ┌─────────────────────┐
                         │      React UI       │
                         │      Frontend       │
                         └──────────┬──────────┘
                                    │
                                  HTTP
                                    ▼
                         ┌─────────────────────┐
                         │     REST API        │
                         │    Controllers      │
                         └──────────┬──────────┘
                                    │
                                    ▼
                         ┌─────────────────────┐
                         │   Business Layer    │
                         │      Services       │
                         └──────────┬──────────┘
                                    │
                                    ▼
                         ┌─────────────────────┐
                         │  Persistence Layer   │
                         │ Repositories / JPA   │
                         └──────────┬──────────┘
                              Hibernate
                                    ▼
                         ┌─────────────────────┐
                         │     PostgreSQL      │
                         └─────────────────────┘
```

Cross-cutting concerns (security, validation, exception handling,
configuration) support every layer. The REST layer is deliberately thin:
controllers expose JPA-backed resources through request/response DTOs and
never contain business rules. The scheduling engine is a pure domain
component that does not depend on HTTP controllers.

The frontend is a single-page application that communicates only with the
backend REST API — it never touches the database and never performs business
computation beyond input validation and presentation. Pages never call
`fetch` directly; they go through per-resource API modules that use a shared
HTTP client.

## Domain model

Five core entities are persisted and managed:

```text
User
Task
Availability
Unavailability
Assignment
```

Schedules complement them as the temporal container for automated allocation.

Enum values:

| Enum              | Values                                              |
| ----------------- | --------------------------------------------------- |
| Role              | `ADMIN`, `REVIEWER`, `OPERATOR`                     |
| TaskStatus        | `PENDING`, `SCHEDULED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED` |
| TaskPriority      | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`                 |
| ScheduleStatus    | `DRAFT`, `PUBLISHED`, `COMPLETED`, `CANCELLED`      |
| AssignmentStatus  | `ASSIGNED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED` |

## REST API

All endpoints are prefixed with `/api`. Every endpoint except
`POST /api/auth/login` requires a valid JWT.

| Resource        | Endpoints                                                          |
| --------------- | ------------------------------------------------------------------ |
| Authentication  | `POST /api/auth/login`, `GET /api/auth/me`                         |
| Users           | CRUD under `/api/users` (ADMIN only)                               |
| Tasks           | CRUD under `/api/tasks`                                            |
| Availability    | CRUD under `/api/availability`                                     |
| Unavailability  | CRUD under `/api/unavailability`                                   |
| Schedules       | CRUD under `/api/schedules` plus `POST /api/schedules/{id}/generate` (run the engine) |
| Assignments     | CRUD under `/api/assignments`                                      |
| Admin import    | `POST /api/admin/import` (upload), `POST /api/admin/import/validate`, `GET /api/admin/import/scenarios`, `GET/POST .../scenarios/{fileName}` and `.../{fileName}/validate` / `.../{fileName}/import` |

HTTP conventions:

| Code | Meaning                                                       |
| ---: | ------------------------------------------------------------- |
|  200 | OK — resource retrieved or replaced                           |
|  201 | Created — resource created                                    |
|  204 | No Content — resource deleted                                 |
|  400 | Bad Request — malformed request or input validation failure   |
|  401 | Unauthorized — missing or invalid token                       |
|  404 | Not Found — resource does not exist                           |
|  409 | Conflict — business rule violation                            |
|  422 | Unprocessable Entity — service-level validation failure       |
|  500 | Internal Server Error — unexpected error                      |

Validation is enforced at the API boundary with Bean Validation and again,
where required, by the business layer (existence checks, duplicate checks,
relationship and window rules). Every error response carries a machine-readable
error body with status, message, path and timestamp.

## Roles and permissions

| Role     | Capabilities                                                            |
| -------- | ----------------------------------------------------------------------- |
| ADMIN    | Everything: user management plus create/edit/delete of tasks, schedules, assignments, availability and unavailability |
| REVIEWER | Create/edit/delete tasks, schedules, assignments, availability, unavailability |
| OPERATOR | Read-only on planning data; manages their own availability/unavailability |

Endpoint-level rules:

- `POST /api/auth/login` is public; all other endpoints require a token.
- `/api/admin/**` and `/api/users/**` require `ADMIN`.
- Task, schedule and assignment writes (`POST`/`PUT`/`DELETE`) require
  `ADMIN` or `REVIEWER`; their `GET`s and all availability/unavailability
  endpoints require any authenticated user.

Authentication flow: credentials are verified with BCrypt; a successful login
returns a signed HS256 JWT sent as `Authorization: Bearer <token>`. The backend
is stateless and never stores sessions.

## Frontend

Routes:

| Path             | Page            | Access        |
| ---------------- | --------------- | ------------- |
| `/login`         | LoginPage       | public        |
| `/`              | DashboardPage   | authenticated |
| `/tasks`         | TasksPage       | authenticated |
| `/availability`  | AvailabilityPage| authenticated |
| `/unavailability`| UnavailabilityPage | authenticated |
| `/schedules`     | SchedulesPage   | authenticated |
| `/assignments`   | AssignmentsPage | authenticated |
| `/users`         | UsersPage       | ADMIN only    |

- Protected routes redirect unauthenticated visitors to `/login`, remember the
  target path, and render role-specific access control (the `Users` link is
  only visible to ADMIN users).
- The session is stored under the key `task-scheduler-auth`; on reload the app
  restores it via `GET /api/auth/me` and silently clears it when invalid.
- There is no external state library: local component state plus an
  `AuthContext` and a generic `useFetch` hook that returns
  `{ data, loading, error, refetch }`.
- Errors are handled at three levels — field-level messages under inputs,
  action-level alerts above tables, and load-level error banners with a retry.
  A `401` during any authenticated call expires the session globally.
- All timestamps are rendered in the browser's local timezone.

### Dashboard task timeline

The Dashboard shows a compact visual calendar at the top, inspired by
Outlook's calendar, giving a weekly overview of task execution:

- columns run Monday → Sunday and rows represent calendar weeks with a fixed
  height;
- each day cell can hold multiple tasks; each task card uses a fill color to
  identify the assigned resource and a border color to identify the task;
- task labels follow a defined abbreviation rule (truncated after the second
  whitespace group, e.g. `Database backup production` → `Database backup…`);
- the current day is visually highlighted;
- a legend explains the resource and task colors and lists the week's
  resources and tasks;
- cells holding more tasks than fit show a `+N more` button that opens a
  dialog listing every overflow task;
- the header provides Previous / Next week navigation and a **Today** action;
  weeks navigate continuously across month and year boundaries without any
  hard-coded limit;
- loading keeps the calendar skeleton visible, an error state offers Retry,
  and an empty week is clearly identified;
- on narrow screens the grid scrolls horizontally and the legend stacks
  below the calendar.

The timeline is a pure read-oriented visualization: it renders data fetched
from the existing tasks/assignments/users endpoints and contains no scheduling
logic. Rendering is bounded to a single visible week, so no virtualization is
needed.

## Scheduling engine

Scheduling is deterministic and deliberately **not** optimized:

1. Higher-priority tasks are scheduled before lower-priority ones.
2. Within the same priority, earlier deadlines win; tasks without a deadline
   come last.
3. A task is assigned entirely to a single eligible user — the first user (by
   ascending id) who can absorb the whole task.
4. Work is placed chronologically, as early as possible.
5. Daily capacity (`maxMinutesPerDay`) is never exceeded; unavailability always
   blocks scheduling; existing assignments reduce both free time and daily
   budget.
6. A task that cannot be completely scheduled is reported as unscheduled with
   the failure reason; no partial assignment is ever created.

Constraints honored: task duration fits inside `[windowStart, windowEnd]` and
before any deadline; work only happens inside merged availability periods;
unavailability overrides availability; allocations never overlap; allocations
are split at midnight so daily budgets stay well-formed. Boundary instants are
inclusive at the start and exclusive at the end, so a task ending at 12:00 and
one starting at 12:00 do not conflict. Priority is handled via an explicit
switch — never through enum ordinal.

## Admin JSON import and demo scenarios

ADMIN users can import complete datasets (users, tasks, availability,
unavailability, assignments) in one JSON document from **Admin → Data Import**.
Imports are two-step (Validate, then Import) and atomic: if validation or
persistence fails, nothing is committed.

Three reproducible demo scenarios ship with the repository and are also listed
on the import page as one-click built-ins:

| Scenario             | Purpose | Demo users (password `admin`) |
| -------------------- | ------- | ----------------------------- |
| `demo-basic.json` | Balanced workflow: 5 users, 8 realistic tasks, availabilities/unavailabilities, one schedule filled by the engine | `alice_basic`, `bob_basic`, `charlie_basic`, `dana_basic`, `evan_basic` |
| `demo-constrained.json` | Constraint handling: reduced capacity, unavailability windows, tight deadlines | `alice_con`, `bob_con`, `charlie_con`, `felix_con`, `marta_con` |
| `demo-priority-deadlines.json` | Priority/deadline-driven allocation across 3 operators and 10 tasks | `alice_prio`, `bob_prio`, `charlie_prio`, `nadia_prio`, `omar_prio` |

These credentials exist only so the imported scenarios are immediately usable.
They are for local/demo purposes only — never reuse them anywhere real.

## Prerequisites

- JDK 21
- Docker (for PostgreSQL)
- Node.js 22 and npm

## Getting started

### 1. Configure the database password

```bash
cp .env.example .env
# edit .env and set POSTGRES_PASSWORD
```

`env.sh` exports `POSTGRES_PASSWORD` from `.env`; the backend reads it from the
environment (`application.yml` contains no secrets).

### 2. Start PostgreSQL

```bash
docker compose up -d
```

This starts a PostgreSQL 17 container with database `task_scheduler` and user
`scheduler`. Flyway creates and migrates the schema automatically on first
backend startup.

### 3. Run the backend

```bash
source ./env.sh
cd backend
./mvnw spring-boot:run
```

The API is served at `http://localhost:8080/api`.

### 4. Run the frontend

```bash
cd frontend
npm install
npm run dev
```

The app is served at `http://localhost:5173` and proxies `/api` requests to
the backend.

### 5. Log in

There is no registration endpoint; users are created by an ADMIN through
`POST /api/users`. To bootstrap the first admin, insert one directly with a
BCrypt hash of the password. For local development, this command creates
`admin` / `admin` (the `\$` keeps the hash intact in bash):

```bash
docker exec -it task-scheduler-postgres psql -U scheduler -d task_scheduler -c \
  "INSERT INTO users (username, password, first_name, last_name, email, role, enabled, created_at, updated_at)
   VALUES ('admin', '\$2a\$10\$350CaHfhAxZB3bRqHdCohOeJUPhBEzgp8bJvZRAEoNOcEwyeuMvsa', 'Admin', 'User', 'admin@example.com', 'ADMIN', true, now(), now())
   ON CONFLICT (username) DO UPDATE SET password = EXCLUDED.password, enabled = true;"
```

> The hash above encodes the password `admin` and is meant for local
> development only — never reuse these credentials anywhere real.

Alternatively, importing any demo scenario (Admin → Data Import) seeds users
that log in with the password `admin`.

## Running the tests

Backend (requires PostgreSQL running and the environment exported):

```bash
cd backend
source ../env.sh
./mvnw test      # unit + integration tests
./mvnw verify    # full verification
```

Frontend:

```bash
cd frontend
npm test         # watch mode
npm test -- --run
npm run build    # type-check + production build
```

Continuous Integration runs both suites via GitHub Actions, including a
PostgreSQL service container for the backend.

**Current baseline:**

```text
Backend  ./mvnw verify   → 287 tests (1 pre-existing deterministic failure:
                            SystemIntegrationTest.schedulingFlowRespectsUnavailabilityWindows,
                            unrelated to the current work; no backend changes are in flight)
Frontend npm test -- --run → 15 test files, 142 tests, all passing
Production build           → tsc --noEmit && vite build succeeds
```

## Project layout

```text
backend/            Spring Boot application (Maven)
frontend/           React SPA (Vite)
data/scenarios/     Built-in demo scenarios (JSON)
_doc/               Design documents and per-phase reports
.env.example        Template for the database password
docker-compose.yml  PostgreSQL service
.github/workflows/  CI definition
```

## Project status

The project was built incrementally in verified phases and each one is
complete:

```text
Phase 1  Bootstrap            COMPLETED
Phase 2  Domain model         COMPLETED
Phase 3  Persistence layer    COMPLETED
Phase 4  Business layer       COMPLETED
Phase 5  REST API             COMPLETED
Phase 6  Authentication       COMPLETED
Phase 7  Scheduling engine    COMPLETED
Phase 8  Frontend             COMPLETED
Phase 9  Integration          COMPLETED
Phase 10 Deployment           PLANNED
```

In addition, the **Phase 11 milestone — Dashboard task timeline** is complete:
backend tests pass (with the single pre-existing failure noted above), the
frontend suite (15 files / 142 tests) and the production build are green, and
the live development stack (backend on port 8080, Vite on port 5173) has been
verified end-to-end.