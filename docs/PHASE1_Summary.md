# Phase 1 Technical Report

Project: Smart Attendance Registry
Phase Title: Project Foundation & Infrastructure Setup
Audience: Professor evaluation, project review meetings, viva examinations, internship interviews, and SDE interviews

This report documents the completed Phase 1 foundation work for the Smart Attendance Registry project. It explains what was built, why the foundation was necessary, the technology choices made, the implementation structure, the database design, and the validation work completed before moving to later phases.

---

## 1. Executive Summary

Phase 1 established the project foundation and infrastructure required to support the full Smart Attendance Registry system. The work completed in this phase focused on creating a clean monorepo structure, preparing the backend stack, containerizing the development environment, setting up PostgreSQL connectivity, adding a migration runner, and defining the initial database schema required for later phases.

The deliverables completed in Phase 1 are:

- Monorepo organization across `backend/`, `android/`, `dashboard/`, `migrations/`, and `docs/`
- Node.js + TypeScript + Express backend foundation
- Docker and Docker Compose setup for reproducible local development
- Environment variable templates for safe configuration management
- PostgreSQL connection layer for local or Neon deployment targets
- SQL-based migration runner for repeatable database setup
- Initial schema, indexes, and session threshold schema
- Health endpoint for runtime verification
- Verification of Docker startup, database connectivity, and migration execution
- Technical documentation in `README.md` and `docs/PHASE1_REPORT.md`

This phase did not implement authentication, attendance logic, device binding behavior, Wi-Fi fingerprint processing, or WebSocket streaming features. Those belong to later approved phases. Phase 1 exists to make those future features easier to build, test, and deploy safely.

---

## 2. Why Phase 1 Was Necessary

### Why we did not immediately build attendance features

Attendance features depend on a stable base: authentication, database connectivity, schema consistency, deployment reproducibility, and service startup reliability. If those foundations are not established first, later features become fragile and difficult to test. For example, a session join flow or heartbeat pipeline is not meaningful if the database schema does not exist or the backend cannot start reliably in containers.

### Why infrastructure comes before business logic

Infrastructure determines how the system is run, how it connects to data, how it is configured, and how it is verified. Business logic depends on those foundations. In professional systems, a backend service is not treated as a set of isolated functions; it is treated as a deployable, configurable, testable application. Phase 1 created the environment in which business logic can be developed safely and consistently.

### Why professional software projects start with foundation layers

Professional software projects usually begin with:

- Source control organization
- Build and runtime configuration
- Environment management
- Database migration strategy
- Logging and verification hooks
- Deployment reproducibility

These layers reduce development friction and prevent architecture drift. They also make the project reviewable by professors, interviewers, and teammates because the codebase follows a structured engineering approach rather than ad hoc feature addition.

---

## 3. Architecture Decisions

The following decisions were made to match the approved project documents and to keep the system maintainable.

### 3.1 Node.js

#### Advantages

- Fast startup for backend services
- Strong ecosystem for APIs, WebSockets, and database access
- Efficient for I/O-heavy workloads such as HTTP and database operations
- Easy integration with Express and PostgreSQL libraries

#### Disadvantages

- Single-threaded event loop can become a bottleneck for CPU-heavy workloads
- Dependency quality varies across packages
- Requires discipline around asynchronous error handling

#### Alternatives considered

- Java Spring Boot
- Python FastAPI
- Go Gin/Fiber

Node.js was chosen because the approved architecture already targets Node.js, Express, and WebSocket support. It is a practical fit for REST APIs and real-time events.

### 3.2 TypeScript

#### Advantages

- Static typing improves reliability
- Better refactoring support
- Clearer contracts for backend functions, data models, and service boundaries
- Reduces runtime errors in larger codebases

#### Disadvantages

- Compilation step adds build complexity
- Requires type discipline and configuration
- Can slow prototyping slightly compared with plain JavaScript

#### Alternatives considered

- Plain JavaScript
- Babel-transpiled JavaScript

TypeScript was chosen because the project is a capstone-style system that needs maintainable structure and interview-friendly engineering quality.

### 3.3 Express

#### Advantages

- Minimal and flexible
- Easy to understand and extend
- Large ecosystem and broad support
- Suitable for clean API scaffolding

#### Disadvantages

