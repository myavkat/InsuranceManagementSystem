# AGENTS.md - InsuranceManagementSystem

## Project's Goal
This is a Spring 4 microservices backend + Next.js frontend project for handling an Insurance company's customers, settings for provided insurances, processing and storing payments, insurance start end dates, creating required documents to comply with insurance standards, and insurance premium estimation based on insurance risk factors like customer's age, profession and if a vehicle is the asset that is insured, vehicle related risk factors like motor size, brand, if a real estate then real estate's type, age etc.

## Folder Structure Overview
- **`frontend/`**: Next.js SSR frontend application (App Router, Tailwind CSS, shadcn/ui).
- **`common/`**: Shared libraries across microservices (event schemas, web config, test utilities).
- **`services/`**: Spring Boot 4 microservices (Customer, Vehicle, RealEstate, Insurance, Estimation, Reference Data, Auth, API Gateway).
- **`infra/`**: Infrastructure artifacts including Docker Compose, SQL init scripts, and Kafka configurations.

## Workflow
When assigned a task, follow this high-level workflow:
1. **Lint**: Format the code using the specific directory's linter.
2. **Compile**: Build the project to ensure no compilation errors.
3. **Test**: Run tests to verify functionality. Maintain at least 80% test coverage.
4. **Summarize**: Summarize changes and design decisions made. If the task originated from the project management MCP (Notion/Jira/Kanban), save this summarization and design decisions info back to the MCP.
5. **Commit**: Commit the changes following the Git commit rules.

*Note: For specific commands, refer to the `AGENTS.md` file in the relevant subfolder (`frontend/`, `common/`, `services/`, `infra/`).*

## Git Commit Rules
- Use the following command for committing:
  ```bash
  git commit -m "Your short header here" -m "Detailed message."
  ```
- Follow the Chris Beams style for commit messages. See @AGENTS_COMMIT_RULES.md
- Commit each logical topic separately (topic-by-topic), not as a batch of unrelated changes.
- Use descriptive, topic-based commit headers with conventional commit prefixes (e.g., `feat(scope):`, `fix(scope):`, `docs:`, `test(scope):`, `refactor(scope):`, `chore:`). Never use opaque codenames, section numbers, or ticket IDs alone.
- Never include `Co-Authored-By` trailers or any other auto-attribution lines.
- Never use here-string delimiters or other `@` wrapping around commit messages in shell commands — pass the message directly as a plain string.
- Never push to remote repositories, ever!

## Review Checklist
- Code formatting/linting must pass.
- All tests must succeed.
- Add new tests for any new feature or bug fix.
- Update documentation for user facing changes.
- Ensure at least 80% test coverage for new code.

## Jira Project Binding
This repository is bound to the **IMS** (InsuranceManagementSystem) Jira project.

**Rule**: Any agent in any session working inside this repo MUST:
1. On session start, verify the **IMS** project exists in Jira (project key `IMS`, cloud ID `09774605-6cc7-40b1-ace1-7d8c69e8c43f`).
2. If IMS is not found → immediately report to the user and **halt execution** — do not proceed with any other work.
3. Default all Jira operations (search, create, update, comment) to the **IMS** project unless the user explicitly overrides.
4. Do NOT search, read, or write to other Jira projects (e.g., KAN) unless the user explicitly asks you to.

Use this JQL for the default task search:
```
project = IMS AND resolution = Unresolved ORDER BY updated DESC
```

## Global Execution Constraints
- **Context Precedence**: 
  1. Local Constraints (`@AGENTS.LOCAL.md` — if present)
  2. Project Constraints (This file)
  3. Subfolder Constraints (Subfolder `AGENTS.md` files)
- DO NOT invent dependencies or refactor out-of-scope modules.
- **Environment variable documentation**: Every `${ENV_VAR:default}` placeholder referenced in any `application.yml` MUST have a corresponding entry in `.env.template` (repo root). The `.env.template` file is the single source of truth for all configurable environment variables.
- **No direct REST between services**: All inter-service communication via Kafka.
- **Database per service**: No shared databases, no cross-service joins.
- **SAGA choreography**: No central orchestrator; services react to events and publish outcomes.
- **Idempotent consumers**: Deduplicate events using `sagaId` + event type.
