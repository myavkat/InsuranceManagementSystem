# Plan 08: Fix Vehicle Service — Add Search/Filter Support

## Objective

The frontend sends a `?search=...` query parameter to `GET /api/vehicles` (see `frontend-next/src/lib/api/vehicles.ts` line 87), but the backend `VehicleController.getAll()` only accepts `Pageable` — there is no `@RequestParam` for search. The service calls `vehicleRepository.findAll(pageable)` with zero filtering. This plan adds search support so that typing in the vehicle list search bar actually filters results.

The search should match against: **plate** (primary), **chassis number**, **brand name**, and **model name**.

## Files to Read First

| File | Reason |
|------|--------|
| `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/controller/VehicleController.java` | Current controller — you will add a `@RequestParam` to `getAll()` |
| `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/service/VehicleService.java` | Current service — you will modify `findAll()` to accept and use a search term |
| `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/repository/VehicleRepository.java` | Current repository — you will add a JPQL search query method |
| `docs/outlines/10_JAVA_CONVENTIONS.md` | Java conventions |
| `docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md` | Vehicle service spec — confirms `GET /api/vehicles` should list/search |

## Key Technical Context

- **Project uses Java 25, Spring Boot 4.0.6, Spring Data JPA with Hibernate.**
- **Spring Data JPQL**: Use `@Query` annotation with JPQL (not native SQL). Entity/field names use Java class/field names, not DB table/column names.
- **`CONCAT` in JPQL**: Use `CONCAT(a, b)` function. For multiple fields, nest them: `CONCAT(CONCAT(v.plate, ' '), v.chassisNumber)`.
- **`LOWER` function**: Wrap both the search term and the entity field in `LOWER()` for case-insensitive matching.
- **`LIKE` with `%` pattern**: Use `LIKE CONCAT('%', LOWER(:search), '%')`.
- **Vehicle entity fields** (from `Vehicle.java`): `plate` (String), `chassisNumber` (String), `carBrandId` (Integer), `carModelId` (Integer). Brand and model NAMES are in separate tables (`CarBrand`, `CarModel`), so searching by brand/model name requires a JOIN.
- **Pageable**: Spring Data's `Pageable` parameter handles pagination and sorting automatically. The repository method must return `Page<Vehicle>` and accept `Pageable` as the last argument.
- **If Plan 07 has been applied**, `VehicleService.findAll()` was already refactored to do batch customer-name resolution. Your changes must preserve that logic — add the search SELECTION before the name resolution step.

## Files to Modify

1. `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/controller/VehicleController.java`
2. `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/service/VehicleService.java`
3. `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/repository/VehicleRepository.java`

## Steps

### Step 1: Add search query method to `VehicleRepository`

Open `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/repository/VehicleRepository.java`.

Current content:
```java
@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {
    Optional<Vehicle> findByPlate(String plate);
    Page<Vehicle> findByCustomerId(UUID customerId, Pageable pageable);
}
```

Add a new method that searches across plate, chassis number, brand name, and model name. Since brand name and model name are in joined tables, use JPQL with explicit JOINs:

```java
@Query("""
    SELECT DISTINCT v FROM Vehicle v
    LEFT JOIN CarBrand b ON b.id = v.carBrandId
    LEFT JOIN CarModel m ON m.id = v.carModelId
    WHERE LOWER(v.plate) LIKE CONCAT('%', LOWER(:search), '%')
       OR LOWER(v.chassisNumber) LIKE CONCAT('%', LOWER(:search), '%')
       OR LOWER(b.name) LIKE CONCAT('%', LOWER(:search), '%')
       OR LOWER(m.name) LIKE CONCAT('%', LOWER(:search), '%')
""")
Page<Vehicle> search(@Param("search") String search, Pageable pageable);
```

**Note**: The JOINs use `LEFT JOIN` with `ON b.id = v.carBrandId` (not JPA relationship paths, since `Vehicle` has no `@ManyToOne` mapping — it stores raw integer IDs). This works because the entity classes are in the same persistence unit.

Add the required imports at the top of the file:
```java
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
```

### Step 2: Update `VehicleService.findAll()` to accept and use search term

