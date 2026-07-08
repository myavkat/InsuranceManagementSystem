# Plan 06: Risk Factor API — CRUD Endpoints + History

## Objective

Add REST API endpoints for reading and updating risk factor values on an insurance product, plus a history endpoint for audit trail. These endpoints will be consumed by the admin UI (Plan 07) and the premium calculation (Plan 08).

## Dependencies

- [ ] Plan 05 (`05-risk-factor-backend.md`) — entities and repositories must exist

## Files to Read First

- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/controller/InsuranceController.java` — controller pattern to follow
- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/service/InsuranceService.java` — service pattern
- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/dto/InsuranceResponse.java` — DTO pattern
- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/entity/RiskFactor.java` — created in Plan 05
- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/entity/RiskFactorHistory.java` — created in Plan 05
- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/repository/RiskFactorRepository.java` — created in Plan 05
- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/repository/RiskFactorHistoryRepository.java` — created in Plan 05
- `common/common-message/src/main/java/com/insurancemanagementsystem/common/web/dto/ApiResponse.java` — standard response wrapper

## Technical Context

- **Controller pattern**: Methods return `ResponseEntity<ApiResponse<T>>` using `ApiResponse.success()`
- **Endpoint structure**: Nested under insurance: `/api/insurances/{id}/risk-factors`
- **DTO conventions**: `@Data @Builder @NoArgsConstructor @AllArgsConstructor`
- **Service conventions**: `@Service`, `@Transactional` for writes, `@Transactional(readOnly = true)` for reads
- **AGENTS.md SAGA rule**: Risk factor changes through the admin UI are simple CRUD with history recording — no SAGA needed (this is configuration data, not multi-service transactions)
- **JSON convention**: Use `JsonMapper` (Jackson 3) for any manual serialization; never build JSON via string concatenation

## API Design

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/insurances/{id}/risk-factors` | List all risk factors for an insurance |
| `PUT` | `/api/insurances/{id}/risk-factors` | Batch update risk factor values (accepts array) |
| `GET` | `/api/insurances/{id}/risk-factors/history` | Paginated change history |

### Request/Response DTOs

**RiskFactorResponse**:
```json
{
  "id": "uuid",
  "insuranceId": "uuid",
  "factorName": "motorSize",
  "factorValue": 0.75,
  "createdAt": "2026-07-09T...",
  "updatedAt": "2026-07-09T..."
}
```

**RiskFactorUpdateRequest** (batch):
```json
[
  { "factorName": "motorSize", "factorValue": 0.75 },
  { "factorName": "fuelType", "factorValue": 0.30 }
]
```

**RiskFactorHistoryResponse**:
```json
{
  "id": "uuid",
  "riskFactorId": "uuid",
  "factorName": "motorSize",
  "oldValue": 0.50,
  "newValue": 0.75,
  "changedAt": "2026-07-09T..."
}
```

## Steps

### Step 1: Create RiskFactorResponse DTO

Create new file: `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/dto/RiskFactorResponse.java`

```java
package com.insurancemanagementsystem.insurance.dto;

import com.insurancemanagementsystem.insurance.entity.RiskFactor;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskFactorResponse {
    private UUID id;
    private UUID insuranceId;
    private String factorName;
    private BigDecimal factorValue;
    private Instant createdAt;
    private Instant updatedAt;

    public static RiskFactorResponse fromEntity(RiskFactor entity) {
        return RiskFactorResponse.builder()
                .id(entity.getId())
                .insuranceId(entity.getInsuranceId())
                .factorName(entity.getFactorName())
                .factorValue(entity.getFactorValue())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
```

### Step 2: Create RiskFactorUpdateRequest DTO

Create new file: `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/dto/RiskFactorUpdateRequest.java`

```java
package com.insurancemanagementsystem.insurance.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskFactorUpdateRequest {
    @NotBlank
    private String factorName;

    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("1.00")
    private BigDecimal factorValue;
}
```

### Step 3: Create RiskFactorHistoryResponse DTO

Create new file: `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/dto/RiskFactorHistoryResponse.java`

```java
package com.insurancemanagementsystem.insurance.dto;

import com.insurancemanagementsystem.insurance.entity.RiskFactorHistory;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskFactorHistoryResponse {
    private UUID id;
    private UUID riskFactorId;
    private UUID insuranceId;
    private String factorName;
    private BigDecimal oldValue;
    private BigDecimal newValue;
    private Instant changedAt;

    public static RiskFactorHistoryResponse fromEntity(RiskFactorHistory entity) {
        return RiskFactorHistoryResponse.builder()
                .id(entity.getId())
                .riskFactorId(entity.getRiskFactorId())
                .insuranceId(entity.getInsuranceId())
                .factorName(entity.getFactorName())
                .oldValue(entity.getOldValue())
                .newValue(entity.getNewValue())
                .changedAt(entity.getChangedAt())
                .build();
    }
}
```

