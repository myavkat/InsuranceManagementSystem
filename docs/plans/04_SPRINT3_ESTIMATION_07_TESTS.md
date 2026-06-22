# Plan: Sprint 3 — Estimation Service — Step 7: Tests

## Objective
Create comprehensive unit and integration tests covering:
- CRUD API endpoints (happy path, validation errors, not found)
- SAGA event consumer (all event types, state transitions, duplicates)
- Timeout compensation scheduler
- Messaging infrastructure (event publishing, deduplication)

Target: ≥80% code coverage (verified by JaCoCo).

## Context Files to Read First

1. **`docs/outlines/11_TESTING_CONVENTIONS.md`** — Testing conventions (RestTestClient, @WebMvcTest, @SpringBootTest, @AutoConfigureRestTestClient, jsonPath assertions, AssertJ)
2. **`services/insurance-service/src/test/java/.../`** — Explore test patterns used by existing services (slice + integration tests)
3. **`services/customer-service/src/test/java/.../`** — Alternative test patterns
4. **`services/insurance-service/build.gradle.kts`** — JaCoCo verification rules (≥80%)
5. **`docs/outlines/12_DEVELOPER_COMMANDS.md`** — Test commands: `.\gradlew.bat :services:estimation-service:test`

## Test Files to Create

### 1. `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/controller/EstimationControllerTest.java`

Slice test (`@WebMvcTest`) for REST controller with mocked service layer.

**Test scenarios:**
- `POST /api/estimations` with valid request → 201 CREATED + correct response
- `POST /api/estimations` with missing customerId → 400 BAD_REQUEST
- `POST /api/estimations` with both vehicleId and realEstateId null → 400 BAD_REQUEST
- `GET /api/estimations/{id}` with existing id → 200 OK + correct data
- `GET /api/estimations/{id}` with non-existing id → 404 NOT_FOUND
- `GET /api/estimations` with no filters → 200 OK + paginated list
- `GET /api/estimations` with customerId filter → 200 OK + filtered list
- `GET /api/estimations` with status filter → 200 OK + filtered list

**Pattern:**
```java
@WebMvcTest(EstimationController.class)
class EstimationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private RestTestClient client;

    @MockitoBean
    private EstimationService estimationService;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindTo(mockMvc).build();
    }

    // Tests use client.get().uri(...).exchange().expectStatus().isOk().expectBody().jsonPath(...)
}
```

### 2. `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/service/EstimationServiceTest.java`

Unit test for `EstimationService` using mocked repository.

**Test scenarios:**
- `create()` creates estimation with STARTED status + generates sagaId
- `create()` throws IllegalArgumentException when both vehicleId and realEstateId are null
- `findById()` returns estimation response for existing id
- `findById()` throws EntityNotFoundException for non-existing id
- `findAll()` with no filters returns all estimations
- `findAll()` with customerId filter returns filtered results
- `findAll()` with status filter returns filtered results

**Pattern:**
```java
@ExtendWith(MockitoExtension.class)
class EstimationServiceTest {

    @Mock
    private EstimationRepository estimationRepository;

    @Mock
    private MessagePublisher messagePublisher;

    @InjectMocks
    private EstimationService estimationService;

    // ... tests using Mockito verify/when + AssertJ assertThat
}
```

### 3. `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumerTest.java`

Unit test for the SAGA consumer — test each event type handler.

**Test scenarios:**
- `CustomerValidated` event → logs progress, skips on duplicate
- `VehicleValidated` event → logs progress, skips on duplicate
- `PremiumCalculated` event → transitions estimation to COMPLETED with premium
- `PremiumCalculated` event for non-existing sagaId → logs warning, no crash
- `CustomerInvalidated` event → transitions to REJECTED + publishes EstimationFailed
- `VehicleInvalidated` event → transitions to REJECTED + publishes EstimationFailed
- `CalculationFailed` event → transitions to REJECTED + publishes EstimationFailed
- Duplicate event for any type → skipped (idempotency)
- Unknown event type → logged as warning

**Pattern:**
```java
@ExtendWith(MockitoExtension.class)
class EstimationSagaConsumerTest {

    @Mock
    private EstimationRepository estimationRepository;

    @Mock
    private DeduplicationStore deduplicationStore;

    @Mock
    private EstimationEventPublisher estimationEventPublisher;

    @InjectMocks
    private EstimationSagaConsumer consumer;

    // Use a real JsonMapper for deserialization
    private final JsonMapper jsonMapper = new JsonMapper();

    // Helper: build a JSON string from a BaseEvent + sagaId
    private String buildEventJson(BaseEvent event, UUID sagaId) {
        EventEnvelope envelope = event.toEnvelope(sagaId, UUID.randomUUID());
        try { return jsonMapper.writeValueAsString(envelope); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void testCustomerValidated() {
        UUID sagaId = UUID.randomUUID();
        when(deduplicationStore.isDuplicate(anyString(), anyString())).thenReturn(false);

        CustomerValidatedEvent event = CustomerValidatedEvent.builder()
                .customerId(UUID.randomUUID())
                .firstName("John")
                .lastName("Doe")
                .build();

        consumer.processEstimationSaga(jsonMapper)
                .accept(buildEventJson(event, sagaId));

        verify(deduplicationStore).markProcessed(sagaId.toString(), EventConstants.CUSTOMER_VALIDATED);
    }

    // ... more tests
}
```

