# Plan: Sprint 5 — Unit Tests, Slice Tests & Integration Tests

## Context Files (Read Before Starting)

| File | Purpose |
|------|---------|
| `docs/outlines/11_TESTING_CONVENTIONS.md` | **CRITICAL** — RestTestClient, @WebMvcTest/@SpringBootTest imports, AssertJ, Jackson 3 ObjectMapper path |
| `docs/outlines/10_JAVA_CONVENTIONS.md` | Jackson 3 imports, Lombok order |
| `docs/outlines/12_DEVELOPER_COMMANDS.md` | Test run commands |
| `AGENTS.md` | Global execution constraints |
| `services/reference-skeleton/src/test/java/.../SkeletonApplicationTests.java` | **Integration test pattern** — Testcontainers, RestTestClient, DynamicPropertySource |
| `services/customer-service/src/test/java/.../controller/CustomerControllerTest.java` | **Slice test pattern** — @WebMvcTest, RestTestClient.bindTo(mockMvc) |
| `services/customer-service/src/test/java/.../CustomerServiceApplicationTests.java` | Integration test with Testcontainers |
| `services/reference-data-service/src/main/java/.../controller/ReferenceDataController.java` | Controller under test |
| `services/reference-data-service/src/main/java/.../service/ReferenceDataService.java` | Service under test |
| `services/reference-data-service/src/main/java/.../entity/City.java` | Entity under test |
| `services/reference-data-service/src/main/java/.../entity/Profession.java` | Entity under test |
| `infra/sql/reference_data_db/init.sql` | DB schema and seed data — used for integration test expectations |

## Prerequisites

- [ ] All previous plans (01-03) completed — service compiles and runs

## Test Coverage Target

≥80% overall coverage (line + branch). Focus areas:
- Controller endpoints (both happy path + error cases)
- Service caching logic (cache hit, cache miss, TTL expiry)
- Entity lifecycle callbacks (`@PrePersist` sets timestamps)

## Crucial Spring Boot 4 Import Paths (MUST USE)

From [11_TESTING_CONVENTIONS.md](../../outlines/11_TESTING_CONVENTIONS.md):

| Class | Correct Import (Spring Boot 4) |
|-------|-------------------------------|
| `@WebMvcTest` | `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` |
| `@AutoConfigureRestTestClient` | `org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient` |
| `@MockitoBean` | `org.springframework.test.context.bean.override.mockito.MockitoBean` |
| `@EntityScan` | `org.springframework.boot.persistence.autoconfigure.EntityScan` |
| `ObjectMapper` (Jackson 3) | `tools.jackson.databind.ObjectMapper` |
| `JsonMapper` (Jackson 3) | `tools.jackson.databind.json.JsonMapper` |
| `RestTestClient` | `org.springframework.test.web.servlet.client.RestTestClient` |

**DO NOT use:**
- `TestRestTemplate` or raw `RestTemplate` — always use `RestTestClient`
- Hamcrest matchers — use AssertJ `assertThat()`
- Old Spring Boot 3 import paths (e.g., `org.springframework.boot.autoconfigure.domain.EntityScan`)

## Assertion Rules

- **HTTP assertions** → `.jsonPath()` on response
- **Domain/entity assertions** → AssertJ `assertThat()`
- **JSON tree extraction** → `objectMapper.readTree(responseBody)` only for extracting IDs for DB verification
- **Never** use `objectMapper.readTree() + assertThat()` when `jsonPath()` covers the same check

## Implementation Steps

### Step 1: Create `CityRepositoryTest` (Slice — Data JPA)

- [ ] Create `services/reference-data-service/src/test/java/com/insurancemanagementsystem/referencedata/repository/CityRepositoryTest.java`

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CityRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private CityRepository cityRepository;

    @Test
    void shouldReturnCitiesSortedByName() {
        // Given: insert test cities out of order
        City istanbul = City.builder().id(34).name("İstanbul").plateCode("34").build();
        City ankara = City.builder().id(6).name("Ankara").plateCode("06").build();
        cityRepository.saveAll(List.of(istanbul, ankara));

        // When
        List<City> cities = cityRepository.findAllByOrderByNameAsc();

        // Then
        assertThat(cities).hasSizeGreaterThanOrEqualTo(2);
        assertThat(cities.get(0).getName()).isEqualTo("Ankara"); // alphabetically first
    }
}
```

**Test cases:**
- [ ] `shouldReturnCitiesSortedByName` — cities returned in alphabetical order
- [ ] `shouldFindCityById` — `findById(34)` returns İstanbul
- [ ] `shouldReturnEmptyForUnknownId` — `findById(999)` returns empty Optional

### Step 2: Create `ProfessionRepositoryTest` (Slice — Data JPA)

- [ ] Create `services/reference-data-service/src/test/java/.../repository/ProfessionRepositoryTest.java`

Same pattern as CityRepositoryTest:
- [ ] `shouldReturnProfessionsSortedByName`
- [ ] `shouldFindProfessionById`
- [ ] `shouldReturnEmptyForUnknownId`

### Step 3: Create `ReferenceDataServiceTest` (Unit Test)

- [ ] Create `services/reference-data-service/src/test/java/.../service/ReferenceDataServiceTest.java`

```java
@ExtendWith(MockitoExtension.class)
class ReferenceDataServiceTest {

