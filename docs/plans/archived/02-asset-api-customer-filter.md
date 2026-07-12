# Plan 02: Add customerId Filter to Vehicle & Real Estate APIs

## Objective

Add an optional `?customerId=` query parameter to `GET /api/vehicles` and `GET /api/real-estate` so the estimation form step 3 can load only the assets belonging to the customer selected in step 1.

## Files to Read First

- `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/controller/VehicleController.java`
- `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/service/VehicleService.java`
- `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/repository/VehicleRepository.java`
- `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/controller/RealEstateController.java`
- `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/service/RealEstateService.java`
- `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/repository/RealEstateRepository.java`
- `frontend/src/lib/api/vehicles.ts`
- `frontend/src/lib/api/realestate.ts`

## Technical Context

- **Architecture rule**: No cross-service REST calls directly — but frontend calls API Gateway which routes to individual services. Adding a query param does NOT violate this rule.
- **Existing pattern**: `GET /api/estimations` already supports `?customerId=` as a filter parameter — follow that same pattern.
- **VehicleRepository** already has `findByCustomerId(UUID customerId, Pageable pageable)` method — it just isn't wired through the controller.
- **RealEstateRepository** already has `findByCustomerId(UUID customerId, Pageable pageable)` method — same situation.
- **AGENTS.md conventions**: Controllers return `ResponseEntity<ApiResponse<Page<T>>>`, services are `@Transactional(readOnly = true)` for read operations, repositories extend `JpaRepository`.
- When `customerId` is provided, the service should filter by customer; when absent, existing behavior (search or findAll) applies. If BOTH `customerId` AND `search` are provided, apply both filters (AND logic).

## Steps

### Step 1: Add customerId param to VehicleController

Open `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/controller/VehicleController.java`.

**Modify the `getAll()` method signature** from:
```java
@GetMapping
public ResponseEntity<ApiResponse<Page<VehicleResponse>>> getAll(
        @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
        @RequestParam(value = "search", required = false) String search) {
    Page<VehicleResponse> vehicles = vehicleService.findAll(pageable, search);
    return ResponseEntity.ok(ApiResponse.success(vehicles));
}
```

**To**:
```java
@GetMapping
public ResponseEntity<ApiResponse<Page<VehicleResponse>>> getAll(
        @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
        @RequestParam(value = "search", required = false) String search,
        @RequestParam(value = "customerId", required = false) UUID customerId) {
    Page<VehicleResponse> vehicles = vehicleService.findAll(pageable, search, customerId);
    return ResponseEntity.ok(ApiResponse.success(vehicles));
}
```

### Step 2: Update VehicleService.findAll()

Open `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/service/VehicleService.java`.

**Modify the `findAll()` method signature and body** from:
```java
@Transactional(readOnly = true)
public Page<VehicleResponse> findAll(Pageable pageable, String search) {
    Page<Vehicle> vehiclePage;
    if (search != null && !search.isBlank()) {
        vehiclePage = vehicleRepository.search(search.trim(), pageable);
    } else {
        vehiclePage = vehicleRepository.findAll(pageable);
    }
    // ... customer name resolution ...
}
```

**To**:
```java
@Transactional(readOnly = true)
public Page<VehicleResponse> findAll(Pageable pageable, String search, UUID customerId) {
    Page<Vehicle> vehiclePage;
    if (customerId != null && search != null && !search.isBlank()) {
        vehiclePage = vehicleRepository.searchByCustomerIdAndSearch(customerId, search.trim(), pageable);
    } else if (customerId != null) {
        vehiclePage = vehicleRepository.findByCustomerId(customerId, pageable);
    } else if (search != null && !search.isBlank()) {
        vehiclePage = vehicleRepository.search(search.trim(), pageable);
    } else {
        vehiclePage = vehicleRepository.findAll(pageable);
    }
    // ... keep existing customer name resolution code unchanged ...
}
```

### Step 3: Add combined query to VehicleRepository

Open `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/repository/VehicleRepository.java`.

**Add** this method (alongside the existing `search` and `findByCustomerId` methods):
```java
@Query("""
    SELECT DISTINCT v FROM Vehicle v
    LEFT JOIN CarBrand b ON b.id = v.carBrandId
    LEFT JOIN CarModel m ON m.id = v.carModelId
    WHERE v.customerId = :customerId
      AND (LOWER(v.plate) LIKE CONCAT('%', LOWER(:search), '%')
           OR LOWER(v.chassisNumber) LIKE CONCAT('%', LOWER(:search), '%')
           OR LOWER(b.name) LIKE CONCAT('%', LOWER(:search), '%')
           OR LOWER(m.name) LIKE CONCAT('%', LOWER(:search), '%'))
""")
Page<Vehicle> searchByCustomerIdAndSearch(@Param("customerId") UUID customerId,
                                          @Param("search") String search,
                                          Pageable pageable);
```

### Step 4: Add customerId param to RealEstateController

Open `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/controller/RealEstateController.java`.

**Modify the `getAll()` method** — add `@RequestParam(value = "customerId", required = false) UUID customerId` and pass it to the service:
```java
@GetMapping
public ResponseEntity<ApiResponse<Page<RealEstateResponse>>> getAll(
        @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
        @RequestParam(value = "search", required = false) String search,
        @RequestParam(value = "customerId", required = false) UUID customerId) {
    Page<RealEstateResponse> realEstates = realEstateService.findAll(pageable, search, customerId);
    return ResponseEntity.ok(ApiResponse.success(realEstates));
}
```

### Step 5: Update RealEstateService.findAll()

Open `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/service/RealEstateService.java`.

**Modify `findAll()` signature** to accept `UUID customerId` and add the filtering logic (same pattern as VehicleService — `customerId + search`, `customerId only`, `search only`, or `findAll`).

### Step 6: Add combined query to RealEstateRepository

Open `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/repository/RealEstateRepository.java`.

Read the existing `search()` method to understand the query structure, then add a `searchByCustomerIdAndSearch()` method following the same pattern as the VehicleRepository (filter by customerId AND the search term columns — address, district).

### Step 7: Update frontend API client — vehicles

Open `frontend/src/lib/api/vehicles.ts`.

**Modify `getVehicles()` function signature** to accept `customerId?: string`:
```typescript
export async function getVehicles(
  page = 0,
  size = 20,
  search?: string,
  sort?: string,
  direction?: string,
  customerId?: string,
): Promise<PageResponse<VehicleResponse>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (search) params.set("search", search);
  if (sort && direction) {
    params.set("sort", `${sort},${direction}`);
  } else if (sort) {
    params.set("sort", sort);
  }
  if (customerId) params.set("customerId", customerId);
  return apiClient<PageResponse<VehicleResponse>>(
    `/api/vehicles?${params.toString()}`
  );
}
```

### Step 8: Update frontend API client — real estate

Open `frontend/src/lib/api/realestate.ts`.

**Modify `getRealEstates()` function** the same way — add `customerId?: string` parameter and append it to query params when present.

## Acceptance Criteria

- [x] `GET /api/vehicles?customerId=<uuid>` returns only vehicles belonging to that customer
- [x] `GET /api/vehicles?search=plate&customerId=<uuid>` returns vehicles matching BOTH search AND customer
- [x] `GET /api/vehicles` (without customerId) still returns all vehicles (backward compatible)
- [x] `GET /api/real-estate?customerId=<uuid>` returns only real estate belonging to that customer
- [x] `GET /api/real-estate?search=address&customerId=<uuid>` returns real estate matching both
- [x] `GET /api/real-estate` (without customerId) still returns all (backward compatible)
- [x] Frontend API client functions accept and forward the `customerId` parameter