### 4. `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/config/DeduplicationStoreTest.java`

Unit test for the DeduplicationStore.

**Test scenarios:**
- `isDuplicate()` returns false for new key
- `markProcessed()` + `isDuplicate()` returns true
- Different event types with same sagaId are not duplicates
- Different sagaId with same event type are not duplicates
- Cleanup removes expired entries

### 5. `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/service/SagaTimeoutServiceTest.java`

Unit test for `SagaTimeoutService`.

**Test scenarios:**
- No stale estimations → no changes, no events published
- Found stale estimations → each transitioned to REJECTED + EstimationFailed published
- Exception during processing one estimation → other estimations still processed
- Correct cutoff time calculation (based on timeoutMinutes config)

**Pattern:**
```java
@ExtendWith(MockitoExtension.class)
class SagaTimeoutServiceTest {

    @Mock
    private EstimationRepository estimationRepository;

    @Mock
    private EstimationEventPublisher estimationEventPublisher;

    @InjectMocks
    private SagaTimeoutService timeoutService;

    @Test
    void testNoStaleEstimations() {
        when(estimationRepository.findByStatusAndCreatedAtBefore(any(), any()))
                .thenReturn(List.of());

        timeoutService.checkForTimedOutSagas();

        verify(estimationEventPublisher, never()).publishEstimationFailed(any(), any(), any(), any());
    }

    @Test
    void testStaleEstimationsAreRejected() {
        UUID sagaId = UUID.randomUUID();
        Estimation stale = Estimation.builder()
                .id(UUID.randomUUID())
                .sagaId(sagaId)
                .status(Estimation.Status.STARTED)
                .build();

        when(estimationRepository.findByStatusAndCreatedAtBefore(any(), any()))
                .thenReturn(List.of(stale));

        timeoutService.checkForTimedOutSagas();

        assertThat(stale.getStatus()).isEqualTo(Estimation.Status.REJECTED);
        assertThat(stale.getDetails()).contains("timed out");
        verify(estimationRepository).save(stale);
        verify(estimationEventPublisher).publishEstimationFailed(eq(sagaId), any(), any(), any());
    }
}
```

### 6. `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/EstimationServiceApplicationTests.java`

Full integration test (`@SpringBootTest`) for the whole application context. This uses `@AutoConfigureRestTestClient`.

**Note:** For full integration tests with Testcontainers (PostgreSQL + Kafka), the test configuration must set up containers. However, since the infrastructure requires real containers, provide a simpler smoke test that just verifies the context loads, and document the full integration test pattern.

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class EstimationServiceApplicationTests {

    @Autowired
    private RestTestClient client;

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        assertThat(context).isNotNull();
    }

    @Test
    void healthEndpointReturnsOk() {
        // Basic smoke test that the app can start
        // This won't hit the DB — just verifies the web context loads
    }
}
```

For full integration tests (requiring PostgreSQL + Kafka via Testcontainers), create a configuration helper:

```java
// Documented pattern — not full implementation here
// Use @SpringBootTest + @TestContainers for:
// - Create estimation via POST → assert 201 + correct status
// - Get estimation by ID → assert correct data
// - List estimations with filters → assert correct pagination
// - Create estimation and simulate PremiumCalculated event → assert COMPLETED
// - Create estimation and simulate CustomerInvalidated event → assert REJECTED
// - Duplicate event consumption → assert ignored
```

## Running Tests

```bash
# Run all tests
.\gradlew.bat :services:estimation-service:test

# Run with coverage report
.\gradlew.bat :services:estimation-service:jacocoTestReport

# Check coverage meets ≥80% threshold
.\gradlew.bat :services:estimation-service:jacocoCoverageVerification
```

## Coverage Goal

| Layer | Target Coverage |
|-------|-----------------|
| Controller | 90%+ |
| Service | 85%+ |
| Config (SAGA consumer, DeduplicationStore, MessagePublisher) | 80%+ |
| Entity/DTO | 60%+ (getters/setters) |

## File Summary
- `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/controller/EstimationControllerTest.java` ✅
- `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/service/EstimationServiceTest.java` ✅
- `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumerTest.java` ✅
- `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/config/DeduplicationStoreTest.java` ✅
- `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/service/SagaTimeoutServiceTest.java` ✅
- `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/EstimationServiceApplicationTests.java` ✅
