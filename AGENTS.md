# AGENTS.md - InsuranceManagementSystem

## Global Execution Constraints

### Context Precedence
When working on features, prioritize guidelines in this order:
1. Local Constraints (@AGENTS.LOCAL.md — if present in active memory)
2. Project Constraints (This file)
3. Workspace Context & Workflow Handling (`docs/AGENTS.md`)

### Operational Rules
- ALWAYS look into `docs/plans/` for the active feature plan before writing code.
- BEFORE executing any workspace actions, read and adhere to the directory workflow defined in `docs/AGENTS.md`.
- If a model swap occurred, verify state by cross-referencing code against the active plan file.
- DO NOT invent dependencies or refactor out-of-scope modules.
- **Commit message convention:** Use descriptive, topic-based commit headers with conventional commit prefixes (e.g., `feat(scope):`, `fix(scope):`, `docs:`, `test(scope):`, `refactor(scope):`, `chore:`). Never use opaque codenames, section numbers, or ticket IDs alone as the commit subject — the header must describe what changed, not reference external tracking. Commit each logical topic separately (topic-by-topic), not as a batch of unrelated changes.
- **No auto-attribution:** Never include `Co-Authored-By` trailers or any other auto-attribution lines in commit messages. Only the actual human author's identity belongs in commits.

## Architecture & Convention Index

All technical decisions and conventions live in `docs/outlines/`. Consult the relevant outline before implementing any feature.

### Core Architecture
| Outline | Content |
|---------|---------|
| [`01_SYSTEM_ARCHITECTURE.md`](docs/outlines/01_SYSTEM_ARCHITECTURE.md) | Tech stack, microservice breakdown, communication architecture, key architectural rules |
| [`02_MICROSERVICES_SPECIFICATIONS.md`](docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md) | Per-service entities, endpoints, SAGA consumers |
| [`07_PROJECT_STRUCTURE.md`](docs/outlines/07_PROJECT_STRUCTURE.md) | Directory layout, technology stack per layer, build order |

### Patterns & Communication
| Outline | Content |
|---------|---------|
| [`03_SAGA_PATTERN.md`](docs/outlines/03_SAGA_PATTERN.md) | SAGA choreography flow, event catalog, idempotency, timeout, compensation |
| [`04_MESSAGE_QUEUE_TOPOLOGY.md`](docs/outlines/04_MESSAGE_QUEUE_TOPOLOGY.md) | Kafka topics, RabbitMQ queues, per-service config |
| [`06_API_GATEWAY_AUTH.md`](docs/outlines/06_API_GATEWAY_AUTH.md) | Gateway routing, filter chain, rate limiting, auth service |

### Frontend
| Outline | Content |
|---------|---------|
| [`05_NEXTJS_FRONTEND.md`](docs/outlines/05_NEXTJS_FRONTEND.md) | Next.js App Router architecture, BFF pattern, component structure, data flow |
| [`09_LEGACY_FRONTEND.md`](docs/outlines/09_LEGACY_FRONTEND.md) | Legacy Vue 3 conventions, mock services, auth quirks, navigation issues |

### Conventions & Operations
| Outline | Content |
|---------|---------|
| [`08_LEGACY_BACKEND.md`](docs/outlines/08_LEGACY_BACKEND.md) | Legacy monolith conventions (WebFlux, R2DBC, Result pattern, Thymeleaf) |
| [`10_JAVA_CONVENTIONS.md`](docs/outlines/10_JAVA_CONVENTIONS.md) | Java 21+ relaxed main, datetime conventions (Instant/LocalDate), Lombok order |
| [`11_TESTING_CONVENTIONS.md`](docs/outlines/11_TESTING_CONVENTIONS.md) | Spring Boot 4 testing rules (RestTestClient, slice/integration tests, assertions) |
| [`12_DEVELOPER_COMMANDS.md`](docs/outlines/12_DEVELOPER_COMMANDS.md) | Build, run, test commands for all subsystems |
| [`13_ENVIRONMENT_QUIRKS.md`](docs/outlines/13_ENVIRONMENT_QUIRKS.md) | .gitignore, defaults, IntelliJ quirks, port allocation, legacy preservation |