Open `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/service/VehicleService.java`.

**If Plan 07 has been applied**, the `findAll()` method currently looks like:
```java
@Transactional(readOnly = true)
public Page<VehicleResponse> findAll(Pageable pageable) {
    Page<Vehicle> vehiclePage = vehicleRepository.findAll(pageable);
    // ... batch customer name resolution ...
    return vehiclePage.map(vehicle -> { ... });
}
```

**If Plan 07 has NOT been applied**, the `findAll()` method looks like:
```java
@Transactional(readOnly = true)
public Page<VehicleResponse> findAll(Pageable pageable) {
    return vehicleRepository.findAll(pageable).map(this::toResponse);
}
```

Modify `findAll()` to accept a new `String search` parameter and use it to choose between `search()` and `findAll()`:

**Version A: Plan 07 already applied** — change the method signature and the first line:

Change from:
```java
public Page<VehicleResponse> findAll(Pageable pageable) {
    Page<Vehicle> vehiclePage = vehicleRepository.findAll(pageable);
```

To:
```java
public Page<VehicleResponse> findAll(Pageable pageable, String search) {
    Page<Vehicle> vehiclePage;
    if (search != null && !search.isBlank()) {
        vehiclePage = vehicleRepository.search(search.trim(), pageable);
    } else {
        vehiclePage = vehicleRepository.findAll(pageable);
    }
```

Leave the rest of the method (customer name batch-resolution and mapping) unchanged.

**Version B: Plan 07 NOT applied** — replace the entire method:

```java
@Transactional(readOnly = true)
public Page<VehicleResponse> findAll(Pageable pageable, String search) {
    Page<Vehicle> vehiclePage;
    if (search != null && !search.isBlank()) {
        vehiclePage = vehicleRepository.search(search.trim(), pageable);
    } else {
        vehiclePage = vehicleRepository.findAll(pageable);
    }
    return vehiclePage.map(this::toResponse);
}
```

(Note: This version uses the simple inline `toResponse()` mapping since Plan 07 hasn't been applied yet. If Plan 07 is applied later, that plan will update this method to add customer name resolution.)

### Step 3: Update `VehicleController.getAll()` to accept search param

Open `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/controller/VehicleController.java`.

Find the `getAll()` method. Currently it looks like:
```java
@GetMapping
public ResponseEntity<ApiResponse<Page<VehicleResponse>>> getAll(
        @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
    Page<VehicleResponse> vehicles = vehicleService.findAll(pageable);
    return ResponseEntity.ok(ApiResponse.success(vehicles));
}
```

Add a `@RequestParam` for search and pass it to the service method:

```java
@GetMapping
public ResponseEntity<ApiResponse<Page<VehicleResponse>>> getAll(
        @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
        @RequestParam(value = "search", required = false) String search) {
    Page<VehicleResponse> vehicles = vehicleService.findAll(pageable, search);
    return ResponseEntity.ok(ApiResponse.success(vehicles));
}
```

Add the required import at the top if not already present:
```java
import org.springframework.web.bind.annotation.RequestParam;
```

### Step 4: Verify compilation

Run from the repo root:

```
./gradlew :vehicle-service:compileJava
```

If there are compilation errors, fix them before marking this plan complete.

## Acceptance Criteria

- [x] `GET /api/vehicles?search=ABC123` returns only vehicles whose plate, chassis number, brand name, or model name contains "ABC123" (case-insensitive)
- [x] `GET /api/vehicles?search=` (empty search) returns all vehicles (same as no search param)
- [x] `GET /api/vehicles` (no search param) returns all vehicles (backward compatible)
- [x] Pagination and sorting still work when search is active
- [x] Search with special characters (%, _, quotes) does not throw errors — the `LIKE` escape is handled by parameter binding
- [x] Code compiles without errors

## Dependencies

- **None.** This plan is self-contained within vehicle-service.
- If Plan 07 (customer name resolution) was applied first, follow **Version A** in Step 2 to preserve the customer name batch-resolution logic.
- If Plan 07 has NOT been applied yet, follow **Version B** in Step 2. Plan 07 will later update this method to add customer name resolution.
