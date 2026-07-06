# Plan: Sprint 4 — Vehicle & RealEstate — Step 9: RealEstate Service Tests

## Objective
Create all test layers for the RealEstate Service: unit tests for service, slice tests for controller, integration tests with Testcontainers, SAGA consumer tests with EmbeddedKafka. Achieve ≥80% JaCoCo coverage.

## Context Files to Read First
1. **`services/customer-service/src/test/java/com/insurancemanagementsystem/customer/service/CustomerServiceTest.java`** — Unit test pattern
2. **`services/customer-service/src/test/java/com/insurancemanagementsystem/customer/controller/CustomerControllerTest.java`** — Controller slice test pattern
3. **`services/customer-service/src/test/java/com/insurancemanagementsystem/customer/saga/CustomerSagaConsumerTest.java`** — SAGA consumer test pattern (CRITICAL)
4. **`services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/EstimationServiceIntegrationTest.java`** — Integration test pattern
5. **`services/estimation-service/src/test/resources/application-test.yml`** — Test configuration
6. **`docs/outlines/11_TESTING_CONVENTIONS.md`** — Testing conventions
7. All RealEstate Service source files from Steps 6-8

## Files to Create

### 1. `services/realestate-service/src/test/resources/application-test.yml`

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: create-drop
realestate:
  outbox:
    poll-interval-ms: 600000  # disable scheduled tasks during tests
```

### 2. `services/realestate-service/src/test/java/com/insurancemanagementsystem/realestate/service/RealEstateServiceTest.java`

Unit tests with `@ExtendWith(MockitoExtension.class)`. Mock all repositories. Test cases:
- `findAll()` — returns paginated results with reference data names
- `findById()` — existing returns response, non-existing throws EntityNotFoundException
- `create()` — valid request creates + returns response; missing address throws validation error; future construction year throws IllegalArgumentException; invalid FK reference throws IllegalArgumentException
- `update()` — valid request updates; non-existing ID throws EntityNotFoundException
- `delete()` — existing deletes; non-existing throws EntityNotFoundException
- `getConstructionTypes()`, `getLuxuryClasses()`, `getUsageTypes()` — return lists

### 3. `services/realestate-service/src/test/java/com/insurancemanagementsystem/realestate/controller/RealEstateControllerTest.java`

Slice tests with `@WebMvcTest(controllers = RealEstateController.class)`, `@Import(GlobalExceptionHandler.class)`, `@MockitoBean` for RealEstateService. Use `RestTestClient.bindTo(mockMvc).build()` in `@BeforeEach`.

Test all 8 endpoints:
- `GET /api/real-estate` — 200 with paginated list
- `GET /api/real-estate/{id}` — existing returns 200, non-existing returns 404
- `POST /api/real-estate` — valid returns 201 with body; missing address returns 400; invalid squareMeters (0 or negative) returns 400
- `PUT /api/real-estate/{id}` — valid returns 200
- `DELETE /api/real-estate/{id}` — returns 200
- `GET /api/real-estate/construction-types` — 200 with list
- `GET /api/real-estate/luxury-classes` — 200 with list
- `GET /api/real-estate/usage-types` — 200 with list

IMPORTANT: Use Spring Boot 4 test imports:
- `@WebMvcTest` from `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`
- `@MockitoBean` from `org.springframework.test.context.bean.override.mockito.MockitoBean`
- `RestTestClient` from `org.springframework.test.web.servlet.client.RestTestClient`

### 4. `services/realestate-service/src/test/java/com/insurancemanagementsystem/realestate/saga/RealEstateSagaConsumerTest.java`

Follow EXACT pattern from `CustomerSagaConsumerTest.java` and `VehicleSagaConsumerTest.java` (Step 5):

- `@SpringBootTest(RANDOM_PORT)`, `@Testcontainers`, `@EmbeddedKafka`, `@DirtiesContext`
- `@Container static PostgreSQLContainer postgres` + `@DynamicPropertySource`
- `@MockitoBean OutboxEventRepository` — capture saves, mock relay queries
- `@Autowired KafkaTemplate<String, String>` — send test messages

Test cases:
1. **Valid real estate** — save RealEstate, send EstimationRequested with that realEstateId, verify outbox contains RealEstateValidated event
2. **Non-existent realEstateId** — send EstimationRequested with random UUID, verify outbox contains RealEstateInvalidated with reason "Real estate not found"
3. **Null realEstateId** — send EstimationRequested without realEstateId, verify outbox contains RealEstateValidated with null realEstateId
4. **Duplicate event** — send same EstimationRequested twice, verify outbox save called once (idempotent)
5. **EstimationFailed** — send EstimationFailed, verify no outbox event saved

### 5. `services/realestate-service/src/test/java/com/insurancemanagementsystem/realestate/RealEstateServiceApplicationTests.java`

Integration test with `@SpringBootTest(RANDOM_PORT)`, `@AutoConfigureRestTestClient`, `@Testcontainers`:
- `@Container` PostgreSQL + Kafka, `@DynamicPropertySource`, `ddl-auto: create-drop`
- Poll intervals set high to disable scheduled tasks
- `@BeforeEach` cleanup on repositories

Test cases:
1. **Create via REST** — POST `/api/real-estate` → 201, verify DB + outbox event
2. **Get by ID** — POST then GET → 200
3. **List** — POST two, GET → 200 with 2 items
4. **Update** — POST then PUT → 200
5. **Delete** — POST then DELETE → 200, verify deleted
6. **Validation** — POST without address → 400; POST with squareMeters=0 → 400
7. **Reference endpoints** — GET construction-types, luxury-classes, usage-types → 200 with seed data

## Key Testing Conventions
- Always `RestTestClient` — never `TestRestTemplate`
- `@WebMvcTest` from `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`
- `@MockitoBean` from `org.springframework.test.context.bean.override.mockito.MockitoBean`
- `ObjectMapper` from `tools.jackson.databind.ObjectMapper` (Jackson 3)
- Assert HTTP responses with `.jsonPath()`; AssertJ `assertThat()` for DB assertions
- `@BeforeEach` cleanup in integration tests

## Verification

```bash
.\gradlew.bat :services:realestate-service:test
.\gradlew.bat :services:realestate-service:jacocoTestReport
```

All tests pass. JaCoCo 95% instruction coverage (≥80% target met). ✅
Branch coverage: 68% (missed branches in saga consumer null/not-null checks and service toResponse reference lookups).

## Files Written
- `services/realestate-service/src/test/resources/application-test.yml` ✅
- `services/realestate-service/src/test/java/com/insurancemanagementsystem/realestate/service/RealEstateServiceTest.java` ✅
- `services/realestate-service/src/test/java/com/insurancemanagementsystem/realestate/controller/RealEstateControllerTest.java` ✅
- `services/realestate-service/src/test/java/com/insurancemanagementsystem/realestate/saga/RealEstateSagaConsumerTest.java` ✅
- `services/realestate-service/src/test/java/com/insurancemanagementsystem/realestate/RealEstateServiceApplicationTests.java` ✅
