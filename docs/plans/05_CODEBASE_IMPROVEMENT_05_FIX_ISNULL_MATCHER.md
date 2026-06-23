# Plan: Fix 10 — Replace Deprecated Mockito `isNull()` with Type-Safe `isNull(Class)` or `nullable(Class)`

## Objective

Replace the deprecated raw `isNull()` calls (from `org.mockito.ArgumentMatchers`) in `EstimationControllerTest.java` with the type-safe `isNull(Class)` or `nullable(Class)` variants, complying with Mockito 5+ best practices.

## Root Cause

In Mockito 5+, `org.mockito.ArgumentMatchers.isNull()` (without a class parameter) is **deprecated** in favor of:
- `isNull(Class<T> clazz)` — matches exactly null
- `nullable(Class<T> clazz)` — matches null OR a non-null value of the given type

The deprecated raw `isNull()` warns at compile time. Using the typed version is more explicit and prevents ambiguous method resolution.

## Cross-Service Analysis

| Service | File | `isNull()` usage | Type |
|---------|------|------------------|------|
| **estimation-service** | `EstimationControllerTest.java` | **8 uses** via `org.mockito.ArgumentMatchers.isNull()` | Mockito matcher (deprecated) |
| **customer-service** | `CustomerServiceApplicationTests.java` | 2 uses of `.isNull()` via `org.assertj.core.api.Assertions` | AssertJ assertion (NOT deprecated) |
| **estimation-service** | `EstimationTest.java` | 2 uses of `.isNull()` via AssertJ | AssertJ assertion (NOT deprecated) |

**Only the estimation-service controller test** uses the deprecated Mockito `isNull()`. The AssertJ `.isNull()` in other files is a different API and not deprecated.

## Which Calls to Fix

In `EstimationControllerTest.java`, **8 uses** of `isNull()` are Mockito matchers:

| Line | Context | Current code |
|------|---------|--------------|
| 176 | `given()` | `given(estimationService.findAll(isNull(), isNull(), any(Pageable.class)))` |
| 187 | `verify()` | `verify(estimationService).findAll(isNull(), isNull(), any(Pageable.class))` |
| 196 | `given()` | `given(estimationService.findAll(eq(customerId), isNull(), any(Pageable.class)))` |
| 206 | `verify()` | `verify(estimationService).findAll(eq(customerId), isNull(), any(Pageable.class))` |
| 215 | `given()` | `given(estimationService.findAll(isNull(), eq("STARTED"), any(Pageable.class)))` |
| 225 | `verify()` | `verify(estimationService).findAll(isNull(), eq("STARTED"), any(Pageable.class))` |
| 233 | `given()` | `given(estimationService.findAll(isNull(), eq("INVALID"), any(Pageable.class)))` |

The `findAll()` method signature is:
```java
Page<EstimationResponse> findAll(UUID customerId, String status, Pageable pageable);
```

So the two `isNull()` arguments are:
1. `UUID customerId` — should use `isNull(UUID.class)` or `nullable(UUID.class)`
2. `String status` — should use `isNull(String.class)` or `nullable(String.class)`

### Choice: `nullable()` vs `isNull()`

- `isNull(Class)` → matches ONLY null. If the method under test receives a non-null value, the stub won't match.
- `nullable(Class)` → matches BOTH null AND non-null. More flexible for stubbing.

**For `given()` stubs:** Use `nullable()` — the test shouldn't care whether the implementation passes null or a default value to the repository method. If the filter is absent, the controller passes null, but the service may convert it to a default. Using `nullable()` makes the stub more robust.

**For `verify()` checks:** Use `isNull(Class)` — verify that the service was called with exactly null (no filter provided).

## Context Files to Read First

1. **`services/estimation-service/src/test/java/.../estimation/controller/EstimationControllerTest.java`**
   - Current `import static org.mockito.ArgumentMatchers.*;`
   - All 8 `isNull()` usages

2. **`services/estimation-service/src/main/java/.../estimation/service/EstimationService.java`**
   - `findAll()` method signature (line ~39)

## Files to Modify

### 1. `services/estimation-service/src/test/java/.../estimation/controller/EstimationControllerTest.java`

