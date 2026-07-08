# Plan 08: Wire Risk Factors into Premium Calculation

## Objective

Replace the hardcoded `BigDecimal.ONE` placeholder in `InsuranceSagaConsumer.calculatePremium()` with actual risk factor lookup and application. The premium should now reflect the risk factors configured for the selected insurance product, and the breakdown should include the factors used.

## Dependencies

- [ ] Plan 05 (`05-risk-factor-backend.md`) — RiskFactor entity, repository, and seed data must exist
- [ ] Plan 01 (`01-seed-data-restructure.md`) — insurance type IDs (Vehicle=1, RealEstate=2, Health=3, Life=4)

## Files to Read First

- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/config/InsuranceSagaConsumer.java` — main file to modify
- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/repository/RiskFactorRepository.java` — created in Plan 05
- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/entity/RiskFactor.java` — created in Plan 05
- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/config/SagaAggregationStore.java` — understanding aggregation state
- `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/PremiumCalculatedEvent.java` — event DTO
- `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/EstimationRequestedEvent.java` — SAGA event with insuranceTypeId
- `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/CustomerValidatedEvent.java` — customer data
- `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/VehicleValidatedEvent.java` — vehicle data

## Technical Context

- **Current premium formula** (lines 230-240 of `InsuranceSagaConsumer.java`):
  ```java
  BigDecimal riskFactor = BigDecimal.ONE;
  Map<String, BigDecimal> breakdown = new LinkedHashMap<>();
  breakdown.put("basePremium", basePremium);
  BigDecimal measuredAdjustment = BigDecimal.ZERO;
  breakdown.put("riskFactor", riskFactor);
  breakdown.put("adjustment", measuredAdjustment);
  BigDecimal totalPremium = basePremium.multiply(riskFactor).add(measuredAdjustment);
  ```

- **New formula**: Instead of a single `riskFactor`, compute a **composite risk multiplier** from multiple factors:
  - For Vehicle-type: motorSize, fuelType, carAge, brandRisk + customerAge, profession, city
  - For RealEstate-type: buildingAge, constructionType, luxuryClass, floorArea + customerAge, profession, city
  - For Health/Life: customerAge, profession, city only

- **How factor values work**: Each factor is 0.0 (no risk) to 1.0 (highest risk). The composite risk multiplier should be an average of all applicable factors:
  ```
  compositeRisk = (factor1 + factor2 + ... + factorN) / N
  ```

- **The premium formula becomes**:
  ```
  totalPremium = basePremium * compositeRisk
  ```

  This means if all factors are at their default 0.50, the compositeRisk is 0.50, and the premium is 50% of base. This is a sensible default — adjust factor values to increase/decrease risk weighting.

- **AGENTS.md SAGA consumer rules**:
  - Wrap DB writes in `transactionTemplate.executeWithoutResult()` — already done
  - Use `tryInsertDedup()` for idempotency — already done
  - **Do NOT use `existsBySagaIdAndEventType()` followed by `save()`** — TOCTOU race
  - **JSON via ObjectMapper only** — use `jsonMapper.writeValueAsString()`, already done
- **Trace propagation**: Outbound events must carry the original `traceId` — already done

## Steps

### Step 1: Inject RiskFactorRepository into InsuranceSagaConsumer

Open `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/config/InsuranceSagaConsumer.java`.

**Add** the field:
```java
private final RiskFactorRepository riskFactorRepository;
```

The class uses `@RequiredArgsConstructor`, so Lombok will add it to the constructor automatically. Just add the field declaration among the other `private final` fields.

### Step 2: Replace the premium calculation logic

**Locate** the `calculatePremium()` method (around line 188).

**Replace** the calculation block (lines 230-240):
```java
// Calculate premium: basePremium * risk factor
BigDecimal riskFactor = BigDecimal.ONE;

Map<String, BigDecimal> breakdown = new LinkedHashMap<>();
breakdown.put("basePremium", basePremium);

BigDecimal measuredAdjustment = BigDecimal.ZERO;
breakdown.put("riskFactor", riskFactor);
breakdown.put("adjustment", measuredAdjustment);

BigDecimal totalPremium = basePremium.multiply(riskFactor).add(measuredAdjustment);
```

