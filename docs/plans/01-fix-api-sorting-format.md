# Plan 01: Fix API Sorting — Spring Data Format

## Objective

Fix sort parameter format in the API client functions for insurances, vehicles, and real-estate.
These functions currently send `sort` and `direction` as separate query parameters, but the
Spring Data backend expects the compound format `sort=field,direction` (e.g., `sort=name,asc`).

The customers API function already uses the correct format — use it as the reference.

## Files to Read First

| File | Reason |
|------|--------|
| `frontend-next/src/lib/api/customers.ts` | Reference: `getCustomers()` already uses correct `sort=field,direction` format |
| `frontend-next/src/lib/api/insurances.ts` | Will be modified |
| `frontend-next/src/lib/api/vehicles.ts` | Will be modified |
| `frontend-next/src/lib/api/realestate.ts` | Will be modified |

## Files to Modify

All three files are in `frontend-next/src/lib/api/`:

1. `insurances.ts` — function `getInsurances()`
2. `vehicles.ts` — function `getVehicles()`
3. `realestate.ts` — function `getRealEstates()`

## Steps

### Step 1: Open the reference file

Open `frontend-next/src/lib/api/customers.ts`. Look at how `getCustomers()` builds the sort parameter
(lines 42-47):

```typescript
if (sort && direction) {
  // Spring Data Pageable format: sort=field,direction
  params.set("sort", `${sort},${direction}`);
} else if (sort) {
  params.set("sort", sort);
}
```

This is the pattern to replicate. Note: the direction parameter is NOT sent as a separate query param.

### Step 2: Fix `insurances.ts`

Open `frontend-next/src/lib/api/insurances.ts`.

Current code (lines 52-57):
```typescript
if (sort) params.set("sort", sort);
if (direction) params.set("direction", direction);
```

Replace those two lines with the compound format:
```typescript
if (sort && direction) {
  params.set("sort", `${sort},${direction}`);
} else if (sort) {
  params.set("sort", sort);
}
```

Do NOT delete the `direction` function parameter — it's still needed to construct the compound value.
Just delete the line that sends it as a separate query param.

### Step 3: Fix `vehicles.ts`

Open `frontend-next/src/lib/api/vehicles.ts`.

Same change as Step 2. In `getVehicles()`, replace the two separate-param lines at lines 88-89:
```typescript
if (sort) params.set("sort", sort);
if (direction) params.set("direction", direction);
```

With:
```typescript
if (sort && direction) {
  params.set("sort", `${sort},${direction}`);
} else if (sort) {
  params.set("sort", sort);
}
```

### Step 4: Fix `realestate.ts`

Open `frontend-next/src/lib/api/realestate.ts`.

Same change as Step 2. In `getRealEstates()`, replace the two separate-param lines at lines 67-68:
```typescript
if (sort) params.set("sort", sort);
if (direction) params.set("direction", direction);
```

With:
```typescript
if (sort && direction) {
  params.set("sort", `${sort},${direction}`);
} else if (sort) {
  params.set("sort", sort);
}
```

### Step 5: Verification

After making all three changes, verify:

1. The TypeScript compiler is happy — no type errors on the sort/direction parameters.
2. Build the frontend: run `cd frontend-next && npx tsc --noEmit` (type-check only, no emit).
3. The three list pages at these routes should now sort correctly when a column header is clicked:
   - `/insurances` — sort by Name, Base Premium, Status, Created
   - `/vehicles` — sort by Plate, Brand/Model, Created
   - `/real-estate` — sort by Address, Square Meters, Created

## Acceptance Criteria

- [x] `getInsurances()` sends `sort=name,asc` (compound) instead of `sort=name&direction=asc` (separate)
- [x] `getVehicles()` sends `sort=plate,desc` (compound) instead of `sort=plate&direction=desc` (separate)
- [x] `getRealEstates()` sends `sort=address,asc` (compound) instead of `sort=address&direction=asc` (separate)
- [x] No remaining `params.set("direction", ...)` calls in any of the three files
- [x] Frontend type-checks without errors (`npx tsc --noEmit`)

## Dependencies

None. This plan is independent and should be executed first.
