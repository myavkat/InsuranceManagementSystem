# Plan: Fix 05 — Validate `status` Parameter with User-Friendly Error Message

## Objective
Fix the `EstimationService.findAll()` method to validate the `status` query parameter and return a **user-friendly error message** for invalid values, instead of the raw Java enum error.

## Root Cause (Corrected)

### Current behavior
In `EstimationService.java:41,46`:
```java
Estimation.Status statusEnum = Estimation.Status.valueOf(status.toUpperCase());
```

If a client calls `GET /api/estimations?status=INVALID`, this line throws `IllegalArgumentException` with the message:
```
No enum constant com.insurancemanagementsystem.estimation.entity.Estimation.Status.INVALID
```

**Status code is already correct:** The shared `GlobalExceptionHandler` (in `common/common-web`) already catches `IllegalArgumentException` and maps it to **400 Bad Request** (confirmed in `GlobalExceptionHandler.java:36-41`).

**The real problem:** The error message is a raw Java enum error — internally-focused, exposes package structure, and doesn't tell the user what valid values are.

### Expected behavior
**400 Bad Request** with a clear, actionable message: `"Invalid status: 'INVALID'. Valid values: STARTED, COMPLETED, REJECTED"`

## Context Files to Read First

1. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/service/EstimationService.java`** — The `findAll()` method (lines 37-53)
2. **`common/common-web/src/main/java/com/insurancemanagementsystem/common/web/exception/GlobalExceptionHandler.java`** — Existing exception handler (check if `IllegalArgumentException` is already handled)
3. **`services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/service/EstimationServiceTest.java`** — Existing service tests
4. **`services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/controller/EstimationControllerTest.java`** — Existing controller tests

## Design Decision: Enum vs String validation

**Option A:** Validate in `EstimationService` before calling `valueOf()`:
```java
try {
    Estimation.Status statusEnum = Estimation.Status.valueOf(status.toUpperCase());
} catch (IllegalArgumentException e) {
    throw new IllegalArgumentException(
        "Invalid status: " + status + ". Valid values: STARTED, COMPLETED, REJECTED");
}
```
- Pro: Simple, minimal change
- Con: Still throws `IllegalArgumentException` — need to ensure GlobalExceptionHandler maps it to 400

**Option B:** Add a static helper to `Estimation.Status` enum:
```java
public enum Status {
    STARTED, COMPLETED, REJECTED;

    public static Status fromString(String value) {
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid status: " + value + ". Valid values: " + 
                Arrays.toString(values()));
        }
    }
}
```
- Pro: Self-documenting, reusable
- Con: More changes

**Option C:** Use `@Validated` on the controller with a custom validator
- Pro: Declarative
- Con: Over-engineering for a simple validation

**Chosen approach: Option A** — validate in `EstimationService.findAll()`. The `IllegalArgumentException` already maps to 400 in a well-configured `GlobalExceptionHandler`. If `GlobalExceptionHandler` doesn't handle it yet, add a handler.

## Files to Modify

### 1. `EstimationService.java` — Add validation in `findAll()`

**Locate the two places where `valueOf()` is called** (lines 41 and 46):

```java
// Line 41 — inside (customerId != null && status != null) branch:
Estimation.Status statusEnum = Estimation.Status.valueOf(status.toUpperCase());

