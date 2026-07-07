# Java Conventions Outline

## Java 21+ Relaxed `main` Method

The `public` modifier on `main(String[])` is no longer required by the JVM specification (Java 21+).

All services **may omit `public`** on the application entry point for conciseness:

```java
// Valid — preferred for brevity
@SpringBootApplication
class CustomerServiceApplication {
    static void main(String[] args) {
        SpringApplication.run(CustomerServiceApplication.class, args);
    }
}
```

This is valid for:
- Java 25 (the project target)
- All Java 21+ runtimes

**Note:** If any existing service uses `public static void main`, it remains valid — both forms compile and run correctly on Java 25.

---

## Datetime Convention

### Timestamps → `java.time.Instant`

Fields representing a precise moment in time use `Instant`:

- `createdAt`
- `updatedAt`
- `deletedAt`
- Any audit/log timestamp

Managed automatically via `@PrePersist` / `@PreUpdate` lifecycle callbacks:

```java
@PrePersist
void onCreate() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
}

@PreUpdate
void onUpdate() {
    updatedAt = Instant.now();
}
```

### Date-Only Fields → `java.time.LocalDate`

Fields representing a calendar date without time zone use `LocalDate`:

- `birthDate`
- `policyStartDate`
- `licenseFirstDate`
- Any date where time-of-day is irrelevant

### Rationale

- `Instant` avoids timezone ambiguity for audit trails (always UTC).
- `LocalDate` avoids the confusion of `java.sql.Date` / `java.util.Date`.
- Consistent across all microservices — enforced in code review.

---

## Lombok Convention

All entities and DTOs use Lombok annotations:

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "customers")
public class Customer { ... }
```

The order is: `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`, then JPA annotations.

---

## Jackson 3 Usage Notes

### Annotations Stay at `com.fasterxml.jackson.annotation`

Jackson 3 kept annotations backward compatible: `@JsonInclude`, `@JsonIgnore`, `@JsonProperty`, `@JsonIgnoreProperties`, `@JsonAlias`, etc. all remain under the original `com.fasterxml.jackson.annotation` package. No import migration is needed for annotations.

```java
// ✅ These are correct — unchanged in Jackson 3
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
```

### What Changed

Only the runtime/databind artifacts moved to `tools.jackson`:
- `ObjectMapper` → `tools.jackson.databind.ObjectMapper`
- `JsonNode`, `JsonMapper` → `tools.jackson.databind.*`
- `@JsonSerialize`, `@JsonDeserialize` (custom serializer/deserializer classes referenced by these must be `tools.jackson`-aware)

**Rule:** Annotations from `com.fasterxml.jackson.annotation.*` remain valid with Jackson 3. Only programmatic API classes (`ObjectMapper`, `JsonNode`, `Module`, etc.) need the `tools.jackson` import migration.