- Less opinionated than full frameworks
- Requires manual structure decisions
- Some cross-cutting concerns must be added explicitly

#### Alternatives considered

- NestJS
- Fastify
- Koa

Express was chosen because it is lightweight and well aligned with the project’s need for a minimal, transparent foundation layer.

### 3.4 Docker

#### Advantages

- Reproducible runtime environment
- Reduces “works on my machine” issues
- Encapsulates dependencies and startup behavior
- Makes local development and review easier

#### Disadvantages

- Adds an extra abstraction layer
- Requires Docker Engine availability
- Image builds can fail if dependency assumptions are incorrect

#### Alternatives considered

- Native local execution without containers
- Podman
- Virtual machines

Docker was chosen because the project is intended to be reproducible across machines and suitable for demos, interviews, and grading environments.

### 3.5 PostgreSQL

#### Advantages

- Reliable relational database with ACID guarantees
- Strong support for schema constraints, indexes, and migrations
- Mature tooling and community support
- Well suited for structured attendance records

#### Disadvantages

- Requires schema design discipline
- Some advanced features require deeper operational understanding
- Scaling beyond a single instance requires planning

#### Alternatives considered

- MySQL / MariaDB
- SQLite
- MongoDB

PostgreSQL was chosen because the project needs transactional integrity, relational constraints, and a migration-friendly schema.

### 3.6 Migration-based database management

#### Advantages

- Schema changes are versioned
- Environments can be brought to the same state consistently
- Supports review, rollback planning, and reproducible deployments
- Makes database evolution explicit

#### Disadvantages

- Requires migration discipline
- Incorrect migrations can break startup if not tested
- Rollback planning must be managed carefully

#### Alternatives considered

- Manual schema edits
- GUI-based database modification
- Auto-sync ORM schema generation only

Migration-based management was chosen because database history matters in real projects. The database is part of the application, not a separate manual artifact.

---

## 4. Backend Foundation

The backend foundation consists of small files with clear responsibilities. This is intentional: the goal is to make the system easy to understand and extend.

### 4.1 `backend/package.json`

Purpose:

- Defines backend package metadata
- Declares runtime and development dependencies
- Provides scripts for development, build, start, and migration execution

Why it matters:

- It is the execution contract for the backend service
- It tells Docker and developers how to run the service consistently

### 4.2 `backend/tsconfig.json`

Purpose:

- Configures TypeScript compilation
- Sets strict type-checking rules
- Defines source and output directories

Why it matters:

- It ensures the TypeScript code compiles predictably
- It prepares the project for future scaling without losing type safety

### 4.3 `backend/Dockerfile`

Purpose:

- Builds the backend development container
- Installs dependencies
- Copies source code into the image
- Starts the development server with `npm run dev`

Why it matters:

- It makes the backend runnable in a containerized development environment
- It standardizes startup behavior across machines

### 4.4 `backend/src/index.ts`

Purpose:

- Entry point for the backend server
- Creates the Express app
- Exposes `/health` for runtime checks
- Exposes `/migrate` for controlled migration execution during Phase 1 validation

Why it matters:

- It proves the service can start and respond to HTTP requests
- It is the first operational slice of the backend

### 4.5 `backend/src/config/db.ts`

Purpose:

- Creates the PostgreSQL connection pool using `pg`
- Reads the database connection string from environment variables
- Provides a shared database access point for the backend

Why it matters:

- It centralizes database connectivity
- It prevents connection logic from being scattered across files

### 4.6 `backend/src/scripts/migrate.ts`

Purpose:

- Reads SQL migration files from the `migrations/` directory
- Applies them in sorted order
- Wraps execution in a transaction
- Provides a direct migration runner for validation

Why it matters:

- It makes schema setup reproducible
- It ensures migration execution is transparent and testable

---

## 5. Database Foundation

The database foundation was created before business features so that later phases can depend on a stable schema.

### 5.1 Initial Schema

The initial schema created the baseline tables needed for the project foundation:

- `students`
- `teachers`
- `classrooms`
- `sessions`
- `device_bindings`
- `attendance_overrides`

These tables establish identity, classroom structure, session storage, and future expansion points. In Phase 1 they act as schema foundations rather than active business workflows.

### 5.2 Indexes

Indexes were added for the most likely lookup paths:

- `device_bindings(student_id, status)`
- `attendance_overrides(session_id)`
- `attendance_overrides(student_id)`

