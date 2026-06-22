# Plan: Sprint 1 — Customer Service

**Task:** `docs/tasks/02_SPRINT1_CUSTOMER_SERVICE.md`
**Story:** `docs/stories/02_CUSTOMER_MANAGEMENT.md`
**Outlines:** `01_SYSTEM_ARCHITECTURE.md` · `02_MICROSERVICES_SPECIFICATIONS.md` · `03_SAGA_PATTERN.md` · `04_MESSAGE_QUEUE_TOPOLOGY.md`

**Baseline:** Phase 0 complete — Docker infra running, reference-skeleton built, common-message library published, customer_db init.sql exists.

---

## 0. Git Branch

- [x] Create branch `sprint1-customer-service` from `main`

---

## 1. Scaffold Customer Service from Reference Skeleton

- [x] 1.1 Uncomment `services:customer-service` in root `settings.gradle.kts` line 10
- [x] 1.2 Create `services/customer-service/build.gradle.kts` — mirror reference-skeleton, add:
  - Dependency on `common:common-message` project
- [x] 1.3 Create `services/customer-service/settings.gradle.kts` with `rootProject.name = "customer-service"`
- [x] 1.4 Create `services/customer-service/Dockerfile` (mirror reference-skeleton, expose port 8081)
- [x] 1.5 Create package directories:
  - `src/main/java/com/insurancemanagementsystem/customer/`
  - Sub-packages: `entity/`, `repository/`, `service/`, `controller/`, `dto/`, `config/`, `exception/`
  - `src/main/resources/`
  - `src/test/java/com/insurancemanagementsystem/customer/`
- [x] 1.6 Create `CustomerServiceApplication.java` — `@SpringBootApplication` main class
- [x] 1.7 Create `application.yml` — PostgreSQL datasource (`customer_db` on `localhost:5432`), Kafka/RabbitMQ config, server port `8081`, JPA `ddl-auto=validate`, structured JSON logging
- [x] 1.8 Copy `ApiResponse.java` → `dto/ApiResponse.java` (update package)
- [x] 1.9 Copy `GlobalExceptionHandler.java` → `exception/GlobalExceptionHandler.java` (update package)

---

## 2. Customer Domain Layer

- [x] 2.1 Create `entity/Customer.java` — JPA `@Entity`, table `customers`:
  - `id` (UUID, `@GeneratedValue(UUID)`)
  - `firstName`, `lastName`, `nationalId` (unique, max 11), `email`, `phone`, `birthDate` (LocalDate)
  - `address` (TEXT), `cityId` (Integer), `professionId` (Integer)
  - `createdAt`, `updatedAt` (LocalDateTime, auto-managed via `@PrePersist`/`@PreUpdate`)
  - `deletedAt` (LocalDateTime, nullable — soft-delete marker)
  - Lombok `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`
- [x] 2.2 Create `repository/CustomerRepository.java` — `JpaRepository<Customer, UUID>`:
  - `findByDeletedAtIsNull(Pageable)` — active customers, paginated
  - `findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(String fn, String ln, Pageable)`
  - `findByNationalIdContaining(String nationalId, Pageable)`
  - `findByNationalId(String nationalId)` — exact lookup for uniqueness validation
- [x] 2.3 Create `service/CustomerService.java`:
  - `findAll(Pageable)` — returns active (non-deleted) page
  - `search(String name, String nationalId, Pageable)` — combined search
  - `findById(UUID)` — throws `EntityNotFoundException` if not found or soft-deleted
  - `create(CustomerRequest)` — validates uniqueness of nationalId, builds entity, saves
  - `update(UUID, CustomerRequest)` — finds existing, updates fields, saves
  - `softDelete(UUID)` — sets `deletedAt = now()` instead of hard delete
  - Business rules from story: national ID format validation (11 chars), email format, phone format, active estimation check before delete (stub — will be implemented when Estimation Service exists)
  - Injection: `CustomerRepository` (MessagePublisher will be wired in Section 5)

---

## 3. CRUD API Layer

