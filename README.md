# Task Scheduler

A full-stack application for planning work: pending tasks are automatically
allocated to available users inside a schedule's time window by a scheduling
engine that respects availability, unavailability, deadlines and capacity.

## Stack

| Layer    | Technology                                                        |
| -------- | ----------------------------------------------------------------- |
| Backend  | Java 21, Spring Boot 3.5 (Web, Data JPA, Security, Validation)     |
| Database | PostgreSQL 17, Flyway migrations                                   |
| Auth     | JWT (stateless), BCrypt password hashing                           |
| Frontend | React 19, TypeScript, Vite, React Router 7                         |
| Testing  | JUnit 5 + Mockito + MockMvc (backend), Vitest + Testing Library (frontend) |

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
environment (`application.yml` does not contain secrets).

### 2. Start PostgreSQL

```bash
docker compose up -d
```

This starts a PostgreSQL 17 container with database `task_scheduler` and user
`scheduler`. Flyway creates and migrates the schema automatically on first
backend startup.

### 3. Run the backend

```bash
cd backend
source ../env.sh
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
`POST /api/users`. To bootstrap the first admin, insert one directly (any
BCrypt hash works for local development):

```bash
docker exec -it task-scheduler-postgres psql -U scheduler -d task_scheduler -c \
  "INSERT INTO users (username, password, first_name, last_name, email, role, enabled, created_at, updated_at)
   VALUES ('admin', '<bcrypt-hash>', 'Admin', 'User', 'admin@example.com', 'ADMIN', true, now(), now());"
```

## Roles

| Role     | Capabilities                                                            |
| -------- | ----------------------------------------------------------------------- |
| ADMIN    | Everything: user management plus create/edit/delete of tasks, schedules, assignments, availability and unavailability |
| REVIEWER | Create/edit/delete tasks, schedules, assignments, availability, unavailability |
| OPERATOR | Read-only on planning data; manages their own availability/unavailability |

All endpoints except `POST /api/auth/login` require a valid JWT
(`Authorization: Bearer <token>`).

## Running the tests

Backend (requires PostgreSQL running):

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

Continuous Integration runs both suites via GitHub Actions
(`.github/workflows/ci.yml`), including a PostgreSQL service container for the
backend.

## Project layout

```
backend/    Spring Boot application (Maven)
frontend/   React SPA (Vite)
_doc/       Design documents and phase reports
.env.example  Template for the database password
docker-compose.yml  PostgreSQL service
```

See `_doc/1_task-scheduler.md` for the overall design and phase history.
