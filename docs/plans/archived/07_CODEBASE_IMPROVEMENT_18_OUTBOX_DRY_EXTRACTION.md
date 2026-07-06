# Plan 18: Extract OutboxProcessor and OutboxRelay to common-message Module

## Severity: MEDIUM — Bug fix divergence risk; 322 duplicated lines across 3 services

## Status — ✅ Complete

- [x] Move `OutboxProcessor` to `common-message` module
- [x] Move `OutboxRelay` to `common-message` module with configurable property prefix
- [x] Remove per-service `OutboxProcessor.java` and `OutboxRelay.java`
- [x] Update all three service configurations to use common beans
- [x] Run all outbox tests across all three services

---

## Context

### The Problem

The `OutboxProcessor` class (112 lines) and `OutboxRelay` class (51 lines) are **byte-for-byte identical** across all three services:

| Service | OutboxProcessor Path | OutboxRelay Path |
|---------|---------------------|-----------------|
| customer-service | `customer/config/OutboxProcessor.java` | `customer/config/OutboxRelay.java` |
| insurance-service | `insurance/config/OutboxRelay.java` | `insurance/config/OutboxRelay.java` |
| estimation-service | `estimation/config/OutboxRelay.java` | `estimation/config/OutboxRelay.java` |

The only difference is the `@Value` property prefix in `OutboxRelay`:
- `estimation.outbox.poll-interval-ms`
- `customer.outbox.poll-interval-ms`
- `insurance.outbox.poll-interval-ms`

The `common-message` module already owns:
- `OutboxEvent` entity
- `OutboxEventRepository` interface
- `MessagePublisher` component

The `common-persistence` auto-configuration already scans and registers the repository. The processing logic is the missing piece.

### Why Extract Now

The existing plan `06_CODEBASE_IMPROVEMENT_12_DRY_EXTRACTION.md` explicitly called for this extraction but it was deferred. As a result:
- Any bug fix to the outbox processing logic (retry backoff, dead-letter handling, zombie recovery thresholds, batch sizing) must be applied identically to 3 files
- A developer fixing insurance-service can miss customer-service, leaving one service with stale behavior
- Adding a new service requires copy-pasting both classes yet again

---

## Fix Strategy

### Step 1: Create Common OutboxProcessor

**New file:** `common/common-message/src/main/java/com/insurancemanagementsystem/common/config/OutboxProcessor.java`

Copy the existing implementation from any service (they are identical). No changes to the logic. The class stays in package `com.insurancemanagementsystem.common.config`.

The existing package-private setters (`setMaxRetries`, `setFailedTtlMinutes`) remain — they will be called by the common `OutboxRelay`.

### Step 2: Create Common OutboxRelay

**New file:** `common/common-message/src/main/java/com/insurancemanagementsystem/common/config/OutboxRelay.java`

The common `OutboxRelay` must accept the property prefix as a constructor parameter (or use a common default prefix):

**Design option A — Configurable prefix via constructor:**
```java
@Component
@Slf4j
public class OutboxRelay {

    private final OutboxProcessor outboxProcessor;
    private final String prefix; // e.g., "estimation.outbox"

    public OutboxRelay(OutboxProcessor outboxProcessor,
                       @Value("${outbox.relay.prefix:outbox}") String prefix) {
        this.outboxProcessor = outboxProcessor;
        this.prefix = prefix;
    }
    // ... schedule using environment.resolvePlaceholders("${" + prefix + ".poll-interval-ms:1000}")
}
```

**Design option B — Common default prefix, override per service via `@Bean`:**
```java
// In common-message:
@Component
public class OutboxRelay {
    @Value("${outbox.poll-interval-ms:1000}")  // common default
    private int pollIntervalMs;
    // ...
}
```

Each service's `application.yml` already defines service-specific prefixes. Since these are Kafka consumers that each run independently, **Option B is simpler** — each service maps its existing properties to the common prefix:

```yaml
# In customer-service application.yml:
outbox:
  poll-interval-ms: ${customer.outbox.poll-interval-ms:1000}
  max-retries: ${customer.outbox.max-retries:3}
  failed-ttl-minutes: ${customer.outbox.failed-ttl-minutes:60}
```

**Use Option B** — every service defines the same property keys. The `@Value` defaults handle backward compatibility.

### Step 3: Remove Per-Service Classes

