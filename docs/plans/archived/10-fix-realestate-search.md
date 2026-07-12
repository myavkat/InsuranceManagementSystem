# Plan 10: Fix Real Estate Service — Add Search/Filter Support

## Objective

The frontend sends a `?search=...` query parameter to `GET /api/real-estate` (see `frontend/src/lib/api/realestate.ts` line 65), but the backend `RealEstateController.getAll()` only accepts `Pageable` — there is no `@RequestParam` for search. The service calls `realEstateRepository.findAll(pageable)` with zero filtering. This plan adds search support so that typing in the real estate list search bar actually filters results.

The search should match against: **address** (primary), **district**.

## Files to Read First

| File | Reason |
|------|--------|
| `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/controller/RealEstateController.java` | Current controller — you will add a `@RequestParam` to `getAll()` |
| `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/service/RealEstateService.java` | Current service — you will modify `findAll()` to accept and use a search term |
| `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/repository/RealEstateRepository.java` | Current repository — you will add a search query method |
| `docs/outlines/10_JAVA_CONVENTIONS.md` | Java conventions |
| `docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md` | Real estate service spec — confirms `GET /api/real-estate` should list/search |

## Key Technical Context

- **Project uses Java 25, Spring Boot 4.0.6, Spring Data JPA with Hibernate.**
- **Spring Data JPQL**: Use `@Query` annotation with JPQL. Entity/field names use Java class/field names.
- **`LOWER` function**: Wrap both the search term and the entity field in `LOWER()` for case-insensitive matching.
- **`LIKE` with `%` pattern**: Use `LIKE CONCAT('%', LOWER(:search), '%')`.
- **RealEstate entity fields** (from `RealEstate.java`): `address` (String, TEXT column), `district` (String, length 100). Both are nullable.
- **Pageable**: Spring Data's `Pageable` handles pagination and sorting. The repository method must return `Page<RealEstate>` and accept `Pageable` as the last argument.
- **If Plan 09 has been applied**, `RealEstateService.findAll()` was already refactored to do batch city/customer name resolution. Your changes must preserve that logic — add the search SELECTION before the name resolution step.

## Files to Modify

1. `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/controller/RealEstateController.java`
2. `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/service/RealEstateService.java`
3. `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/repository/RealEstateRepository.java`

## Steps

- [x] Step 1: Add search query method to `RealEstateRepository`
- [x] Step 2: Update `RealEstateService.findAll()` to accept and use search term
- [x] Step 3: Update `RealEstateController.getAll()` to accept search param
- [x] Step 4: Verify compilation

### Step 1: Add search query method to `RealEstateRepository`

Open `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/repository/RealEstateRepository.java`.

Current content:
```java
@Repository
public interface RealEstateRepository extends JpaRepository<RealEstate, UUID> {
    Page<RealEstate> findByCustomerId(UUID customerId, Pageable pageable);
}
```

Add a new JPQL search method:

```java
@Query("""
    SELECT r FROM RealEstate r
    WHERE LOWER(r.address) LIKE CONCAT('%', LOWER(:search), '%')
       OR LOWER(r.district) LIKE CONCAT('%', LOWER(:search), '%')
""")
Page<RealEstate> search(@Param("search") String search, Pageable pageable);
```

Add the required imports at the top of the file:
```java
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
```

### Step 2: Update `RealEstateService.findAll()` to accept and use search term

Open `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/service/RealEstateService.java`.

**If Plan 09 has been applied**, the `findAll()` method currently does batch city/customer name resolution. You need to add the search parameter and conditional repository call while preserving the name resolution logic.

**If Plan 09 has NOT been applied**, the method is a simple one-liner.

Pick the version that matches your state:

---

**Version A: Plan 09 already applied** — the method currently looks like:

```java
@Transactional(readOnly = true)
public Page<RealEstateResponse> findAll(Pageable pageable) {
    Page<RealEstate> page = realEstateRepository.findAll(pageable);
    // ... batch city/customer name resolution ...
    return page.map(realEstate -> { ... });
}
```

Change the method signature and the query selection:

```java
@Transactional(readOnly = true)
public Page<RealEstateResponse> findAll(Pageable pageable, String search) {
    Page<RealEstate> page;
    if (search != null && !search.isBlank()) {
        page = realEstateRepository.search(search.trim(), pageable);
    } else {
        page = realEstateRepository.findAll(pageable);
    }

    // --- Keep ALL the existing batch name resolution code below this line unchanged ---
    // (the city ID / customer ID collection, lookup map building, and page.map() logic
    //  from Plan 09 stays exactly as-is)
```

Do NOT remove or modify the city/customer name resolution logic. Only change:
1. The method signature (add `String search` parameter)
2. The first statement that fetches the page (add if/else for search vs findAll)

---

**Version B: Plan 09 NOT applied** — the method currently looks like:

```java
@Transactional(readOnly = true)
public Page<RealEstateResponse> findAll(Pageable pageable) {
    return realEstateRepository.findAll(pageable).map(this::toResponse);
}
```

Replace with:

```java
@Transactional(readOnly = true)
public Page<RealEstateResponse> findAll(Pageable pageable, String search) {
    Page<RealEstate> page;
    if (search != null && !search.isBlank()) {
        page = realEstateRepository.search(search.trim(), pageable);
    } else {
        page = realEstateRepository.findAll(pageable);
    }
    return page.map(this::toResponse);
}
```

(Note: This version uses the inline `toResponse()` mapping since Plan 09 hasn't been applied yet. If Plan 09 is applied later, it will update this method to add name resolution.)

### Step 3: Update `RealEstateController.getAll()` to accept search param

Open `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/controller/RealEstateController.java`.

Find the `getAll()` method. Currently it looks like:
```java
@GetMapping
public ResponseEntity<ApiResponse<Page<RealEstateResponse>>> getAll(
        @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
    Page<RealEstateResponse> properties = realEstateService.findAll(pageable);
    return ResponseEntity.ok(ApiResponse.success(properties));
}
```

Add a `@RequestParam` for search and pass it to the service method:

```java
@GetMapping
public ResponseEntity<ApiResponse<Page<RealEstateResponse>>> getAll(
        @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
        @RequestParam(value = "search", required = false) String search) {
    Page<RealEstateResponse> properties = realEstateService.findAll(pageable, search);
    return ResponseEntity.ok(ApiResponse.success(properties));
}
```

Add the required import at the top if not already present:
```java
import org.springframework.web.bind.annotation.RequestParam;
```

### Step 4: Verify compilation

Run from the repo root:

```
./gradlew :realestate-service:compileJava
```

If there are compilation errors, fix them before marking this plan complete.

## Acceptance Criteria

- [x] `GET /api/real-estate?search=Istanbul` returns only real estate properties whose address or district contains "Istanbul" (case-insensitive)
- [x] `GET /api/real-estate?search=` (empty search) returns all real estate (same as no search param)
- [x] `GET /api/real-estate` (no search param) returns all real estate (backward compatible)
- [x] Pagination and sorting still work when search is active
- [x] Search with special characters (%, _, quotes) does not throw errors — the `LIKE` escape is handled by parameter binding
- [x] Code compiles without errors

## Dependencies

- **None.** This plan is self-contained within realestate-service.
- If Plan 09 (city/customer name resolution) was applied first, follow **Version A** in Step 2 to preserve the name batch-resolution logic.
- If Plan 09 has NOT been applied yet, follow **Version B** in Step 2. Plan 09 will later update this method to add name resolution.
