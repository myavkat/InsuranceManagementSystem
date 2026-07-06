# Plan: Sprint 4 — Vehicle & RealEstate — Step 5: Vehicle Service Tests

## Objective
Create all test layers for the Vehicle Service: unit tests for service, slice tests for controller, integration tests with Testcontainers, SAGA consumer tests with EmbeddedKafka. Achieve ≥80% JaCoCo coverage.

## Context Files to Read First
1. **`services/customer-service/src/test/java/com/insurancemanagementsystem/customer/service/CustomerServiceTest.java`** — Unit test pattern
2. **`services/customer-service/src/test/java/com/insurancemanagementsystem/customer/controller/CustomerControllerTest.java`** — Controller slice test pattern
3. **`services/customer-service/src/test/java/com/insurancemanagementsystem/customer/saga/CustomerSagaConsumerTest.java`** — SAGA consumer test pattern
4. **`services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/EstimationServiceIntegrationTest.java`** — Integration test pattern
5. **`services/estimation-service/src/test/resources/application-test.yml`** — Test configuration
6. **`docs/outlines/11_TESTING_CONVENTIONS.md`** — Testing conventions (RestTestClient, @WebMvcTest imports, JSONPath assertions)
7. All Vehicle Service source files from Steps 2-4

## Files to Create

### 1. `services/vehicle-service/src/test/resources/application-test.yml`

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: create-drop
vehicle:
  outbox:
    poll-interval-ms: 600000  # disable scheduled tasks during tests