Delete the following files (6 files total):
- `services/customer-service/src/main/java/.../customer/config/OutboxProcessor.java`
- `services/customer-service/src/main/java/.../customer/config/OutboxRelay.java`
- `services/insurance-service/src/main/java/.../insurance/config/OutboxProcessor.java`
- `services/insurance-service/src/main/java/.../insurance/config/OutboxRelay.java`
- `services/estimation-service/src/main/java/.../estimation/config/OutboxProcessor.java`
- `services/estimation-service/src/main/java/.../estimation/config/OutboxRelay.java`

### Step 4: Update Service Configurations

Ensure each service scans the `com.insurancemanagementsystem.common.config` package. Since `common-message` is already a dependency and `CommonPersistenceAutoConfiguration` already handles entity scanning, component scanning for `OutboxProcessor` and `OutboxRelay` should work if:
1. The `@ComponentScan` on each service's `@SpringBootApplication` includes the common package, OR
2. They are registered via `@Import` in each service's configuration, OR
3. A new `CommonMessagingAutoConfiguration` in `common-message` is registered via `AutoConfiguration.imports`

Check existing imports in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

### Step 5: Update Tests

Each service has per-service `OutboxProcessorTest.java` files:
- `services/customer-service/src/test/.../customer/config/OutboxProcessorTest.java`
- `services/insurance-service/src/test/.../insurance/config/OutboxProcessorTest.java`
- `services/estimation-service/src/test/.../estimation/config/OutboxProcessorTest.java`

Move the common test logic to `common/common-message/src/test/.../common/config/OutboxProcessorTest.java`. Remove per-service duplicates.

---

## Files to Modify

| # | Action | File |
|---|--------|------|
| 1 | CREATE | `common/common-message/src/main/java/.../common/config/OutboxProcessor.java` |
| 2 | CREATE | `common/common-message/src/main/java/.../common/config/OutboxRelay.java` |
| 3 | DELETE | `services/customer-service/src/main/java/.../customer/config/OutboxProcessor.java` |
| 4 | DELETE | `services/customer-service/src/main/java/.../customer/config/OutboxRelay.java` |
| 5 | DELETE | `services/insurance-service/src/main/java/.../insurance/config/OutboxProcessor.java` |
| 6 | DELETE | `services/insurance-service/src/main/java/.../insurance/config/OutboxRelay.java` |
| 7 | DELETE | `services/estimation-service/src/main/java/.../estimation/config/OutboxProcessor.java` |
| 8 | DELETE | `services/estimation-service/src/main/java/.../estimation/config/OutboxRelay.java` |
| 9 | CREATE/MOVE | `common/common-message/src/test/.../common/config/OutboxProcessorTest.java` |
| 10 | UPDATE | Each service's `application.yml` — add property aliases if needed |
| 11 | CHECK | Each service's component scan includes `com.insurancemanagementsystem.common.config` |
| 12 | CHECK | `common-message/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |

---

## Files to Read for Full Context

| File | Purpose |
|------|---------|
| `common/common-message/build.gradle.kts` | Verify dependencies available (TransactionTemplate, JPA) |
| `common/common-message/src/main/java/.../common/config/CommonPersistenceAutoConfiguration.java` | Existing auto-configuration pattern |
| `common/common-message/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | Auto-config registration |
| Each service's `application.yml` | Property prefix patterns |
| `docs/outlines/07_PROJECT_STRUCTURE.md` | Module dependency rules |
| `docs/outlines/01_SYSTEM_ARCHITECTURE.md` | Cross-cutting concern guidelines |
| `docs/plans/06_CODEBASE_IMPROVEMENT_12_DRY_EXTRACTION.md` | Original DRY extraction plan |

---

## Verification Checklist

- [ ] Common `OutboxProcessor` compiles in common-message module
- [ ] Common `OutboxRelay` compiles in common-message module
- [ ] All three services compile without per-service `OutboxProcessor`/`OutboxRelay`
- [ ] All three services start up and component scan discovers the common beans
- [ ] Outbox property values resolve correctly in each service
- [ ] All existing `OutboxProcessorTest` tests pass (from common module)
- [ ] Integration tests for each service still pass (outbox events published)
- [ ] No import of `*.customer.config.OutboxProcessor` or `*.insurance.config.OutboxProcessor` or `*.estimation.config.OutboxProcessor` remains in any file

---

## Risk Assessment

**RISK: MEDIUM.** The classes are identical so the logic change is zero-risk. The main risk is Spring bean discovery — if the common package isn't scanned, `OutboxProcessor` won't be a bean and `OutboxRelay` will fail with `NoSuchBeanDefinitionException`. Verify component scanning before removing per-service copies. The `CommonPersistenceAutoConfiguration` already scans `com.insurancemanagementsystem.common.entity`, so adding `com.insurancemanagementsystem.common.config` to the scan path is a one-line change.