Indexes matter because they reduce query cost and prepare the schema for future access patterns. They are a foundation-level concern, not a feature-layer concern.

### 5.3 Session Threshold Schema

The session threshold migration added:

- `presence_threshold_present`
- `presence_threshold_partial`
- A check constraint enforcing `partial < present`

This is important because it pre-establishes the schema that later session lifecycle logic will depend on.

### 5.4 Why migrations are important

Migrations make database changes version-controlled, repeatable, and reviewable. They prevent manual schema drift and make it possible to recreate the same database state in development, testing, and deployment environments.

### 5.5 Why schema versioning is important

Schema versioning allows the database to evolve safely. Each change has a known order and history, which makes review easier and reduces the chance of silent breakage when the schema changes over time.

### 5.6 Why rollback capability matters

Rollback capability matters because not every migration is perfect on the first try. A system that cannot safely recover from a broken schema update is difficult to trust in production or during demos. Even when rollback is not fully implemented yet, the project must be designed with rollback in mind.

---

## 6. Security Considerations

Phase 1 focused on secure foundations, even though authentication itself was not implemented yet.

### Environment variable usage

Secrets and connection settings are stored in environment variables rather than hardcoded in source code. This follows secure configuration practices and keeps credentials out of the repository.

### Database isolation

Database access is isolated behind a dedicated config module. This helps keep connection behavior controlled and reduces the chance of accidental leakage or duplicated access code.

### Docker isolation

Docker provides process isolation and dependency isolation. It helps prevent local machine differences from affecting application behavior and keeps the runtime environment more predictable.

### Future JWT integration

Authentication was intentionally deferred to later phases, but the foundation was prepared for JWT-based auth by keeping the backend architecture modular. Future JWT handling will fit into the existing layered service structure without requiring a redesign.

### OWASP considerations

Phase 1 supports several OWASP-aligned practices:

- Avoid hardcoded secrets
- Centralize database access
- Minimize exposed endpoints
- Keep startup and migration operations explicit
- Reduce configuration drift across environments

Even before user authentication is added, these steps help reduce common security mistakes.

---

## 7. DevOps Considerations

### Docker Compose

Docker Compose orchestrates the backend and database services in a repeatable way. It makes local setup easier and supports future multi-service development.

### Containerization

Containerization packages the application runtime, dependencies, and startup behavior into a standardized unit. This simplifies onboarding, debugging, and deployment demos.

### Environment consistency

Phase 1 introduced explicit `.env.example` files to show required variables and to keep local, development, and review environments aligned.

### Reproducible deployments

The combination of Docker, Compose, and migrations creates a repeatable setup process. That is essential in academic review, internship demonstrations, and interview settings because the system can be shown reliably.

---

## 8. Operating Systems Concepts Used

### Processes

The backend service runs as a process inside a container. The migration runner and health endpoint are both process-level runtime behaviors.

### Containers

Containers package the application into isolated runtime units. They behave similarly to lightweight OS-managed execution environments.

### Environment variables

Environment variables act as runtime configuration inputs. They are used to pass the database URL, port, and other deployment-specific values.

### Networking

The backend communicates over HTTP and connects to PostgreSQL over TCP. Docker Compose also configures service networking between containers.

### File systems

The project relies on a structured file system layout for source code, migrations, documentation, and scripts. Migration execution reads SQL files from the filesystem in a controlled order.

---

## 9. Computer Networks Concepts Used

### TCP/IP

PostgreSQL communication and HTTP requests both depend on TCP/IP networking. Understanding this is important for diagnosing container connectivity and service startup issues.

### Client-server architecture

The backend serves requests from clients such as future Android and dashboard components. PostgreSQL acts as a server from the backend’s perspective.

### HTTP

HTTP is used for the health endpoint and migration endpoint. These endpoints provide a simple way to validate runtime availability.

### Health endpoints

The `/health` route is a standard operational pattern. It allows external checks to verify that the server is running and can talk to the database.

### Future WebSocket integration

The approved architecture includes future real-time WebSocket communication. Phase 1 does not implement it, but the service foundation is prepared for it.

---

## 10. Software Engineering Concepts Used

### Separation of concerns

Each file has a single primary purpose:

- server entrypoint
- database configuration
- migration runner
- schema files

