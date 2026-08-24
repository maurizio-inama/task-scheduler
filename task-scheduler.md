from pathlib import Path

content = """# Task Scheduler

## 1. Project Overview

**Task Scheduler** is a small full-stack scheduling application designed to demonstrate a modern Java/Spring backend architecture integrated with PostgreSQL.

The project is intentionally limited in scope, but it will implement the main concepts expected in a professional enterprise application:

- Java 21
- Spring Boot
- Maven
- Spring Web / REST
- Spring Data JPA
- Hibernate ORM
- PostgreSQL
- Docker / Docker Compose
- Bean Validation
- React frontend (planned)
- REST API between frontend and backend

The project will be developed incrementally. This document is the technical reference for the project and will be updated as architectural and implementation decisions are made.

---

## 2. Main Goal

The application will allow an organization to manage tasks and assign them to available users according to their roles, availability, and scheduling constraints.

The initial version will support three conceptual roles:

- **ADMIN** — manages users and system configuration.
- **REVIEWER** — reviews tasks and assignments.
- **OPERATOR** — performs assigned tasks.

The core scheduling workflow is:

1. Create users.
2. Create tasks.
3. Define user availability.
4. Assign tasks to suitable users.
5. Create and manage scheduled assignments.
6. Expose the data through REST APIs.
7. Provide a frontend for interaction.

The scheduling algorithm will initially be simple and deterministic. More advanced optimization can be added later without changing the fundamental data model.

---

## 3. Architecture

The planned architecture is:

```text
+-------------------+
|   React Frontend  |
+---------+---------+
          |
          | HTTP / JSON
          v
+-------------------+
| Spring Boot REST  |
|      API          |
+---------+---------+
          |
          v
+-------------------+
| Service Layer     |
| Business Logic    |
+---------+---------+
          |
          v
+-------------------+
| Spring Data JPA   |
|     / Hibernate   |
+---------+---------+
          |
          | JDBC
          v
+-------------------+
|    PostgreSQL     |
+-------------------+

4. Repository Structure

The target repository structure is:

task_scheduler/
├── docker-compose.yml
├── pom.xml
├── task-scheduler.md
├── .gitignore
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/taskscheduler/
│   │   │       ├── TaskSchedulerApplication.java
│   │   │       ├── entity/
│   │   │       ├── repository/
│   │   │       ├── service/
│   │   │       ├── controller/
│   │   │       ├── dto/
│   │   │       └── exception/
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/
│           └── com/example/taskscheduler/
└── ...

The exact package structure may evolve as implementation progresses.

5. Technology Stack
Layer	Technology
Language	Java 21
Build	Maven
Backend framework	Spring Boot 3.x
REST API	Spring Web
Persistence	Spring Data JPA
ORM	Hibernate
Database	PostgreSQL
Database connectivity	PostgreSQL JDBC Driver
Validation	Jakarta Bean Validation
Containerization	Docker / Docker Compose
Frontend	React
Version control	Git
6. Domain Model

The first version will contain five JPA entities.

6.1 User

Represents a person who can interact with the scheduling system.

Expected attributes include:

id
username
email
firstName
lastName
role
active

The exact fields and constraints will be defined during implementation.

6.2 Task

Represents an activity that must be performed.

Expected attributes include:

id
title
description
priority
status
estimatedDuration
createdAt
dueDate

A task may eventually be associated with one or more scheduling records.

6.3 Availability

Represents the time period during which a user is available.

Expected attributes include:

id
user
startDateTime
endDateTime

Availability must satisfy temporal constraints such as:

startDateTime < endDateTime
6.4 Assignment

Represents the relationship between a task and the user responsible for performing it.

Expected attributes include:

id
task
user
assignedAt
status

This entity provides an explicit domain object for the assignment process instead of placing the relationship directly on Task.

6.5 Schedule

Represents the actual planned execution of a task.

Expected attributes include:

id
assignment
startDateTime
endDateTime
status

The distinction between Assignment and Schedule is intentional:

Assignment answers: Who is responsible for the task?
Schedule answers: When will the task be performed?
7. Enumerations

The initial domain will use Java enums for controlled values.

Expected enums include:

UserRole
ADMIN
REVIEWER
OPERATOR
TaskPriority
LOW
MEDIUM
HIGH
URGENT
TaskStatus
TODO
IN_PROGRESS
COMPLETED
CANCELLED
AssignmentStatus
PENDING
ACTIVE
COMPLETED
CANCELLED
ScheduleStatus
PLANNED
IN_PROGRESS
COMPLETED
CANCELLED

The exact enum set may be refined as the business rules are implemented.

8. Entity Relationships

The initial relationship model is:

User
 |
 +----< Availability
 |
 +----< Assignment
             |
             v
           Task
             |
             |
             v
          Schedule

Conceptually:

One User can have many Availability records.
One User can have many Assignment records.
One Task can have many Assignment records over its lifecycle if reassignment is supported.
One Assignment can have one Schedule.
A Schedule belongs to one Assignment.

The exact cardinalities and ownership sides will be explicitly defined with JPA annotations.

9. JPA / ORM Strategy

The persistence layer will use:

@Entity
@Id
@GeneratedValue
@ManyToOne
@OneToMany
@OneToOne where appropriate
@Enumerated(EnumType.STRING)
@Column
@Table
@JoinColumn

Enums will normally be persisted as strings rather than ordinal integers to avoid database corruption when enum ordering changes.

Example:

@Enumerated(EnumType.STRING)
private TaskStatus status;

Hibernate will be responsible for translating the Java object model into the PostgreSQL relational model.

10. Database Strategy

PostgreSQL will run through Docker Compose.

The database configuration will include:

database name
username
password
exposed port
persistent volume

The Spring Boot application will connect to PostgreSQL through JDBC.

Development configuration will initially use Hibernate schema generation to automatically create/update the database structure.

The exact ddl-auto strategy will be documented before moving toward production-style database migrations.

11. Database Constraints

Constraints will be implemented at both application and database levels where appropriate.

Expected constraints include:

primary keys
foreign keys
non-null fields
unique username
unique email
valid temporal ranges
valid relationships
controlled enum values

For example:

User.email       -> UNIQUE
User.username    -> UNIQUE
Availability.startDateTime < Availability.endDateTime
Schedule.startDateTime    < Schedule.endDateTime

The final constraint strategy will be defined together with the entity mappings.

12. Backend Layering

The backend will follow a conventional layered architecture:

Controller
    |
    v
Service
    |
    v
Repository
    |
    v
JPA / Hibernate
    |
    v
PostgreSQL
Controller

Responsible for:

HTTP endpoints
request parameters
request/response handling
HTTP status codes
Service

Responsible for:

business rules
scheduling logic
validation that requires domain knowledge
transaction boundaries
Repository

Responsible for:

persistence
queries
database access

Spring Data JPA will be used to minimize boilerplate persistence code.

13. DTO Strategy

Entities should not automatically become the public REST API representation.

The project will therefore introduce DTOs when the REST layer is implemented.

Example:

UserEntity
    |
    v
UserResponseDto

This keeps the persistence model separate from the API contract.

DTO usage will be introduced progressively rather than adding unnecessary complexity to the first persistence milestone.

14. Validation

Validation will use Jakarta Bean Validation.

Typical constraints may include:

@NotNull
@NotBlank
@Email
@Size
@Positive

Cross-field validation, such as checking that an end date occurs after a start date, will be handled separately where standard annotations are insufficient.

15. Scheduling Algorithm

The scheduler will initially use a deterministic rule-based algorithm.

The algorithm will consider:

task priority
task duration
task due date
user role
user availability
existing scheduled tasks
possible scheduling conflicts

The initial objective is not to create an optimal mathematical scheduler, but to demonstrate clean separation between:

domain model
persistence
business logic
scheduling algorithm

A more sophisticated algorithm can be introduced later.

16. REST API

The backend will expose REST endpoints.

The initial API will likely include:

GET    /api/users
GET    /api/users/{id}
POST   /api/users
PUT    /api/users/{id}
DELETE /api/users/{id}


GET    /api/tasks
GET    /api/tasks/{id}
POST   /api/tasks
PUT    /api/tasks/{id}
DELETE /api/tasks/{id}


GET    /api/availability
POST   /api/availability


GET    /api/assignments
POST   /api/assignments


GET    /api/schedules
POST   /api/schedules

The final API contract will be defined after the domain model has been implemented.

17. Application Configuration

The initial application configuration will contain PostgreSQL connection properties.

Example conceptual configuration:

spring.datasource.url
spring.datasource.username
spring.datasource.password


spring.jpa.hibernate.ddl-auto
spring.jpa.show-sql
spring.jpa.properties.hibernate.format_sql

Credentials should not be committed to the repository in a production-oriented implementation.

For the initial local project, configuration may be simplified, but the separation between source code and environment-specific configuration should remain clear.

18. Docker Strategy

Docker Compose will initially provide PostgreSQL only.

Target architecture:

Docker Compose
    |
    +---- PostgreSQL

Later, the architecture may become:

Docker Compose
    |
    +---- PostgreSQL
    |
    +---- Spring Boot

The React frontend may also be containerized in a later stage.

19. Testing Strategy

Testing will be introduced incrementally.

Planned levels:

Unit Tests

Used for:

scheduling algorithm
business rules
validation logic
Repository Tests

Used to verify:

JPA mappings
queries
relationships
constraints
Integration Tests

Used to verify:

REST API
   |
Service
   |
JPA
   |
PostgreSQL

The initial milestone does not require a complete test suite, but the project structure must allow testing from the beginning.

20. Development Milestones

The project will be developed in the following order.

Milestone 1 — Infrastructure
 + Create task_scheduler directory
 + Create docker-compose.yml
 + Start PostgreSQL
 + Verify PostgreSQL connection
Milestone 2 — Spring Boot
 Create Spring Boot Maven project
 Configure Java 21
 Configure PostgreSQL driver
 Configure Spring Data JPA
 Configure application properties
 Start Spring Boot application
Milestone 3 — Domain Model
 Create 5 JPA entities
 Create enums
 Define primary keys
 Define entity relationships
 Define database constraints
 Configure Hibernate
Milestone 4 — Database Verification
 Start PostgreSQL
 Start Spring Boot
 Verify Hibernate startup
 Verify generated tables
 Verify foreign keys
 Verify constraints
 Inspect schema directly in PostgreSQL
Milestone 5 — Persistence Layer
 Create repositories
 Test CRUD operations
 Test entity relationships
 Add repository tests
Milestone 6 — Business Layer
 Create services
 Implement task management
 Implement availability management
 Implement assignment logic
 Implement scheduling algorithm
Milestone 7 — REST API
 Create controllers
 Create DTOs
 Add validation
 Add exception handling
 Test endpoints
Milestone 8 — Frontend
 Create React application
 Implement login UI
 Implement user management
 Implement task management
 Implement calendar/scheduling UI
 Connect frontend to REST API
Milestone 9 — Packaging
 Improve Docker configuration
 Containerize Spring Boot
 Containerize React
 Create complete Docker Compose environment
 Document startup procedure
21. Current Implementation Status

Current status:

[X] Create task_scheduler structure
[X] Create docker-compose.yml with PostgreSQL
[ ] Create Spring Boot Maven project
[ ] Configure PostgreSQL
[ ] Create 5 JPA entities
[ ] Create enums
[ ] Define relationships
[ ] Define constraints
[ ] Start application
[ ] Verify tables created by Hibernate

The immediate next objective is:

Create the Spring Boot Maven project without changing the existing PostgreSQL infrastructure.

After that, PostgreSQL connectivity will be configured and verified before implementing the JPA domain model.

22. Technical Decision Log

This section will record important architectural decisions made during development.

TD-001 — Java Version

Decision: Java 21.

Reason: Long-term-support Java version suitable for a modern Spring Boot project.

TD-002 — Persistence

Decision: Spring Data JPA + Hibernate.

Reason: Demonstrates standard enterprise Java ORM architecture while keeping repository code concise.

TD-003 — Database

Decision: PostgreSQL.

Reason: Mature relational database, strong SQL support, excellent Spring integration, and suitable for Docker-based development.

TD-004 — Database Development Environment

Decision: PostgreSQL runs in Docker Compose.

Reason: Reproducible local development environment without requiring PostgreSQL installation on the host.

TD-005 — API Architecture

Decision: REST API.

Reason: Clear separation between backend and planned React frontend.

23. Project Principles

The following principles should guide implementation.

Keep the project small.
Prefer clear architecture over unnecessary abstraction.
Use standard Spring conventions.
Keep business logic out of controllers.
Keep persistence logic out of controllers and services where possible.
Use explicit entity relationships.
Prefer readable code over clever code.
Document architectural decisions when they matter.
Introduce complexity only when it solves a real problem.
Every implementation step should leave the project in a runnable state.
24. Definition of Done

A milestone is considered complete when:

the implementation compiles;
the application starts successfully;
the relevant functionality can be demonstrated;
database changes can be verified;
configuration is documented;
the Git working tree contains only intentional changes.

The project should remain buildable and runnable throughout development.

25. Next Step

The next implementation step is to generate the Spring Boot Maven project inside the existing task_scheduler repository and verify that the minimal application starts successfully.

No JPA entities or scheduling logic should be introduced until the basic Spring Boot + Maven + PostgreSQL foundation has been verified.
"""

path = Path("/mnt/data/task-scheduler.md")
path.write_text(content, encoding="utf-8")
print(path)