**Change imports:**
```java
// BEFORE:
import static org.mockito.ArgumentMatchers.*;

// AFTER:
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
// or use nullable:
import static org.mockito.ArgumentMatchers.nullable;
```

**Replace all `isNull()` with `nullable()` in `given()` stubs, and `isNull(Class)` in `verify()` calls:**

Line 176:
```java
// BEFORE:
given(estimationService.findAll(isNull(), isNull(), any(Pageable.class))).willReturn(page);

// AFTER:
given(estimationService.findAll(nullable(UUID.class), nullable(String.class), any(Pageable.class))).willReturn(page);
```

Line 187:
```java
// BEFORE:
verify(estimationService).findAll(isNull(), isNull(), any(Pageable.class));

// AFTER:
verify(estimationService).findAll(isNull(UUID.class), isNull(String.class), any(Pageable.class));
```

Line 196:
```java
// BEFORE:
given(estimationService.findAll(eq(customerId), isNull(), any(Pageable.class))).willReturn(page);

// AFTER:
given(estimationService.findAll(eq(customerId), nullable(String.class), any(Pageable.class))).willReturn(page);
```

Line 206:
```java
// BEFORE:
verify(estimationService).findAll(eq(customerId), isNull(), any(Pageable.class));

// AFTER:
verify(estimationService).findAll(eq(customerId), isNull(String.class), any(Pageable.class));
```

Lines 215 and 233:
```java
// BEFORE:
given(estimationService.findAll(isNull(), eq("STARTED"), any(Pageable.class))).willReturn(page);
given(estimationService.findAll(isNull(), eq("INVALID"), any(Pageable.class)));

// AFTER:
given(estimationService.findAll(nullable(UUID.class), eq("STARTED"), any(Pageable.class))).willReturn(page);
given(estimationService.findAll(nullable(UUID.class), eq("INVALID"), any(Pageable.class)));
```

Line 225:
```java
// BEFORE:
verify(estimationService).findAll(isNull(), eq("STARTED"), any(Pageable.class));

// AFTER:
verify(estimationService).findAll(isNull(UUID.class), eq("STARTED"), any(Pageable.class));
```

### Complete mapping:

| Line | Current | Replacement |
|------|---------|-------------|
| 176 | `isNull(), isNull()` | `nullable(UUID.class), nullable(String.class)` |
| 187 | `isNull(), isNull()` | `isNull(UUID.class), isNull(String.class)` |
| 196 | `eq(customerId), isNull()` | `eq(customerId), nullable(String.class)` |
| 206 | `eq(customerId), isNull()` | `eq(customerId), isNull(String.class)` |
| 215 | `isNull(), eq("STARTED")` | `nullable(UUID.class), eq("STARTED")` |
| 225 | `isNull(), eq("STARTED")` | `isNull(UUID.class), eq("STARTED")` |
| 233 | `isNull(), eq("INVALID")` | `nullable(UUID.class), eq("INVALID")` |

## Verification

```bash
# 1. Compile estimation-service
.\gradlew.bat :services:estimation-service:compileTestJava

# 2. Run the controller tests specifically
.\gradlew.bat :services:estimation-service:test --tests "*EstimationControllerTest"

# 3. Run all estimation-service tests
.\gradlew.bat :services:estimation-service:test
```

## Execution Checklist

- [ ] Read `EstimationControllerTest.java` — identify all 8 `isNull()` usages
- [ ] Edit file — replace `import static org.mockito.ArgumentMatchers.*;` with specific imports
- [ ] Edit file — replace all 8 deprecated `isNull()` calls with typed variants
- [ ] Compile test: `BUILD SUCCESSFUL`
- [ ] All controller tests pass

## Risk Assessment

- **Risk:** VERY LOW. Pure compilation fix — no behavioral change. If `nullable(UUID.class)` matches more cases than `isNull()` (which it does — it also matches non-null UUIDs), the stub becomes more tolerant, not less. This cannot break tests.
- **Note:** There is a small risk that a `nullable()` stub "masks" a test failure by matching a non-null argument when null was expected. This is only relevant for `given()` stubs. The `verify()` calls should use `isNull(Class)` for strict checking.
