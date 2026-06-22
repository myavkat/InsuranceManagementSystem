# Sub-Plan 4: Insurance Service — Tests

**Parent Plan:** `docs/plans/03_SPRINT2_INSURANCE_SERVICE.md`
**Checklist items:** 4.1 through 4.5
**Prerequisite:** Sub-plans 1, 2, and 3 must be COMPLETE — all production code exists.

---

## Context Files to Read

Before implementing, Read these exact test pattern files:
- `services/customer-service/src/test/java/com/insurancemanagementsystem/customer/service/CustomerServiceTest.java` — service unit test pattern
- `services/customer-service/src/test/java/com/insurancemanagementsystem/customer/controller/CustomerControllerTest.java` — controller slice test pattern
- `services/customer-service/src/test/java/com/insurancemanagementsystem/customer/saga/CustomerSagaConsumerTest.java` — SAGA consumer test pattern
- `services/customer-service/src/test/java/com/insurancemanagementsystem/customer/CustomerServiceApplicationTests.java` — integration test pattern

**Your production code (read to understand what you're testing):**
- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/service/InsuranceService.java`
- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/controller/InsuranceController.java`
- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/config/InsuranceSagaConsumer.java`
- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/config/InsuranceEventPublisher.java`
- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/config/SagaAggregationStore.java`
- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/entity/Insurance.java`
- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/entity/InsuranceCompany.java`
- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/entity/InsuranceType.java`

**Common-message event schemas (read as needed):**
- `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/CustomerValidatedEvent.java`
- `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/VehicleValidatedEvent.java`
- `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/EstimationRequestedEvent.java`
- `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/PremiumCalculatedEvent.java`
- `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/CalculationFailedEvent.java`
- `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/CustomerInvalidatedEvent.java`
- `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/VehicleInvalidatedEvent.java`
- `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/domain/InsuranceCreatedEvent.java`
- `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/domain/InsuranceUpdatedEvent.java`
- `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/EventEnvelope.java`
- `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/EventConstants.java`
- `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/BaseEvent.java`

**Test conventions to follow:**
- `docs/outlines/11_TESTING_CONVENTIONS.md` — REST testing, assertions, DB isolation rules
- `docs/outlines/10_JAVA_CONVENTIONS.md` — datetime conventions, Lombok order

---

## Key Testing Conventions (from outline)

### HTTP Client: `RestTestClient`
- Never use `TestRestTemplate`
- Slice tests: wrap `@Autowired MockMvc` with `RestTestClient.bindTo(mockMvc).build()`
- Integration tests: `@AutoConfigureRestTestClient` + `@Autowired RestTestClient`

### Assertions
- HTTP assertions → `.jsonPath()` (not `objectMapper.readTree() + assertThat()`)
- Domain assertions → AssertJ `assertThat()`
- `readTree()` acceptable ONLY to extract entity IDs for DB verification

### Database Isolation
- `@BeforeEach` cleanup preferred over `@DirtiesContext`
- Never rely on test order

---

## Step 4.1: Service Layer Unit Tests

**File to CREATE:** `services/insurance-service/src/test/java/com/insurancemanagementsystem/insurance/service/InsuranceServiceTest.java`

**Pattern:** Follow `CustomerServiceTest.java` structure. Mock repositories and event publisher.

**Tests to implement:**

| # | Test | Verification |
|---|------|-------------|
| 1 | `create_withValidRequest_returnsInsuranceResponse` | Mock repos, verify saved fields, verify event published |
| 2 | `create_withDuplicateName_throwsIllegalArgumentException` | Mock `findByNameIgnoreCase` returns existing, verify exception |
| 3 | `create_withInvalidTypeId_throwsIllegalArgumentException` | Mock type repo returns empty, verify exception |
| 4 | `findById_whenExists_returnsInsuranceResponse` | Mock findById returns active entity, verify fields |
| 5 | `findById_whenNotExists_throwsEntityNotFoundException` | Mock findById returns empty |
| 6 | `findById_whenInactive_throwsEntityNotFoundException` | Mock findById returns entity with isActive=false |
| 7 | `softDelete_setsIsActiveFalse` | Mock findById, save, verify isActive=false |
| 8 | `update_updatesAllFields` | Mock findById, save, verify fields changed, event published |
| 9 | `update_withChangedNameAndDuplicate_throwsIllegalArgumentException` | Mock findByNameIgnoreCase returns other entity |
| 10 | `findAll_withTypeFilter_returnsFilteredPage` | Mock findByTypeIdAndIsActiveTrue, verify filter applied |
| 11 | `findAll_withSearch_returnsFilteredPage` | Mock searchByName, verify search applied |
| 12 | `findAll_withoutFilter_returnsAllActive` | Mock findByIsActiveTrue, verify all active returned |

Use `@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks`.

---

## Step 4.2: Controller Slice Tests

**File to CREATE:** `services/insurance-service/src/test/java/com/insurancemanagementsystem/insurance/controller/InsuranceControllerTest.java`

**Pattern:** Follow `CustomerControllerTest.java` exactly.

```java
package com.insurancemanagementsystem.insurance.controller;

import com.insurancemanagementsystem.common.web.exception.GlobalExceptionHandler;
import com.insurancemanagementsystem.insurance.dto.InsuranceCompanyRequest;
import com.insurancemanagementsystem.insurance.dto.InsuranceCompanyResponse;
import com.insurancemanagementsystem.insurance.dto.InsuranceRequest;
import com.insurancemanagementsystem.insurance.dto.InsuranceResponse;
import com.insurancemanagementsystem.insurance.service.InsuranceService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@WebMvcTest(controllers = InsuranceController.class)
@Import(GlobalExceptionHandler.class)
class InsuranceControllerTest {
    // ... follow CustomerControllerTest pattern
}
```

**Tests to implement (mirror customer controller pattern):**

| # | HTTP Method | URI | Scenario | Expected Status | Verify |
|---|------------|-----|----------|----------------|--------|
| 1 | GET | `/api/insurances` | List all | 200 | `.jsonPath("$.success")` true, paginated |
| 2 | GET | `/api/insurances?typeId=1` | Filter by type | 200 | service called with typeId=1 |
| 3 | GET | `/api/insurances?search=Kasko` | Search by name | 200 | service.search called |
| 4 | GET | `/api/insurances/{id}` | Found | 200 | `.jsonPath("$.data.name")` matches |
| 5 | GET | `/api/insurances/{id}` | Not found | 404 | `.jsonPath("$.success")` false |
| 6 | POST | `/api/insurances` | Valid | 201 | `.jsonPath("$.data.name")` matches |
| 7 | POST | `/api/insurances` | Invalid | 400 | `.jsonPath("$.success")` false |
| 8 | PUT | `/api/insurances/{id}` | Valid | 200 | fields updated |
| 9 | DELETE | `/api/insurances/{id}` | Valid | 200 | soft-deleted |
| 10 | GET | `/api/insurances/types` | List types | 200 | types array |
| 11 | GET | `/api/insurances/companies` | List companies | 200 | companies page |
| 12 | POST | `/api/insurances/companies` | Create company | 201 | company created |
| 13 | PUT | `/api/insurances/companies/{id}` | Update company | 200 | company updated |

Use `@MockitoBean` for `InsuranceService`. Create helper methods for sample request/response DTOs.

---

## Step 4.3: SAGA Consumer Tests

**File to CREATE:** `services/insurance-service/src/test/java/com/insurancemanagementsystem/insurance/saga/InsuranceSagaConsumerTest.java`

**Pattern:** Follow `CustomerSagaConsumerTest.java` EXACTLY. Key differences: 
- Insurance SAGA consumer needs both CustomerValidated AND VehicleValidated before calculating
- Also need EstimationRequested to provide insurance type/company context
- Mock `InsuranceRepository`, `MessagePublisher`, `DeduplicationStore`, `SagaAggregationStore` as needed

```java
package com.insurancemanagementsystem.insurance.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.insurancemanagementsystem.common.event.EventConstants;
import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.event.saga.*;
import com.insurancemanagementsystem.insurance.config.MessagePublisher;
import com.insurancemanagementsystem.insurance.entity.Insurance;
import com.insurancemanagementsystem.insurance.entity.InsuranceCompany;
import com.insurancemanagementsystem.insurance.entity.InsuranceType;
import com.insurancemanagementsystem.insurance.repository.InsuranceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
```

**Tests to implement:**

| # | Test | Setup | Assertion |
|---|------|-------|-----------|
| 1 | `allThreeEvents_valid_shouldPublishPremiumCalculated` | Send EstimationRequested, CustomerValidated, VehicleValidated (any order) | Verify `PremiumCalculated` published with correct premium |
| 2 | `customerInvalidated_shouldPublishCalculationFailed` | Send EstimationRequested + CustomerInvalidated | Verify `CalculationFailed` published |
| 3 | `vehicleInvalidated_shouldPublishCalculationFailed` | Send EstimationRequested + VehicleInvalidated | Verify `CalculationFailed` published |
| 4 | `noMatchingInsurance_shouldPublishCalculationFailed` | Send all 3 events but no Insurance entity for typeId+companyId | Verify `CalculationFailed` published |
| 5 | `duplicateEvents_shouldBeIdempotent` | Send duplicate CustomerValidated | Verify each event type processed only once per sagaId |
| 6 | `eventsOutOfOrder_shouldStillCalculate` | Send VehicleValidated first, then CustomerValidated, then EstimationRequested | Verify calculation still succeeds |

**Test setup requirements:**
- Use `@EmbeddedKafka(topics = {"estimation.saga"}, partitions = 1)`
- PostgreSQL Testcontainer
- Populate InsuranceType (id=1, name=TRAFFIC), InsuranceCompany (UUID, name=TestCo), Insurance (typeId=1, companyId, basePremium=1000) in the test DB before sending SAGA events
- Log the published events for verification

---

## Step 4.4: Integration Tests

**File to CREATE:** `services/insurance-service/src/test/java/com/insurancemanagementsystem/insurance/InsuranceServiceApplicationTests.java`

**Pattern:** Follow `CustomerServiceApplicationTests.java` EXACTLY. Use `RestTestClient`, PostgreSQL + Kafka Testcontainers.

```java
package com.insurancemanagementsystem.insurance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.insurancemanagementsystem.insurance.dto.InsuranceRequest;
import com.insurancemanagementsystem.insurance.entity.Insurance;
import com.insurancemanagementsystem.insurance.entity.InsuranceCompany;
import com.insurancemanagementsystem.insurance.entity.InsuranceType;
import com.insurancemanagementsystem.insurance.repository.InsuranceCompanyRepository;
import com.insurancemanagementsystem.insurance.repository.InsuranceRepository;
import com.insurancemanagementsystem.insurance.repository.InsuranceTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
```

**Tests to implement:**

| # | Test | Steps | Key Assertions |
|---|------|-------|---------------|
| 1 | `contextLoads` | (no action) | Context starts |
| 2 | `createInsuranceViaRest_verifyInDb` | POST → 201, read ID, query DB | Entity persisted, isActive=true |
| 3 | `listInsurances` | POST seed insurance, GET list | Paginated, contains created |
| 4 | `getById_NotFound_Returns404` | GET random UUID | 404 |
| 5 | `softDelete_thenGetReturns404` | POST → DELETE → GET | 404 after delete |
| 6 | `updateInsurance` | POST → PUT → GET | Fields updated, updatedAt refreshed |
| 7 | `createInsuranceOneWithtName_returns400` | POST with blank name | 400, validation errors |
| 8 | `createCompany_thenListCompanies` | POST company → GET companies | Company in list |
| 9 | `getTypes_returnsSeedData` | GET /api/insurances/types | 5 types (TRAFFIC, CASCO, DASK, HEALTH, LIFE) |

**Setup:**
- PostgreSQL Testcontainer + Kafka Testcontainer
- `create-drop` for JPA (auto-creates tables)
- Seed InsuranceType in `@BeforeEach` (type repository)

---

## Step 4.5: Run Full Test Suite

**Commands:**
```bash
# Run all tests
.\gradlew.bat :services:insurance-service:test

# Run only specific test classes
.\gradlew.bat :services:insurance-service:test --tests "*ServiceTest"
.\gradlew.bat :services:insurance-service:test --tests "*ControllerTest"
.\gradlew.bat :services:insurance-service:test --tests "*SagaConsumerTest"
.\gradlew.bat :services:insurance-service:test --tests "*ApplicationTests"
```

**Target:** ≥80% coverage. All tests green.