### Step 4: Add service methods to InsuranceService

Open `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/service/InsuranceService.java`.

**Inject** the new repositories (add to constructor or add `@RequiredArgsConstructor` fields):
```java
private final RiskFactorRepository riskFactorRepository;
private final RiskFactorHistoryRepository riskFactorHistoryRepository;
```

**Add** these methods to `InsuranceService`:

```java
// ---------- Risk Factors ----------

@Transactional(readOnly = true)
public List<RiskFactorResponse> getRiskFactors(UUID insuranceId) {
    return riskFactorRepository.findByInsuranceId(insuranceId).stream()
            .map(RiskFactorResponse::fromEntity)
            .toList();
}

@Transactional
public List<RiskFactorResponse> updateRiskFactors(UUID insuranceId,
                                                   List<RiskFactorUpdateRequest> updates) {
    List<RiskFactorResponse> results = new java.util.ArrayList<>();

    for (RiskFactorUpdateRequest update : updates) {
        RiskFactor factor = riskFactorRepository
                .findByInsuranceIdAndFactorName(insuranceId, update.getFactorName())
                .orElseThrow(() -> new EntityNotFoundException(
                    "Risk factor '" + update.getFactorName() +
                    "' not found for insurance " + insuranceId));

        BigDecimal oldValue = factor.getFactorValue();
        BigDecimal newValue = update.getFactorValue();

        // Skip if no change
        if (oldValue.compareTo(newValue) == 0) {
            results.add(RiskFactorResponse.fromEntity(factor));
            continue;
        }

        // Update the factor
        factor.setFactorValue(newValue);
        factor = riskFactorRepository.save(factor);

        // Record history
        RiskFactorHistory history = RiskFactorHistory.builder()
                .riskFactorId(factor.getId())
                .insuranceId(insuranceId)
                .factorName(factor.getFactorName())
                .oldValue(oldValue)
                .newValue(newValue)
                .changedAt(Instant.now())
                .build();
        riskFactorHistoryRepository.save(history);

        results.add(RiskFactorResponse.fromEntity(factor));
    }

    return results;
}

@Transactional(readOnly = true)
public Page<RiskFactorHistoryResponse> getRiskFactorHistory(UUID insuranceId, Pageable pageable) {
    return riskFactorHistoryRepository
            .findByInsuranceIdOrderByChangedAtDesc(insuranceId, pageable)
            .map(RiskFactorHistoryResponse::fromEntity);
}
```

### Step 5: Add controller endpoints to InsuranceController

Open `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/controller/InsuranceController.java`.

**Add** these methods (alongside existing methods, before the class closing brace):

```java
// ---------------------------------------------------------------
// Risk Factors
// ---------------------------------------------------------------

@GetMapping("/{id}/risk-factors")
public ResponseEntity<ApiResponse<List<RiskFactorResponse>>> getRiskFactors(@PathVariable UUID id) {
    List<RiskFactorResponse> factors = insuranceService.getRiskFactors(id);
    return ResponseEntity.ok(ApiResponse.success(factors));
}

@PutMapping("/{id}/risk-factors")
public ResponseEntity<ApiResponse<List<RiskFactorResponse>>> updateRiskFactors(
        @PathVariable UUID id,
        @Valid @RequestBody List<RiskFactorUpdateRequest> updates) {
    List<RiskFactorResponse> updated = insuranceService.updateRiskFactors(id, updates);
    return ResponseEntity.ok(ApiResponse.success("Risk factors updated", updated));
}

@GetMapping("/{id}/risk-factors/history")
public ResponseEntity<ApiResponse<Page<RiskFactorHistoryResponse>>> getRiskFactorHistory(
        @PathVariable UUID id,
        @PageableDefault(sort = "changedAt", direction = Sort.Direction.DESC) Pageable pageable) {
    Page<RiskFactorHistoryResponse> history = insuranceService.getRiskFactorHistory(id, pageable);
    return ResponseEntity.ok(ApiResponse.success(history));
}
```

**Add imports** for the new DTOs at the top of the file.

## Acceptance Criteria

- [ ] `GET /api/insurances/{id}/risk-factors` returns all risk factors for that insurance (list of RiskFactorResponse)
- [ ] `PUT /api/insurances/{id}/risk-factors` accepts a JSON array, updates matching factors, and records history entries
- [ ] `PUT /api/insurances/{id}/risk-factors` returns 404 if factor_name doesn't exist for that insurance
- [ ] `PUT /api/insurances/{id}/risk-factors` skips factors where old_value == new_value (no history noise)
- [ ] `GET /api/insurances/{id}/risk-factors/history` returns paginated history, most recent first
- [ ] History entries show old_value (null for first creation — or handled by seed), new_value, and changed_at
- [ ] All responses use `ApiResponse` wrapper