- [x] 3.1 Create `dto/CustomerRequest.java` — create/update DTO with Jakarta validation:
  - `@NotBlank` firstName, lastName, nationalId
  - `@Size(max=11)` nationalId
  - `@Email` email, `@Pattern` phone, `@NotNull` birthDate
  - `cityId`, `professionId` (nullable — reference data not yet available)
  - `address` (optional)
- [x] 3.2 Create `dto/CustomerResponse.java` — read DTO (all fields, no `deletedAt`)
  - Static factory `fromEntity(Customer)` for clean controller mapping
- [x] 3.3 Create `controller/CustomerController.java` — `@RestController`, `@RequestMapping("/api/customers")`:
  - `GET /` — paginated list with optional `search` query param (searches name + nationalId)
  - `GET /{id}` — single customer detail
  - `POST /` — create, returns 201 with created customer
  - `PUT /{id}` — update, returns 200 with updated customer
  - `DELETE /{id}` — soft-delete, returns 200 with confirmation
  - All endpoints wrap response in `ApiResponse<T>`
  - `@Valid` on request bodies, `Pageable` default sort by `lastName asc`

---

## 4. Database Verification

- [x] 4.1 Run `docker compose -f infra/docker/docker-compose.yml up -d customer-db`
- [x] 4.2 Verify `customer_db` container is healthy
- [x] 4.3 Verify `customers` table exists with correct schema (UUID PK, indexes on `national_id`, `last_name`, `email`, `deleted_at` column)
- [x] 4.4 No seed data required for customer service (customers are created by users)

---

## 5. Messaging Infrastructure

- [x] 5.1 Create `config/MessagePublisher.java` — `StreamBridge` wrapper (copy from skeleton, update package)
- [x] 5.2 Create `config/CustomerEventPublisher.java`:
  - `publishCustomerCreated(Customer)` — builds `CustomerCreatedEvent`, publishes to `customer.events`
  - `publishCustomerUpdated(Customer)` — builds `CustomerUpdatedEvent`, publishes to `customer.events`
  - Uses `MessagePublisher` and `EventEnvelope`/`BaseEvent.toEnvelope()`
- [x] 5.3 Wire `CustomerEventPublisher` into `CustomerService` — publish on create and update
- [x] 5.4 Add Spring Cloud Stream function bindings in `application.yml`:
  - `spring.cloud.stream.bindings.customerSagaConsumer-in-0.destination: estimation.saga`
  - `spring.cloud.stream.bindings.customerSagaConsumer-in-0.group: customer-service-group`
  - `spring.cloud.stream.bindings.customerEvents-out-0.destination: customer.events`
  - Added `spring.cloud.stream.dynamicDestinations: estimation.saga,customer.events` for StreamBridge dynamic routing

---

## 6. SAGA Consumer

- [x] 6.1 Create `config/CustomerSagaConsumer.java` — `@Configuration` with `@Bean` `Consumer<String>`:
  - Bean name: `customerSagaConsumer`
  - Deserializes `EventEnvelope` from JSON string
  - If `eventType == "EstimationRequested"`:
    - Extract `customerId` from `EstimationRequestedEvent` payload
    - Check idempotency: skip if `(sagaId, eventType)` already processed (in-memory `ConcurrentHashMap` with TTL)
    - Find customer by ID, check `deletedAt == null` (active)
    - Publish `CustomerValidatedEvent` or `CustomerInvalidatedEvent` to `estimation.saga`
  - If `eventType == "EstimationFailed"`:
    - Log only (no reversible action for read-only validation per outline)
  - Log structured with `traceId` + `sagaId` from MDC/event envelope
- [x] 6.2 Create in-memory dedup store — `ConcurrentHashMap<String, Instant>` with periodic cleanup via `ScheduledExecutorService` (or simple LRU)

---

## 7. Unit Tests (Service Layer)

- [x] 7.1 Create `src/test/java/com/insurancemanagementsystem/customer/service/CustomerServiceTest.java`:
  - Mock `CustomerRepository`, verify `CustomerService` logic
  - Test: create customer with valid data → returns saved entity
  - Test: create customer with duplicate nationalId → throws `IllegalArgumentException`
  - Test: findById not found → throws `EntityNotFoundException`
  - Test: findById soft-deleted → throws `EntityNotFoundException`
  - Test: softDelete → sets `deletedAt`
  - Test: update customer → fields updated, `updatedAt` refreshed
  - Test: search by name → returns matching results
  - Test: search by nationalId → returns matching results
  - Target: ≥80% service layer coverage

