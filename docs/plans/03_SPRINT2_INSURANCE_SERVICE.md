# Plan: Sprint 2 — Insurance Service

**Task:** `docs/tasks/03_SPRINT2_INSURANCE_SERVICE.md`
**Story:** `docs/stories/03_INSURANCE_PRODUCTS.md`
**Outlines:** `01_SYSTEM_ARCHITECTURE.md` · `02_MICROSERVICES_SPECIFICATIONS.md` · `03_SAGA_PATTERN.md` · `04_MESSAGE_QUEUE_TOPOLOGY.md`

**Baseline:** Sprint 1 (Customer Service) complete. Insurance DB init.sql exists. `common:common-message` has `InsuranceCreatedEvent`, `InsuranceUpdatedEvent`, `PremiumCalculatedEvent`, `CalculationFailedEvent`. `common:common-web` has `ApiResponse` + `GlobalExceptionHandler`.

**Port allocations:** insurance-service → 8084, insurance-db → 5436

---

## 0. Git Branch

- [ ] 0.1 Create branch `sprint2-insurance-service` from `main`

---

## 1. Scaffold, Domain, & DB — see `03_SPRINT2_INSURANCE_01_SCAFFOLD_DOMAIN.md`

- [ ] 1.1 Uncomment `services:insurance-service` in root `settings.gradle.kts` line 13
- [ ] 1.2 Create `services/insurance-service/build.gradle.kts`
- [ ] 1.3 Create `services/insurance-service/settings.gradle.kts`
- [ ] 1.4 Create `services/insurance-service/Dockerfile`
- [ ] 1.5 Create package directories and `InsuranceServiceApplication.java`
- [ ] 1.6 Create `application.yml` — port 8084, DB `insurance_db` on localhost:5436
- [ ] 1.7 Create entity classes: `Insurance`, `InsuranceType`, `InsuranceCompany`
- [ ] 1.8 Create JPA repositories for all three entities
- [ ] 1.9 Start insurance-db container, verify tables and seed data

---

## 2. CRUD API — see `03_SPRINT2_INSURANCE_02_CRUD_API.md`

- [ ] 2.1 Create DTOs: `InsuranceRequest`, `InsuranceResponse`, `InsuranceCompanyRequest`, `InsuranceCompanyResponse`
- [ ] 2.2 Create `InsuranceService` — business logic for all CRUD operations
- [ ] 2.3 Create `InsuranceController` — all REST endpoints
- [ ] 2.4 Verify all endpoints via manual smoke test

---

## 3. Messaging + SAGA — see `03_SPRINT2_INSURANCE_03_MESSAGING_SAGA.md`

- [ ] 3.1 Create `MessagePublisher.java`
- [ ] 3.2 Create `InsuranceEventPublisher.java` — domain events to `insurance.events`
- [ ] 3.3 Wire event publishing into `InsuranceService`
- [ ] 3.4 Create `DeduplicationStore.java`
- [ ] 3.5 Create `SagaAggregationStore.java` — correlation state for SAGA
- [ ] 3.6 Create `InsuranceSagaConsumer.java` — aggregate consumer
- [ ] 3.7 Configure Spring Cloud Stream bindings in `application.yml`

---

## 4. Tests — see `03_SPRINT2_INSURANCE_04_TESTS.md`

- [ ] 4.1 Service layer unit tests (`InsuranceServiceTest`)
- [ ] 4.2 Controller slice tests (`InsuranceControllerTest`)
- [ ] 4.3 SAGA consumer tests (`InsuranceSagaConsumerTest`)
- [ ] 4.4 Integration tests (`InsuranceServiceApplicationTests`)
- [ ] 4.5 Run full test suite — verify ≥80% coverage

---

## 5. Build & Final Verification

- [ ] 5.1 Run `.\gradlew.bat :services:insurance-service:build`
- [ ] 5.2 Run `.\gradlew.bat :services:insurance-service:test` — all green
- [ ] 5.3 Start service: `.\gradlew.bat :services:insurance-service:bootRun`
- [ ] 5.4 Commit topic-by-topic (scaffold, CRUD, messaging, tests)