**With**:
```java
// Load risk factors for this insurance product
List<RiskFactor> riskFactors = riskFactorRepository.findByInsuranceId(insurance.getId());

// Compute composite risk multiplier (average of all factor values)
BigDecimal compositeRisk;
Map<String, BigDecimal> breakdown = new LinkedHashMap<>();
breakdown.put("basePremium", basePremium);

if (riskFactors.isEmpty()) {
    // No risk factors configured — use neutral 0.50
    compositeRisk = new BigDecimal("0.50");
    breakdown.put("compositeRisk", compositeRisk);
} else {
    BigDecimal sum = BigDecimal.ZERO;
    for (RiskFactor rf : riskFactors) {
        sum = sum.add(rf.getFactorValue());
        breakdown.put("factor." + rf.getFactorName(), rf.getFactorValue());
    }
    compositeRisk = sum.divide(
        BigDecimal.valueOf(riskFactors.size()), 4, java.math.RoundingMode.HALF_UP);
    breakdown.put("compositeRisk", compositeRisk);
}

// Calculate total premium
// premium = basePremium * compositeRisk
BigDecimal totalPremium = basePremium.multiply(compositeRisk).setScale(2, java.math.RoundingMode.HALF_UP);
breakdown.put("totalPremium", totalPremium);
```

### Step 3: Import RiskFactor entity

**Add** import at the top of `InsuranceSagaConsumer.java`:
```java
import com.insurancemanagementsystem.insurance.entity.RiskFactor;
import java.util.List;
```

(Check if `List` is already imported — it might not be since the existing code only uses `Map`, `Optional`, `UUID`.)

### Step 4: Update PremiumCalculatedEvent if needed

Open `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/PremiumCalculatedEvent.java`.

Check the `breakdown` field type — it should be `Map<String, BigDecimal>`. This supports arbitrary key-value pairs, so the new breakdown keys (`factor.motorSize`, `compositeRisk`, `basePremium`, `totalPremium`) are fine. No changes needed to the event structure.

### Step 5: The breakdown is recorded in the estimation

The `PremiumCalculatedEvent` with its `breakdown` map flows through Kafka to the `EstimationSagaConsumer`. Check what happens to the breakdown data:

Open `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumer.java`.

Look at how the `PremiumCalculatedEvent` is handled — the breakdown should be stored in the `Estimation.details` JSONB column. If it's stored, the risk factor snapshot is preserved for historical reference, meeting the requirement: "change of risk factor values should be recorded to make historical estimations based on the old factors deducible."

If the breakdown is NOT currently stored in `details`, add:
```java
// After updating estimation status to COMPLETED:
String detailsJson;
try {
    detailsJson = jsonMapper.writeValueAsString(premiumEvent.getBreakdown());
} catch (Exception e) {
    detailsJson = "{}";
}
estimation.setDetails(detailsJson);
```

### Step 6: Build and verify

Run the build for the insurance-service and estimation-service modules:
```bash
cd services/insurance-service && ../gradlew build
cd services/estimation-service && ../gradlew build
```

> Use project commands from `docs/outlines/12_DEVELOPER_COMMANDS.md`.

## Acceptance Criteria

- [ ] `calculatePremium()` loads risk factors from `RiskFactorRepository` instead of using `BigDecimal.ONE`
- [ ] Composite risk is the arithmetic mean of all applicable factor values
- [ ] When no risk factors exist, falls back to 0.50 (neutral)
- [ ] Each factor value is recorded in the `breakdown` map (keyed as `factor.<name>`)
- [ ] `compositeRisk` and `totalPremium` are in the breakdown
- [ ] The breakdown is serialized into `Estimation.details` (JSONB) for historical reference
- [ ] Changing risk factor values through the admin UI affects future estimations
- [ ] Historical estimations retain their original breakdown (old factor values)
- [ ] Build succeeds for insurance-service and estimation-service
