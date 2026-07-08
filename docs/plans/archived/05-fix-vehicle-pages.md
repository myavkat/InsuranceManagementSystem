# Plan 05: Fix Vehicle Pages

## Objective

Fix the following bugs across vehicle pages:

**Vehicle List (`/vehicles`)**:
1. Sorting doesn't work → fixed by Plan 01 (API sorting format)
2. Customer name not shown in table → investigate and fix
3. Search doesn't filter → investigate and fix

**Vehicle Detail (`/vehicles/[id]`)**:
1. Customer field shows dash (`-`) → investigate and fix

**Vehicle Form — Edit (`/vehicles/[id]/edit`)**:
1. Brand re-selection resets model even when same brand is selected → fix
2. After saving, details page shows stale data → fix cache invalidation

**Vehicle Form — New (`/vehicles/new`) & Edit**:
1. Dropdowns show IDs → fixed by Plan 03 (SelectValue render fix)

## Files to Read First

| File | Reason |
|------|--------|
| `frontend-next/src/components/features/vehicles/vehicle-list.tsx` | List page |
| `frontend-next/src/components/features/vehicles/vehicle-detail.tsx` | Detail page |
| `frontend-next/src/components/features/vehicles/vehicle-form.tsx` | Form (used by new + edit) |
| `frontend-next/src/components/features/vehicles/edit-vehicle-form.tsx` | Edit wrapper |
| `frontend-next/src/lib/api/vehicles.ts` | API functions and response types |
| `frontend-next/src/components/features/search-bar.tsx` | Search component (check debounce behavior) |
| `frontend-next/src/components/features/data-table/data-table.tsx` | DataTable (check search/filter wiring) |

## Files to Modify

1. `frontend-next/src/components/features/vehicles/vehicle-list.tsx`
2. `frontend-next/src/components/features/vehicles/vehicle-detail.tsx`
3. `frontend-next/src/components/features/vehicles/vehicle-form.tsx`

## Steps

### Step 1: Fix customer name not showing in vehicle list

Open `frontend-next/src/components/features/vehicles/vehicle-list.tsx`.

The customer column is defined at lines 41-44:
```typescript
columnHelper.accessor("customerName", {
  header: "Customer",
  cell: (info) => info.getValue() ?? "—",
}),
```

The API response type `VehicleResponse` (in `vehicles.ts`) includes:
```typescript
customerName?: string;
```

The field is optional (`?`), meaning the backend may not always populate it. If `customerName`
is `undefined` or `null`, the cell renders `"—"`.

**Investigation**: Check the actual API response. Open the browser DevTools Network tab and
inspect the `/api/vehicles` response. If `customerName` is missing or `null` for all rows,
the issue is on the backend — the API projection (DTO) isn't including the customer name.

**If backend is the issue**: Note this and move on. The frontend code is correct — it reads
`customerName` and renders it or shows `"—"`. The fix belongs in the backend service.

**If frontend is the issue**: Verify the `VehicleResponse` type matches what the backend sends.
The API call uses `apiClient<PageResponse<VehicleResponse>>()`, which deserializes the JSON.
If the backend sends `customerName` but under a different key (e.g., `customer_name`), the
field won't be populated due to case mismatch. Check if the backend uses camelCase (it should,
since the gateway normalizes JSON keys).

For this plan, **verify the API response** and document findings. If it's a backend issue,
the fix is out of scope for this frontend plan.

However, one thing to verify: the `getVehicles` API function call. In `vehicle-list.tsx`, the
query function at line 81 is:
```typescript
getVehicles(pagination.pageIndex, pagination.pageSize, search || undefined, sortField, sortDirection)
```

This looks correct. The API function sends the request to `/api/vehicles?...`. Verify that
`apiClient` receives the correct URL params.

### Step 2: Fix search not filtering in vehicle list

Open `frontend-next/src/components/features/vehicles/vehicle-list.tsx`.

The search flow:
1. User types in `SearchBar` → after 300ms debounce, `onSearch` fires → calls `setSearch(value)`
2. DataTable receives `globalFilter={search}` and `onGlobalFilterChange` which also calls `setSearch`
3. `search` state change → `useQuery` query key changes → `queryFn` runs → calls `getVehicles(..., search || undefined, ...)`
4. `getVehicles` sends `search` as query param → backend filters results

**Investigation:**
- Check if the `SearchBar` component actually fires `onSearch`. The `SearchBar` has internal state
  and a debounce timer. Verify the timer callback fires correctly.
- Check if `onGlobalFilterChange` from DataTable and `onSearch` from SearchBar are both calling
  `setSearch` — this double-update might cause issues. However, since both set the same state,
  React should batch the updates.

**Possible fix if search isn't triggering**: The DataTable's `onGlobalFilterChange` calls
`setSearch(value)` directly without debouncing. If the SearchBar's `onSearch` (debounced) and
`onGlobalFilterChange` (immediate) both fire, the query might fire twice. But more importantly —
does `onGlobalFilterChange` ever actually fire? Looking at the DataTable code, `onGlobalFilterChange`
is only called by `useReactTable`'s internal global filter mechanism. Since `manualFiltering: true`,
tanstack table doesn't call `onGlobalFilterChange` from its own UI — it only calls it if there's
a UI element that triggers it. Since the DataTable has no built-in search input, `onGlobalFilterChange`
is only called if some Table plugin triggers it (none does in this setup).

