# Plan: Sprint 5 — REST API with Caching & Domain Events

## Context Files (Read Before Starting)

| File | Purpose |
|------|---------|
| `docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md` | §7 Reference Data Service endpoints |
| `docs/outlines/04_MESSAGE_QUEUE_TOPOLOGY.md` | `reference-data.events` topic config |
| `docs/stories/07_REFERENCE_DATA.md` | User stories: list cities, list professions |
| `docs/outlines/11_TESTING_CONVENTIONS.md` | RestTestClient, @WebMvcTest, AssertJ, jsonPath |
| `AGENTS.md` | SAGA Consumer, Outbox, Cross-Service, JSON rules |
| `services/customer-service/src/main/java/.../config/CustomerEventPublisher.java` | Domain event publisher pattern |
| `services/customer-service/src/main/java/.../controller/CustomerController.java` | REST controller pattern |
| `services/reference-skeleton/src/main/java/.../exception/GlobalExceptionHandler.java` | Exception handler pattern |
| `common/common-message/src/main/java/.../event/domain/ReferenceDataChangedEvent.java` | Existing event class — REUSE THIS |
| `common/common-message/src/main/java/.../event/EventConstants.java` | `REFERENCE_DATA_EVENTS` = `"reference-data.events"` |
| `common/common-message/src/main/java/.../event/EventEnvelope.java` | Event envelope structure |
| `common/common-message/src/main/java/.../messaging/MessagePublisher.java` | Message publishing via StreamBridge |

## Prerequisites

- [x] Plan 01 (Service Scaffold) completed — project compiles, dependencies resolve
- [x] Plan 02 (Domain Entities) completed — entities, repositories, DTOs compile

## Conventions to Apply

- **Spring Boot MVC:** `@RestController`, not `@Controller`
- **API envelope:** All responses wrapped in `ApiResponse<T>`
- **JSON via ObjectMapper only:** Never build JSON strings via concatenation (AGENTS.md §Outbox & Messaging Rules)
- **Check send results:** Always check `StreamBridge.send()` boolean return (AGENTS.md)
- **Cross-service code sharing:** `ReferenceDataChangedEvent` already exists in `common-message` — REUSE it, don't create a duplicate
- **Propagate trace context:** Domain events use `UUID.randomUUID()` for traceId since there's no incoming saga envelope (this is the starting point)
- **Caching:** Use in-memory `ConcurrentHashMap` with scheduled TTL eviction (AGENTS.md §Event table TTL + Scheduled task rules apply)

## Implementation Steps

### Step 1: Create `ReferenceDataService`

- [x] Create `services/reference-data-service/src/main/java/com/insurancemanagementsystem/referencedata/service/ReferenceDataService.java`

Package: `com.insurancemanagementsystem.referencedata.service`

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ReferenceDataService {

    private final CityRepository cityRepository;
    private final ProfessionRepository professionRepository;

    // In-memory cache with TTL
    private volatile List<CityResponse> cachedCities;
    private volatile List<ProfessionResponse> cachedProfessions;
    private volatile Instant citiesCacheExpiry = Instant.MIN;
    private volatile Instant professionsCacheExpiry = Instant.MIN;

    private static final long CACHE_TTL_SECONDS = 300; // 5 minutes

    @Transactional(readOnly = true)
    public List<CityResponse> getCities() {
        if (cachedCities == null || Instant.now().isAfter(citiesCacheExpiry)) {
            synchronized (this) {
                if (cachedCities == null || Instant.now().isAfter(citiesCacheExpiry)) {
                    cachedCities = cityRepository.findAllByOrderByNameAsc().stream()
                            .map(this::toCityResponse)
                            .toList();
                    citiesCacheExpiry = Instant.now().plusSeconds(CACHE_TTL_SECONDS);
                    log.debug("Cities cache refreshed: {} entries", cachedCities.size());
                }
            }
        }
        return cachedCities;
    }

    @Transactional(readOnly = true)
    public List<ProfessionResponse> getProfessions() {
        if (cachedProfessions == null || Instant.now().isAfter(professionsCacheExpiry)) {
            synchronized (this) {
                if (cachedProfessions == null || Instant.now().isAfter(professionsCacheExpiry)) {
                    cachedProfessions = professionRepository.findAllByOrderByNameAsc().stream()
                            .map(this::toProfessionResponse)
                            .toList();
                    professionsCacheExpiry = Instant.now().plusSeconds(CACHE_TTL_SECONDS);
                    log.debug("Professions cache refreshed: {} entries", cachedProfessions.size());
                }
            }
        }
        return cachedProfessions;
    }

    /**
     * Invalidate caches. Called after data changes (admin endpoints or future mutation operations).
     */
    public void invalidateCache() {
        synchronized (this) {
            cachedCities = null;
            cachedProfessions = null;
            citiesCacheExpiry = Instant.MIN;
            professionsCacheExpiry = Instant.MIN;
            log.info("Reference data caches invalidated");
        }
    }

    private CityResponse toCityResponse(City city) {
        return CityResponse.builder()
                .id(city.getId())
                .name(city.getName())
                .plateCode(city.getPlateCode())
                .build();
    }

    private ProfessionResponse toProfessionResponse(Profession profession) {
        return ProfessionResponse.builder()
                .id(profession.getId())
                .name(profession.getName())
                .build();
    }
}
```

**Key decisions:**
- Double-checked locking pattern — thread-safe lazy cache population
- `volatile` fields — visibility across threads without full synchronization on reads
- 5-minute TTL — configurable via `CACHE_TTL_SECONDS`, can be externalized to `application.yml` later
- `synchronized` block for cache refresh — prevents thundering herd
- Cache invalidation via `invalidateCache()` — called when reference data changes (e.g., admin endpoints or DB migrations)

### Step 2: Create `ReferenceDataEventPublisher`

- [x] Create `services/reference-data-service/src/main/java/com/insurancemanagementsystem/referencedata/config/ReferenceDataEventPublisher.java`

Package: `com.insurancemanagementsystem.referencedata.config`

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class ReferenceDataEventPublisher {

    private final MessagePublisher messagePublisher;

    /**
     * Publish a ReferenceDataChangedEvent to reference-data.events topic.
     * Called after any reference data mutation (admin endpoints, DB migrations, etc.).
     */
    public void publishReferenceDataChanged(String entityType, String changeType) {
        ReferenceDataChangedEvent event = ReferenceDataChangedEvent.builder()
                .entityType(entityType)
                .changeType(changeType)
                .build();

        EventEnvelope envelope = event.toEnvelope(null, UUID.randomUUID());
        messagePublisher.publish(EventConstants.REFERENCE_DATA_EVENTS, envelope);
        log.info("Published ReferenceDataChanged event: entityType={}, changeType={}", entityType, changeType);
    }
}
```