```

### 2. `services/vehicle-service/src/test/java/com/insurancemanagementsystem/vehicle/service/VehicleServiceTest.java`

Unit tests for VehicleService with `@ExtendWith(MockitoExtension.class)`. Mock all repositories. Test:
- `findAll()` — returns paginated vehicles
- `findById()` — existing ID returns vehicle, non-existing throws EntityNotFoundException
- `create()` — valid request creates vehicle and returns response; duplicate plate throws IllegalArgumentException; invalid reference ID throws IllegalArgumentException
- `update()` — valid request updates vehicle; non-existing ID throws EntityNotFoundException
- `delete()` — existing ID deletes; non-existing throws EntityNotFoundException
- `getBrands()` / `getModelsByBrand()` / `getEngines()` etc. — return reference data

### 3. `services/vehicle-service/src/test/java/com/insurancemanagementsystem/vehicle/controller/VehicleControllerTest.java`

Slice tests with `@WebMvcTest(controllers = VehicleController.class)`, `@Import(GlobalExceptionHandler.class)`, `@MockitoBean` for VehicleService. Use `RestTestClient.bindTo(mockMvc).build()` in `@BeforeEach`. Test all 11 endpoints:
- `GET /api/vehicles` — returns 200 with paginated list
- `GET /api/vehicles/{id}` — existing returns 200, non-existing returns 404
- `POST /api/vehicles` — valid returns 201, invalid (missing plate) returns 400
- `PUT /api/vehicles/{id}` — valid returns 200
- `DELETE /api/vehicles/{id}` — returns 200
- `GET /api/vehicles/brands` — returns 200 with brand list
- `GET /api/vehicles/brands/{brandId}/models` — returns 200 with model list
- `GET /api/vehicles/engines` — returns 200
- `GET /api/vehicles/fuel-types` — returns 200
- `GET /api/vehicles/types` — returns 200
- `GET /api/vehicles/packages` — returns 200

IMPORTANT: Use Spring Boot 4 test imports:
- `@WebMvcTest` from `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`
- `@MockitoBean` from `org.springframework.test.context.bean.override.mockito.MockitoBean`
- `RestTestClient` from `org.springframework.test.web.servlet.client.RestTestClient`
- `@Import(GlobalExceptionHandler.class)` for error path testing
- Assert with `.jsonPath()` on response, not `objectMapper.readTree()`

### 4. `services/vehicle-service/src/test/java/com/insurancemanagementsystem/vehicle/saga/VehicleSagaConsumerTest.java`

SAGA consumer tests with `@SpringBootTest(RANDOM_PORT)`, `@Testcontainers`, `@EmbeddedKafka(topics = {"estimation.saga"}, partitions = 1, controlledShutdown = true)`, `@DirtiesContext(classMode = AFTER_CLASS)`.

Follow the EXACT pattern from `CustomerSagaConsumerTest.java`:
- `@Container static PostgreSQLContainer postgres` + `@DynamicPropertySource`
- `@MockitoBean OutboxEventRepository` with `doAnswer` capturing saves to a list
- Mock `OutboxEventRepository.findTop10ByStatusOrderByCreatedAtAsc` and `findByStatusAndCreatedAtBefore` to return empty lists (disable relay)
- Mock `OutboxEventRepository.save()` to assign IDs and capture events
- `@Autowired KafkaTemplate<String, String>` to send test messages

Test cases:
1. **Valid vehicle** — save a Vehicle, send EstimationRequested with that vehicleId, verify outbox contains VehicleValidated event with correct vehicleId and plate
2. **Non-existent vehicleId** — send EstimationRequested with random UUID, verify outbox contains VehicleInvalidated event
3. **Null vehicleId** — send EstimationRequested with null vehicleId, verify outbox contains VehicleValidated with null vehicleId
4. **Duplicate event** — send same EstimationRequested twice, verify outbox save called only once (idempotent)
5. **EstimationFailed** — send EstimationFailed, verify no outbox event saved (log only)

### 5. `services/vehicle-service/src/test/java/com/insurancemanagementsystem/vehicle/VehicleServiceApplicationTests.java`

Integration test with `@SpringBootTest(RANDOM_PORT)`, `@AutoConfigureRestTestClient`, `@Testcontainers`. Follow pattern from `EstimationServiceIntegrationTest.java`:
- `@Container static PostgreSQLContainer postgres` + `@Container static ConfluentKafkaContainer kafka`
- `@DynamicPropertySource` for datasource + Kafka config
- `ddl-auto: create-drop` to auto-create schema
- Poll intervals set high (600000ms) to disable scheduled tasks
- `@Autowired RestTestClient`
- `@BeforeEach` cleanup: delete all from repositories

Test cases:
1. **Create vehicle via REST** — POST `/api/vehicles` with valid request → 201, verify vehicle in DB, verify PENDING outbox event for VehicleCreated
2. **Get vehicle by ID** — POST then GET → 200, verify response body
3. **List vehicles** — POST two vehicles, GET → 200, verify page with 2 items
4. **Update vehicle** — POST then PUT → 200, verify updated fields
5. **Delete vehicle** — POST then DELETE → 200, verify vehicle gone from DB
6. **Validation** — POST with missing plate → 400
7. **Reference endpoints** — GET /brands, /engines, /fuel-types, /types, /packages → 200 with seed data

## Key Testing Conventions (from `docs/outlines/11_TESTING_CONVENTIONS.md`)
- Always `RestTestClient` — never `TestRestTemplate` or raw `RestTemplate`
- `@WebMvcTest` from `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` (Spring Boot 4)
- `@MockitoBean` from `org.springframework.test.context.bean.override.mockito.MockitoBean`
- `ObjectMapper` from `tools.jackson.databind.ObjectMapper` (Jackson 3)
- `@EntityScan` from `org.springframework.boot.persistence.autoconfigure.EntityScan`
- Assert HTTP responses with `.jsonPath()` — avoid `objectMapper.readTree()` + `assertThat()` for HTTP assertions
- Use AssertJ `assertThat()` for DB/domain assertions
- `@BeforeEach` cleanup on repositories in integration tests

## Verification

```bash
.\gradlew.bat :services:vehicle-service:test
.\gradlew.bat :services:vehicle-service:jacocoTestReport
```

All tests should pass. JaCoCo report at `services/vehicle-service/build/reports/jacoco/test/html/index.html` should show ≥80% coverage.

## Files Written
- `services/vehicle-service/src/test/resources/application-test.yml` ✅
- `services/vehicle-service/src/test/java/com/insurancemanagementsystem/vehicle/service/VehicleServiceTest.java` ✅
- `services/vehicle-service/src/test/java/com/insurancemanagementsystem/vehicle/controller/VehicleControllerTest.java` ✅
- `services/vehicle-service/src/test/java/com/insurancemanagementsystem/vehicle/saga/VehicleSagaConsumerTest.java` ✅
- `services/vehicle-service/src/test/java/com/insurancemanagementsystem/vehicle/VehicleServiceApplicationTests.java` ✅

## Verification Results
```bash
.\gradlew.bat :services:vehicle-service:test               # ✅ ALL TESTS PASS
.\gradlew.bat :services:vehicle-service:jacocoTestReport    # ✅ 92% instruction coverage (≥80% target met)
```

### Coverage Breakdown
| Package | Instruction Coverage |
|---------|-------------------|
| Overall | 92% |
| vehicle.controller | 100% |
| vehicle.service | 88% |
| vehicle.config | 95% |
| vehicle.dto | 100% |
| vehicle.entity | 100% |