So the search should work through the SearchBar → `onSearch` → `setSearch` path. If it doesn't work,
the most likely cause is:
1. The backend doesn't support `?search=...` on the vehicles endpoint
2. The search state is being reset somewhere

**Verify**: Add a console.log or React DevTools check to confirm `search` state updates when typing.
Then check the network request to see if the `search` param is in the URL.

**If no fix is needed on frontend**: Document that the frontend code appears correct and the issue
may be on the backend.

However, there IS one potential issue: the `initialData` check at line 83:
```typescript
initialData:
  pagination.pageIndex === 0 && !search && !sortField ? initialData : undefined,
```

When `search` is non-empty, `initialData` is `undefined`, so React Query WILL fetch from the server.
This is correct. The query should fire and include `search` in the URL params.

Wait — one more thing to check. Does `getVehicles` function correctly handle the search param?
```typescript
export async function getVehicles(
  page = 0, size = 20, search?: string, sort?: string, direction?: string,
): Promise<PageResponse<VehicleResponse>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (search) params.set("search", search);
  ...
}
```

This is correct. If `search` is a non-empty string, it's added to params. The URL would be:
`/api/vehicles?page=0&size=20&search=ABC123`

This should filter results if the backend supports it. If the backend doesn't support filtering
by the `search` parameter, that's a backend issue.

**Conclusion for this step**: The frontend code appears correct. Verify by checking the network
tab. If search isn't working, the fix is on the backend. Document findings.

### Step 3: Fix customer field in vehicle detail

Open `frontend-next/src/components/features/vehicles/vehicle-detail.tsx`.

The customer field is at line 95:
```typescript
<DetailItem label="Customer" value={vehicle.customerName ?? "—"} />
```

Same investigation as Step 1 — if `customerName` is not populated by the API, the detail view
shows `"—"`. Check the `/api/vehicles/{id}` response for `customerName`.

The `getVehicle(id)` function calls `/api/vehicles/${id}` and returns `VehicleResponse`. If the
backend single-entity endpoint includes `customerName` but the list endpoint doesn't, check that.

### Step 4: Fix brand-model cascading (don't reset model for same brand)

Open `frontend-next/src/components/features/vehicles/vehicle-form.tsx`.

The `handleBrandChange` callback at lines 130-136:
```typescript
const handleBrandChange = useCallback(
  (value: string | null) => {
    setValue("carBrandId", value ?? "");
    setValue("carModelId", "");
  },
  [setValue]
);
```

This ALWAYS resets `carModelId` to `""` whenever the brand dropdown changes — even if the user
re-selects the same brand. The fix is to check whether the value actually changed before resetting:

```typescript
const handleBrandChange = useCallback(
  (value: string | null) => {
    const newBrandId = value ?? "";
    const currentBrandId = watch("carBrandId");
    
    setValue("carBrandId", newBrandId);
    
    // Only reset model if brand actually changed
    if (newBrandId !== currentBrandId) {
      setValue("carModelId", "");
    }
  },
  [setValue, watch]
);
```

Add `watch` to the dependency array since we're now using it inside the callback.

**Important**: `watch` returns the current form value. `watch("carBrandId")` at the time of the
callback should return the OLD value (before `setValue` is called), because `setValue` updates
are batched and the form hasn't re-rendered yet. So `currentBrandId` is the old value,
`newBrandId` is the newly selected value. If they match, skip resetting the model.

### Step 5: Fix stale data after edit save

Open `frontend-next/src/components/features/vehicles/vehicle-form.tsx`.

The `onSuccess` callback of the edit mutation (around lines 157-159):
```typescript
onSuccess: (result) => {
  queryClient.invalidateQueries({ queryKey: ["vehicles"] });
  router.push(`/vehicles/${result.id}`);
},
```

The issue: `invalidateQueries({ queryKey: ["vehicles"] })` only invalidates the LIST query
(`["vehicles", ...]`), but it DOESN'T invalidate the specific detail query `["vehicle", result.id]`.
When the user is redirected to the detail page, React Query serves the stale cached data for
`["vehicle", id]` because it wasn't invalidated.

**Fix**: Add invalidation for the specific vehicle detail query:
```typescript
onSuccess: (result) => {
  queryClient.invalidateQueries({ queryKey: ["vehicles"] });
  queryClient.invalidateQueries({ queryKey: ["vehicle", result.id] });
  router.push(`/vehicles/${result.id}`);
},
```

This ensures both the list cache AND the specific vehicle detail cache are invalidated before
navigating to the detail page. The detail page will fetch fresh data from the server.

Note: `invalidateQueries` with `{ queryKey: ["vehicle", result.id] }` matches the exact query
key used in `vehicle-detail.tsx`: `["vehicle", id]` and `edit-vehicle-form.tsx`: `["vehicle", id]`.

### Step 6: Type-check

Run `cd frontend-next && npx tsc --noEmit` to verify no type errors.

## Acceptance Criteria

- [x] Vehicle list shows customer names (or verified as backend issue with documented findings)
- [x] Vehicle detail page shows customer name (or verified as backend issue)
- [x] Search on vehicle list filters results (or verified as backend issue with documented findings)
- [x] Re-selecting the same brand in vehicle form does NOT reset the model dropdown
- [x] Changing the brand in vehicle form DOES reset the model dropdown
- [x] After editing a vehicle and saving, the detail page shows the updated data (not stale)
- [x] Frontend type-checks without errors

## Dependencies

- **Plan 01** (API sorting format) — fixes sorting on the list page
- **Plan 03** (SelectValue render fix) — fixes dropdown ID display on the form pages