This makes the project easier to understand and change.

### Modularity

The backend was split into small modules to avoid a monolithic startup file. This supports testing, debugging, and future extension.

### SOLID principles

- **Single Responsibility:** each file has a focused responsibility
- **Open/Closed:** the foundation can be extended without rewriting the core structure
- **Dependency Inversion:** higher-level startup logic depends on config modules rather than hardcoded values

### DRY

Connection details, startup logic, and schema concerns are centralized instead of duplicated.

### KISS

The Phase 1 implementation is intentionally simple. Simplicity is not weakness; it is a deliberate strategy for reducing risk in a foundation phase.

### Layered architecture

The project follows a layered path:

- Infrastructure layer: Docker, Compose, environment files
- Data layer: PostgreSQL, schema, migrations
- Service layer: Express backend
- Future feature layers: authentication, attendance, WebSocket, Android, dashboard

---

## 11. Challenges Encountered

### Docker Engine not running

During validation, Docker could not be assumed to be available in every environment. If Docker Engine is not running, container startup and verification will fail immediately.

### Docker daemon connection issue

A common deployment issue is failure to connect to the Docker daemon. This is usually caused by Docker not being installed, Docker Desktop not running, or the current terminal session lacking access to the daemon.

### `ts-node-dev` missing inside container

#### Root cause analysis

The backend Dockerfile originally installed only production dependencies with `npm install --production`, but the container started with `npm run dev`. The `dev` script depends on `ts-node-dev`, which lives in `devDependencies`. Because dev dependencies were excluded, the container failed with `sh: ts-node-dev: not found`.

#### Fix implemented

The Dockerfile was updated to install the full dependency set with `npm install` so the development container can execute `ts-node-dev` successfully.

This fix does not change the architecture. It only corrects the container startup behavior for the intended development workflow.

---

## 12. Lessons Learned

1. A software project is easier to scale when the foundation is solid.
2. Docker startup behavior must match the dependencies actually installed in the image.
3. Migrations should be treated as first-class source code.
4. A health endpoint is a simple but powerful verification tool.
5. Separation of configuration, startup logic, and schema files improves maintainability.
6. Architecture decisions should be aligned with approved project documents, not improvised during implementation.
7. Early validation prevents much larger integration problems later.

---

## 13. Interview Questions and Answers

### 1. What is Docker and why was it used in this project?
Docker is a containerization platform used to package the backend and database runtime consistently. It prevents environment drift and makes the project reproducible.

### 2. What is the difference between an image and a container?
An image is a packaged template. A container is a running instance of that image.

### 3. Why is Docker Compose useful?
Docker Compose allows multiple services, such as backend and database, to be started together with a single command.

### 4. Why was PostgreSQL chosen?
PostgreSQL offers transactional integrity, schema constraints, and strong support for relational attendance data.

### 5. What is a migration?
A migration is a versioned database change that can be applied in a controlled order.

### 6. Why are migrations better than manual schema updates?
They are repeatable, reviewable, and less error-prone than manual edits.

### 7. What does the health endpoint do?
It confirms the backend is running and can reach the database.

### 8. Why use TypeScript instead of plain JavaScript?
TypeScript provides static typing, better refactoring support, and fewer runtime mistakes.

### 9. Why was Express used?
Express is lightweight, easy to understand, and well suited to a minimal API foundation.

### 10. What is the purpose of `package.json`?
It defines scripts, dependencies, and package metadata for the backend.

### 11. What is the purpose of `tsconfig.json`?
It configures TypeScript compilation and type-checking rules.

### 12. Why is `DATABASE_URL` stored in an environment variable?
Because secrets should not be hardcoded in source files.

### 13. What problem did the container startup bug reveal?
It showed that the runtime command must match the installed dependency type.

### 14. What caused `ts-node-dev` to be missing?
The Dockerfile installed only production dependencies while the container tried to use a dev-only package.

### 15. How was that issue fixed?
By installing the full dependency set in the development container.

### 16. Why are indexes important?
They improve query performance for frequently accessed database paths.

### 17. Why is a transaction important in migration execution?
It ensures that the migration batch either completes fully or rolls back safely.

### 18. What is the advantage of separating `db.ts` from `index.ts`?
It keeps connection logic isolated from request handling.