    @Mock
    private CityRepository cityRepository;
    @Mock
    private ProfessionRepository professionRepository;

    @InjectMocks
    private ReferenceDataService service;

    // Test cases below
}
```

**Test cases:**
- [ ] `shouldReturnCitiesFromRepository` — mock repository returns cities, verify correct mapping to DTOs
- [ ] `shouldCacheCitiesOnSecondCall` — repository called only once across two `getCities()` calls
- [ ] `shouldReturnProfessionsFromRepository` — mock repository, verify DTO mapping
- [ ] `shouldCacheProfessionsOnSecondCall` — repository called only once across two `getProfessions()` calls
- [ ] `shouldInvalidateCache` — populate cache, call `invalidateCache()`, next `getCities()` hits repository again

### Step 4: Create `ReferenceDataControllerTest` (Slice — @WebMvcTest)

- [ ] Create `services/reference-data-service/src/test/java/.../controller/ReferenceDataControllerTest.java`

```java
@WebMvcTest(ReferenceDataController.class)
class ReferenceDataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReferenceDataService service;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        this.client = RestTestClient.bindTo(mockMvc).build();
    }
}
```

**Test cases:**
- [ ] `shouldReturnCitiesList` — mock service returns city list, verify HTTP 200 + JSON structure
  ```java
  client.get().uri("/api/reference-data/cities")
      .exchange()
      .expectStatus().isOk()
      .expectBody()
      .jsonPath("$.success").isEqualTo(true)
      .jsonPath("$.data[0].id").isEqualTo(6)
      .jsonPath("$.data[0].name").isEqualTo("Ankara")
      .jsonPath("$.data[0].plateCode").isEqualTo("06");
  ```
- [ ] `shouldReturnProfessionsList` — mock, verify HTTP 200 + structure
- [ ] `shouldIncludeCacheControlHeader` — verify `Cache-Control` header present
- [ ] `shouldReturnSuccessTrue` — verify `$.success` is `true`

### Step 5: Create `ReferenceDataServiceApplicationTests` (Integration Test)

- [ ] Create `services/reference-data-service/src/test/java/.../ReferenceDataServiceApplicationTests.java`

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
class ReferenceDataServiceApplicationTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("reference_data_db")
            .withUsername("test")
            .withPassword("test");

    @Container
    static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private ProfessionRepository professionRepository;
}
```

**Test cases:**
- [ ] `contextLoads` — Spring context starts successfully
- [ ] `shouldReturnCitiesFromDatabase` — seed test data, call `/api/reference-data/cities`, verify JSON response
  ```java
  @Test
  void shouldReturnCitiesFromDatabase() {
      // Given: seed city data
      cityRepository.save(City.builder().id(6).name("Ankara").plateCode("06").build());
      cityRepository.save(City.builder().id(34).name("İstanbul").plateCode("34").build());

      // When/Then
      restTestClient.get().uri("/api/reference-data/cities")
              .exchange()
              .expectStatus().isOk()
              .expectBody()
              .jsonPath("$.success").isEqualTo(true)
              .jsonPath("$.data.length()").isEqualTo(2)
              .jsonPath("$.data[0].name").isEqualTo("Ankara");
  }
  ```
- [ ] `shouldReturnProfessionsFromDatabase` — seed, call, verify
- [ ] `shouldReturnEmptyArrayWhenNoData` — empty DB, city list returns `[]`, HTTP 200
- [ ] `shouldHaveCorrectResponseEnvelope` — verify `success`, `message`, `data`, `timestamp` fields exist

### Step 6: Verify Coverage

- [ ] Run: `.\gradlew.bat :services:reference-data-service:test jacocoTestReport`
- [ ] Open `services/reference-data-service/build/reports/jacoco/test/html/index.html`
- [ ] Confirm ≥80% line coverage across all production classes
- [ ] If coverage is below threshold, identify untested branches and add tests

### Step 7: Verify All Tests Pass

- [ ] Run: `.\gradlew.bat :services:reference-data-service:test`
- [ ] All tests green
- [ ] No test depends on test order — each test cleans up after itself

## Deliverables (this plan)

- [ ] `CityRepositoryTest.java` — 3 test cases
- [ ] `ProfessionRepositoryTest.java` — 3 test cases
- [ ] `ReferenceDataServiceTest.java` — 5 test cases (caching + mapping)
- [ ] `ReferenceDataControllerTest.java` — 4 test cases (REST endpoints + headers)
- [ ] `ReferenceDataServiceApplicationTests.java` — 5 integration test cases
- [ ] JaCoCo report ≥80% coverage
- [ ] `.\gradlew.bat :services:reference-data-service:test` — all tests pass
- [ ] `.\gradlew.bat :services:reference-data-service:build` — full build passes