---

## 8. Unit Tests (Controller Layer)

- [x] 8.1 Create `src/test/java/com/insurancemanagementsystem/customer/controller/CustomerControllerTest.java`:
  - `@WebMvcTest(CustomerController.class)`, mock `CustomerService`
  - Test: `GET /api/customers` → 200 with paginated response
  - Test: `GET /api/customers?search=Doe` → 200 with filtered results
  - Test: `GET /api/customers/{id}` → 200 with customer data
  - Test: `GET /api/customers/{id}` not found → 404
  - Test: `POST /api/customers` with valid body → 201
  - Test: `POST /api/customers` with invalid body → 400 with validation errors
  - Test: `PUT /api/customers/{id}` → 200
  - Test: `DELETE /api/customers/{id}` → 200

---

## 9. Integration Tests

- [x] 9.1 Create `src/test/java/com/insurancemanagementsystem/customer/CustomerServiceApplicationTests.java`:
  - `@SpringBootTest(webEnvironment = RANDOM_PORT)`
  - `@Testcontainers` — PostgreSQL 16 + Kafka containers
  - `@DynamicPropertySource` for datasource, Kafka bootstrap, JPA ddl-auto=create-drop
  - Test: `contextLoads` — Spring context starts
  - Test: `createCustomerViaRest_verifyInDb` — POST → 201, verify entity in DB
  - Test: `searchCustomers` — create 2 customers, GET with search → both match
  - Test: `softDeleteCustomer` — DELETE → 200, GET by ID → 404
  - Test: `updateCustomer` — PUT → 200, verify updated fields
- [x] 9.2 Create `src/test/java/com/insurancemanagementsystem/customer/saga/CustomerSagaConsumerTest.java`:
  - `@SpringBootTest` with Kafka test support
  - Test: consume `EstimationRequested` with valid customerId → `CustomerValidated` published
  - Test: consume `EstimationRequested` with invalid customerId → `CustomerInvalidated` published
  - Test: consume `EstimationRequested` with soft-deleted customer → `CustomerInvalidated` published
  - Test: duplicate `EstimationRequested` (same sagaId) → idempotent, no duplicate publish

---

## 10. Build & Verification

- [x] 10.1 Run `./gradlew :services:customer-service:build` — compile, test, package all pass
- [x] 10.2 Run `./gradlew :services:customer-service:test` — all tests green (27/27)
- [x] 10.3 Run `./gradlew :services:customer-service:bootRun` — starts on port 8081, connects to customer-db
- [x] 10.4 Manual smoke test (curl or RestTemplate):
  - `POST /api/customers` with valid JSON → 201 ✓
  - `GET /api/customers` → 200 with created customer ✓
  - `GET /api/customers/{id}` → 200 ✓
  - `PUT /api/customers/{id}` → 200 ✓
  - `DELETE /api/customers/{id}` → 200 ✓
  - `GET /api/customers/{id}` after delete → 404 ✓
- [x] 10.5 Commit: `fix(customer-service): correct DB connection config for Docker Compose infra` (CRUD, domain events, SAGA consumer code already committed incrementally in prior commits)

---

## Dependencies & Notes

- **Depends on:** `common:common-message` (built in Phase 0), `customer-db` Docker container (exists)
- **No dependencies on other microservices** — Customer Service is self-contained for SAGA consumers (idempotency is local)
- **Reference data (cityId, professionId)** — stored as plain integers for now; will integrate with Reference Data Service RPC in a later phase
- **Active estimation check before delete** — stub for now (will query Estimation Service via event when available)
- **Dedup store** — in-memory `ConcurrentHashMap` with TTL-based cleanup; can be replaced with Redis/DB later
- **Controller tests** required adding `testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")` in `build.gradle.kts` for `@WebMvcTest` support (Spring Boot 4.0.6 uses `.webmvc.test.autoconfigure` package instead of the classic `.test.autoconfigure.web.servlet`)