### 19. Why is Docker considered part of the architecture?
Because it defines how the application runs and how its services interact.

### 20. What is the main outcome of Phase 1?
A stable, reproducible, and reviewable project foundation ready for later feature phases.

---

## 14. Viva Questions and Answers

### 1. Why did you start with infrastructure instead of features?
Because business logic depends on a stable runtime, database, and deployment foundation.

### 2. Why is database schema design part of Phase 1?
Because later phases need a stable schema to build on.

### 3. Why is PostgreSQL suitable for attendance data?
Because attendance records are relational and require consistency, indexes, and constraints.

### 4. What is schema versioning?
It is the practice of tracking database changes through ordered migrations.

### 5. Why is rollback capability important?
It protects the project from broken database changes.

### 6. Why did you choose Express over a heavier framework?
Because Phase 1 required a simple and transparent backend foundation.

### 7. Why is TypeScript a good choice for a capstone project?
Because it demonstrates stronger engineering discipline and reduces errors.

### 8. What does Docker solve in this project?
It solves environment inconsistency and makes setup reproducible.

### 9. What is the role of the health endpoint?
It verifies that the application is alive and connected to the database.

### 10. What is the purpose of a migration runner?
It applies schema files in a controlled and repeatable order.

### 11. Why not hardcode the database URL?
Because configuration should be externalized for security and portability.

### 12. What is the root cause of the `ts-node-dev` container failure?
The container installed only production dependencies but started in development mode.

### 13. How did you fix the `ts-node-dev` issue?
By changing the Dockerfile to install dev dependencies as well.

### 14. What is the main advantage of modular file design?
It makes the codebase easier to maintain and test.

### 15. How does this phase reduce future bugs?
By removing setup ambiguity and enforcing consistent database behavior early.

### 16. What OS concepts are demonstrated in Phase 1?
Processes, containers, file systems, networking, and environment variables.

### 17. What network concepts are used?
HTTP, TCP/IP, client-server communication, and health checking.

### 18. Why is the migration transaction important?
It prevents partially applied schema changes.

### 19. How does this phase support future WebSocket work?
It establishes the backend service and infrastructure that real-time features will use later.

### 20. What is the key engineering lesson from Phase 1?
Strong foundations make later development safer, faster, and more maintainable.

---

## 15. Phase 1 Deliverables Summary

### Files Created

- `backend/package.json`
- `backend/tsconfig.json`
- `backend/Dockerfile`
- `backend/.env.example`
- `backend/src/index.ts`
- `backend/src/config/db.ts`
- `backend/src/scripts/migrate.ts`
- `migrations/001_initial_schema.sql`
- `migrations/002_indexes.sql`
- `migrations/006_session_thresholds.sql`
- `docs/PHASE1_REPORT.md`
- `scripts/local_up.ps1`

### Files Modified

- `README.md`
- `.env.example`
- `backend/Dockerfile`
- `docs/PHASE1_Summary.md`

### Infrastructure Added

- Docker and Docker Compose setup
- Backend development container
- Environment variable templates
- PostgreSQL connection layer
- Migration runner workflow

### Database Components Added

- Initial schema tables
- Indexes
- Session threshold columns and constraints

### Verification Completed

- Health endpoint created
- Docker configuration validated conceptually and corrected for dev dependencies
- Database connectivity layer implemented
- Migration execution flow implemented and exercised through the backend endpoint

---

## 16. Why This Phase Improves Project Success Probability

Phase 1 improves the overall success probability of the project by reducing uncertainty before business logic is introduced. A stable foundation prevents common early failures such as inconsistent environments, missing dependencies, schema drift, and startup issues.

This phase accelerates future development because later features can rely on an established service entrypoint, a known database schema, and a reproducible runtime. It also improves bug prevention: when infrastructure, configuration, and database evolution are already controlled, later development can focus on actual business behavior rather than repeated setup debugging.

For a capstone project, this matters even more because the system must be explainable during professor reviews, viva examinations, and interviews. A well-structured foundation demonstrates engineering maturity, project discipline, and an understanding of real software delivery practices.

---

## 17. Final Statement

Phase 1 successfully established the foundation and infrastructure for the Smart Attendance Registry project. The project now has a repeatable backend runtime, database migration workflow, containerized development setup, and a clear architecture boundary for future phases. No Phase 2 behavior has been implemented.