**Key decisions:**
- Reuses `ReferenceDataChangedEvent` from `common-message` — DO NOT create a duplicate event class
- `sagaId` is `null` — this is a domain event, not a SAGA event
- `traceId` generated fresh — no incoming saga context to propagate
- `MessagePublisher.publish()` checks return value (throws `IllegalStateException` on failure per AGENTS.md rules)

### Step 3: Create `ReferenceDataController`

- [x] Create `services/reference-data-service/src/main/java/com/insurancemanagementsystem/referencedata/controller/ReferenceDataController.java`

Package: `com.insurancemanagementsystem.referencedata.controller`

```java
@RestController
@RequestMapping("/api/reference-data")
@RequiredArgsConstructor
@Slf4j
public class ReferenceDataController {

    private final ReferenceDataService service;

    @GetMapping("/cities")
    public ResponseEntity<ApiResponse<List<CityResponse>>> getCities() {
        List<CityResponse> cities = service.getCities();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
                .body(ApiResponse.success(cities));
    }

    @GetMapping("/professions")
    public ResponseEntity<ApiResponse<List<ProfessionResponse>>> getProfessions() {
        List<ProfessionResponse> professions = service.getProfessions();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
                .body(ApiResponse.success(professions));
    }
}
```

**Key decisions:**
- `Cache-Control: max-age=300` header — tells clients/browsers/Gateway to cache for 5 minutes
- Returns `ApiResponse<List<T>>` — matches the standard envelope
- No pagination needed — 81 cities, 35 professions are small datasets
- No `POST`/`PUT`/`DELETE` endpoints — reference data is seeded at DB level; mutation endpoints can be added later if needed

**Cross-service access pattern:** Other microservices call these REST endpoints through the API Gateway — the same pattern used for customer/vehicle/insurance lookups. No RabbitMQ RPC needed. Services that want to cache reference data locally subscribe to `reference-data.events` (Kafka) for cache invalidation.

### Step 4: Create `GlobalExceptionHandler`

- [x] Create `services/reference-data-service/src/main/java/com/insurancemanagementsystem/referencedata/exception/GlobalExceptionHandler.java`

Package: `com.insurancemanagementsystem.referencedata.exception`

**Pattern:** Copy from `services/reference-skeleton/src/main/java/.../exception/GlobalExceptionHandler.java`:
- `@ControllerAdvice` + `@Slf4j`
- Handle `EntityNotFoundException` → 404
- Handle `MethodArgumentNotValidException` → 400 (field validation errors)
- Handle `IllegalArgumentException` → 400
- Handle `Exception` → 500 (generic fallback, log the error)
- All responses use `ApiResponse.error(message)`

### Step 5: Verify Build & Manual Test

- [x] Run: `.\gradlew.bat :services:reference-data-service:build`
- [ ] Start Docker Compose: `docker compose -f infra/docker/docker-compose.yml up -d reference-data-db`
- [ ] Run service: `.\gradlew.bat :services:reference-data-service:bootRun`
- [ ] Test `GET http://localhost:8086/api/reference-data/cities` — returns 81 cities sorted alphabetically
- [ ] Test `GET http://localhost:8086/api/reference-data/professions` — returns 35 professions sorted alphabetically
- [ ] Verify `Cache-Control: max-age=300` header is present
- [ ] Verify response envelope: `{"success":true,"message":"Operation successful","data":[...],"timestamp":"..."}`

## Deliverables (this plan)

- [x] `ReferenceDataService.java` — cached lookups, entity-to-DTO mapping, cache invalidation
- [x] `ReferenceDataEventPublisher.java` — publishes `ReferenceDataChangedEvent` to `reference-data.events`
- [x] `ReferenceDataController.java` — `GET /api/reference-data/cities`, `GET /api/reference-data/professions`
- [x] `GlobalExceptionHandler.java` — standard error handling
- [x] `.\gradlew.bat :services:reference-data-service:build` passes
- [ ] Manual smoke test against running service returns correct data