// Line 46 — inside (status != null) branch:
Estimation.Status statusEnum = Estimation.Status.valueOf(status.toUpperCase());
```

**Replace both occurrences with a common helper or inline try-catch:**

```java
// Reusable helper at the bottom of EstimationService.java:
private Estimation.Status parseStatus(String status) {
    try {
        return Estimation.Status.valueOf(status.toUpperCase());
    } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(
                "Invalid status: '" + status + "'. Valid values: STARTED, COMPLETED, REJECTED");
    }
}
```

Then replace BOTH `valueOf()` calls with `parseStatus(status)`:

**BEFORE:**
```java
if (customerId != null && status != null) {
    Estimation.Status statusEnum = Estimation.Status.valueOf(status.toUpperCase());
    estimations = estimationRepository.findByCustomerIdAndStatus(customerId, statusEnum, pageable);
} else if (customerId != null) {
    estimations = estimationRepository.findByCustomerId(customerId, pageable);
} else if (status != null) {
    Estimation.Status statusEnum = Estimation.Status.valueOf(status.toUpperCase());
    estimations = estimationRepository.findByStatus(statusEnum, pageable);
} else {
    estimations = estimationRepository.findAll(pageable);
}
```

**AFTER:**
```java
if (customerId != null && status != null) {
    Estimation.Status statusEnum = parseStatus(status);
    estimations = estimationRepository.findByCustomerIdAndStatus(customerId, statusEnum, pageable);
} else if (customerId != null) {
    estimations = estimationRepository.findByCustomerId(customerId, pageable);
} else if (status != null) {
    Estimation.Status statusEnum = parseStatus(status);
    estimations = estimationRepository.findByStatus(statusEnum, pageable);
} else {
    estimations = estimationRepository.findAll(pageable);
}
```

### 2. GlobalExceptionHandler — NO CHANGE NEEDED

The shared `GlobalExceptionHandler` (in `common/common-web`) already catches `IllegalArgumentException` and maps it to **400 Bad Request** (confirmed at `GlobalExceptionHandler.java:36-41`):

```java
@ExceptionHandler(IllegalArgumentException.class)
public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
    return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(ex.getMessage()));
}
```

**No changes needed to GlobalExceptionHandler.** The only change needed is in `EstimationService.java` to throw `IllegalArgumentException` with a user-friendly message instead of relying on the raw Java enum error message.

## Test Updates

### `EstimationServiceTest.java` — Add test for invalid status

Add a new test case:

```java
@Test
void findAll_withInvalidStatus_throwsIllegalArgumentException() {
    Pageable pageable = PageRequest.of(0, 20);

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> estimationService.findAll(null, "INVALID", pageable));
    assertThat(exception.getMessage()).contains("Invalid status");
    assertThat(exception.getMessage()).contains("INVALID");

    verify(estimationRepository, never()).findAll(any(Pageable.class));
    verify(estimationRepository, never()).findByStatus(any(Estimation.Status.class), any(Pageable.class));
}
```

Also test with customerId + invalid status:

```java
@Test
void findAll_withCustomerIdAndInvalidStatus_throwsIllegalArgumentException() {
    Pageable pageable = PageRequest.of(0, 20);
    UUID customerId = UUID.randomUUID();

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> estimationService.findAll(customerId, "INVALID", pageable));
    assertThat(exception.getMessage()).contains("Invalid status");

    verify(estimationRepository, never()).findByCustomerIdAndStatus(any(), any(), any(Pageable.class));
}
```

### `EstimationControllerTest.java` — Add test for invalid status via REST

Add a new test case to verify the controller returns 400:

```java
@Test
void getAll_WithInvalidStatus_Returns400() {
    given(estimationService.findAll(isNull(), eq("INVALID"), any(Pageable.class)))
            .willThrow(new IllegalArgumentException("Invalid status: 'INVALID'. Valid values: STARTED, COMPLETED, REJECTED"));

    restTestClient.get().uri("/api/estimations?status=INVALID")
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.success").isEqualTo(false)
            .jsonPath("$.message").contains("Invalid status")
            .jsonPath("$.message").contains("INVALID");
}
```

## Verification

```bash
# 1. Compile
.\gradlew.bat :services:estimation-service:compileJava

# 2. Run service tests
.\gradlew.bat :services:estimation-service:test --tests "*EstimationServiceTest"

# 3. Run controller tests
.\gradlew.bat :services:estimation-service:test --tests "*EstimationControllerTest"

# 4. Run all tests
.\gradlew.bat :services:estimation-service:test
```

---

## Files Summary

### Modified
- `services/estimation-service/src/main/java/.../service/EstimationService.java` — Add `parseStatus()` helper; replace `valueOf()` calls
- `services/estimation-service/src/test/java/.../service/EstimationServiceTest.java` — Add 2 tests for invalid status
- `services/estimation-service/src/test/java/.../controller/EstimationControllerTest.java` — Add 1 test for 400 response

### NOT Modified
- `common/common-web/src/main/java/.../exception/GlobalExceptionHandler.java` — Already correctly maps `IllegalArgumentException` to 400. No changes needed.

---

## Important Notes for Implementer

1. **Case insensitivity:** The `parseStatus()` method uses `status.toUpperCase()` before `valueOf()`, so `?status=started` and `?status=STARTED` both work. This preserves existing behavior.

2. **Null safety:** `parseStatus()` is only called when `status != null` (guarded by the if/else structure). No null check needed inside the method.

3. **Error message clarity:** The error message lists all valid values. This helps API consumers fix their request without reading docs.

4. **Do NOT change `@NotNull` DTO validation:** The `EstimationRequest` DTO already has `@NotNull` validation for `customerId`, `insuranceTypeId`, `companyId`. The controller test for 400 on missing fields is separate and already correct.

5. **GlobalExceptionHandler already correct:** The shared handler already returns 400 for `IllegalArgumentException`. The fix only needs to change the exception MESSAGE from the raw Java enum error to a user-friendly one. No handler changes needed.
