# Legacy Backend Outline (Monolith)

## Overview

The legacy `backend/` is a **Java Spring Boot WebFlux monolith** with Thymeleaf server-side rendering. It is preserved as-is during migration and will be replaced incrementally by the target microservices.

---

## Technology Stack

| Aspect | Detail |
|--------|--------|
| Language | Java 25 |
| Framework | Spring Boot 4.0.6 |
| Web Layer | Hybrid WebFlux + WebMVC (both starters on classpath) |
| Database | MS SQL Server via R2DBC |
| Templates | Thymeleaf |
| Build | Gradle |

---

## Key Conventions

### Controller Pattern

- Controllers use `@Controller` (never `@RestController`).
- All controller methods return the template name `"app"`.
- Controllers set `model.addAttribute("controller", ...)` and `model.addAttribute("page", ...)` for Thymeleaf fragment resolution.
- Thymeleaf rendering is synchronous — services call `.block(Duration.ofSeconds(...))` to bridge reactive → blocking.

### R2DBC with Blocking

- Repositories return `Mono<T>` / `Flux<T>` (Spring Data R2DBC).
- Services call `.block(Duration.ofSeconds(...))` because Thymeleaf template rendering is synchronous.
- This is a hybrid reactive-blocking pattern — not pure reactive.

### Entity Mapping

- Uses Spring Data R2DBC `@Table` annotation (not JPA `@Entity`).
- No JPA/Hibernate in the legacy stack — all persistence is R2DBC.

### Lombok

- `@Data`, `@AllArgsConstructor`, `@NoArgsConstructor` on all entities and DTOs.

### Service Naming Convention

- Interfaces: `business/abstracts/` package
- Implementations: `business/concretes/` package, suffixed `*Manager`
- Example: `CustomerService` interface → `CustomerManager` implementation

### Result Pattern

Services return `Result` / `DataResult<T>` with `success + message` (Turkish toast strings):

| Type | Description |
|------|-------------|
| `Result` | Base result with `success` + `message` |
| `DataResult<T>` | Result with `data` payload |
| `SuccessResult` | Successful operation |
| `ErrorResult` | Failed operation |
| `SuccessDataResult<T>` | Successful operation with data |
| `ErrorDataResult<T>` | Failed operation with data |

### Custom Converters

`WebConfig` registers locale-aware string-to-number converters for Turkish locale (comma decimal separator):

- `LocalizedStringToDoubleConverter` — form input → double
- `LocalizedDoubleToStringConverter` — double → display string

**Critical for form submission** — removing or misconfiguring these breaks all numeric form fields.

---

## Database Defaults

- Connection string: `r2dbc:pool:mssql://localhost/InsuranceDB`
- Credentials: `sa` / `123456`
- Configured in `application.properties`

---

## Preservation Rule

Do **not** modify the legacy backend unless explicitly directed for maintenance. It remains operational on a separate subdomain during migration.
