# Testing Conventions Outline (Spring Boot 4)

## HTTP Client for Tests

Always use **`RestTestClient`** — never `TestRestTemplate` or raw `RestTemplate`.

---

## Test Types

### Slice Tests (Controller-Only)

For testing controllers in isolation with mocked services:

```java
@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        this.client = RestTestClient.bindTo(mockMvc).build();
    }
}
```

- Use `@WebMvcTest` + `@Autowired MockMvc`
- Wrap with `RestTestClient.bindTo(mockMvc).build()` in `@BeforeEach`
- Provides the fluent API without a real server

### Integration Tests (Full Context)

For end-to-end tests with real DB and messaging:

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureRestTestClient
class CustomerServiceApplicationTests {

    @Autowired
    private RestTestClient client;
}
```

- Use `@SpringBootTest(webEnvironment = RANDOM_PORT)`
- Use `@AutoConfigureRestTestClient` + `@Autowired RestTestClient`
- **No `@LocalServerPort`** — `RestTestClient` handles URL resolution automatically
- **No manual URL-building** — use relative paths in requests

---

## Key Import Paths (Spring Boot 4)

The project uses Spring Boot 4 with Jackson 3. Many classes were repackaged or renamed as part of Spring Boot's modularization — always check the actual import before writing direct references.

### Test Infrastructure

| Annotation / Class | Import Path |
|---|---|
| `@WebMvcTest` | `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` |
| `@AutoConfigureRestTestClient` | `org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient` |
| `@MockitoBean` | `org.springframework.test.context.bean.override.mockito.MockitoBean` |
| `ObjectMapper` (Jackson 3) | `tools.jackson.databind.ObjectMapper` |
| `JsonMapper` (Jackson 3) | `tools.jackson.databind.json.JsonMapper` |
| `@ExtendWith(MockitoExtension.class)` | `org.mockito.junit.jupiter.MockitoExtension` |

### JPA / Persistence

| Annotation / Class | Import Path (Spring Boot 4) | Old Path (Spring Boot 3) |
|---|---|---|
| `@EntityScan` | `org.springframework.boot.persistence.autoconfigure.EntityScan` | `org.springframework.boot.autoconfigure.domain.EntityScan` |
| `DataJpaRepositoriesAutoConfiguration` | `org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration` | `org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration` |
| `DataSourceAutoConfiguration` | `org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration` | `org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration` |

---

## Hibernate 6+ JSON(B) Mapping in Tests

Entities using `@Column(columnDefinition = "JSONB")` fail in integration tests with Hibernate's `create-drop` DDL — PostgreSQL rejects String→JSONB inserts. **Replace `columnDefinition` with Hibernate 6+'s built-in `@JdbcTypeCode(SqlTypes.JSON)`:**

```java
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// Before (fails with create-drop in tests):
@Column(name = "details", columnDefinition = "JSONB")
private String details;

// After (works with both create-drop and validate):
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "details")
private String details;
```

Hibernate 6+ handles JSON serialization natively — no additional Hibernate Types library needed. The annotation works for `String`, `Map<String, ?>`, and `Serializable` custom types.

---

## Assertion Rules

### HTTP Response Assertions → `.jsonPath()`

```java
// ✅ Correct — jsonPath for HTTP assertions
client.get().uri("/api/customers/{id}", customerId)
    .exchange()
    .expectStatus().isOk()
    .expectBody()
    .jsonPath("$.data.firstName").isEqualTo("John")
    .jsonPath("$.success").isEqualTo(true);
```

### Domain-Level Assertions → AssertJ `assertThat()`

```java
// ✅ Correct — AssertJ for DB state / entity field checks
Customer saved = customerRepository.findById(customerId).orElseThrow();
assertThat(saved.getFirstName()).isEqualTo("John");
assertThat(saved.getDeletedAt()).isNull();
```

### Avoid

- ❌ `objectMapper.readTree() + assertThat()` for HTTP assertions when `jsonPath()` covers the same check
- ❌ Hamcrest matchers in test assertions
- ❌ `TestRestTemplate` or raw `RestTemplate` — always use `RestTestClient`

### `readTree()` is acceptable only for:

Extracting values from the response body (e.g., an entity ID) needed for subsequent DB verification:

```java
String responseBody = new String(result.getResponseBody());
JsonNode root = objectMapper.readTree(responseBody);
UUID customerId = UUID.fromString(root.get("data").get("id").asText());
```

---

## Database Isolation

- Each integration test class cleans shared state — **never rely on test order**.
- Use `@BeforeEach` to delete test data or `@DirtiesContext` to reset the full context.
- `@DirtiesContext` is the fallback; prefer targeted cleanup in `@BeforeEach`.
