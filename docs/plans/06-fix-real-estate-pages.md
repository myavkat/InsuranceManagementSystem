# Plan 06: Fix Real Estate Pages

## Objective

Fix the following bugs across real estate pages:

**Real Estate List (`/real-estate`)**:
1. City and customer fields not showing → investigate and fix
2. Sorting doesn't work → fixed by Plan 01 (API sorting format)
3. Search doesn't filter → investigate and fix

**Real Estate Detail (`/real-estate/[id]`)**:
1. City and customer fields not loaded → investigate and fix
2. Dropdowns show IDs → actually, detail pages use read-only `<dl>`, not dropdowns.
   The bug report may refer to the edit page. If the detail page does have dropdown-related
   data display issues, verify and fix.

**Real Estate Form — New (`/real-estate/new`) & Edit (`/real-estate/[id]/edit`)**:
1. Dropdowns show IDs → fixed by Plan 03 (SelectValue render fix)

## Files to Read First

| File | Reason |
|------|--------|
| `frontend-next/src/components/features/real-estate/real-estate-list.tsx` | List page |
| `frontend-next/src/components/features/real-estate/real-estate-detail.tsx` | Detail page |
| `frontend-next/src/components/features/real-estate/real-estate-form.tsx` | Form (used by new + edit) |
| `frontend-next/src/components/features/real-estate/edit-real-estate-form.tsx` | Edit wrapper |
| `frontend-next/src/lib/api/realestate.ts` | API functions and response types |
| `frontend-next/src/components/features/search-bar.tsx` | Search component |

## Files to Modify

1. `frontend-next/src/components/features/real-estate/real-estate-list.tsx`
2. `frontend-next/src/components/features/real-estate/real-estate-detail.tsx`
3. `frontend-next/src/components/features/real-estate/real-estate-form.tsx` (if stale-data fix needed, similar to vehicle Plan 05)

## Steps

### Step 1: Fix city and customer fields not showing in real estate list

Open `frontend-next/src/components/features/real-estate/real-estate-list.tsx`.

The city column is at lines 30-33:
```typescript
columnHelper.accessor("cityName", {
  header: "City",
  cell: (info) => info.getValue() ?? "—",
}),
```

The customer column is at lines 43-46:
```typescript
columnHelper.accessor("customerName", {
  header: "Customer",
  cell: (info) => info.getValue() ?? "—",
}),
```

Check against the API response type `RealEstateResponse` (in `realestate.ts`):
```typescript
cityName?: string;
customerName?: string;
```

Both fields are optional (`?`). If the backend doesn't populate them, the table shows `"—"`.

**Investigation**: Check the actual API response from `/api/real-estate?page=0&size=20`. If
`cityName` and `customerName` are missing or `null` for all rows:
- This is a **backend issue** — the DTO projection in the real-estate service doesn't join the
  city and customer tables.
- Document the finding. The frontend code is correct.

**If the backend DOES send these fields** but they're not rendering:
- Check for JSON key name mismatch (e.g., backend sends `city_name` but the type expects `cityName`).
- The API Gateway should normalize keys to camelCase. Verify this is happening.

**No frontend code change expected** for this step — it's an investigation + documentation step.
If the fields aren't populated by the backend, frontend can't fix it.

### Step 2: Fix search not filtering in real estate list

Open `frontend-next/src/components/features/real-estate/real-estate-list.tsx`.

The search logic is identical to the vehicle list page (see Plan 05, Step 2). The flow:

1. `SearchBar` debounces user input (300ms) → calls `onSearch` → `setSearch(value)`
2. `search` state changes → query key updates → `queryFn` calls `getRealEstates(..., search || undefined, ...)`
3. `getRealEstates` sends `search` as a query param → backend filters

**Verification**: 
- Check that the `SearchBar` fires `onSearch` (add console.log temporarily if needed)
- Check the network tab to see if `?search=...` is in the API URL
- If the search param is sent but results aren't filtered, the issue is on the backend

**Potential frontend fix**: If the search value never reaches the API call, check:
1. Is `search` state updating? (React DevTools)
2. Is the query key changing? (React Query DevTools)
3. Is `getRealEstates` being called with the search value?

The code looks correct at a static analysis level. Same conclusion as Plan 05 Step 2 — likely a
backend search implementation gap.

### Step 3: Fix city and customer fields not loaded in real estate detail

Open `frontend-next/src/components/features/real-estate/real-estate-detail.tsx`.

City field at line 90:
```typescript
<DetailItem label="City" value={property.cityName ?? "—"} />
```

Customer field at line 97:
```typescript
<DetailItem label="Customer" value={property.customerName ?? "—"} />
```

Same investigation as Step 1. Check the `/api/real-estate/{id}` response. If `cityName` and
`customerName` are not populated, it's a backend issue.

The code correctly renders the value or shows `"—"` when it's `null`/`undefined`.

### Step 4: Fix stale data after edit save (same pattern as vehicles)

Open `frontend-next/src/components/features/real-estate/real-estate-form.tsx`.

The `onSuccess` callback of the mutation (around lines 129-131):
```typescript
onSuccess: (result) => {
  queryClient.invalidateQueries({ queryKey: ["real-estate"] });
  router.push(`/real-estate/${result.id}`);
},
```

Same bug as vehicle Plan 05 Step 5 — only the list cache is invalidated, not the detail cache.
When the user is redirected to the detail page, React Query serves stale data.

**Fix**: Add invalidation for the specific detail query:
```typescript
onSuccess: (result) => {
  queryClient.invalidateQueries({ queryKey: ["real-estate"] });
  queryClient.invalidateQueries({ queryKey: ["real-estate", result.id] });
  router.push(`/real-estate/${result.id}`);
},
```

Note: The query key in `edit-real-estate-form.tsx` is:
```typescript
queryKey: ["real-estate", id]
```
And in `real-estate-detail.tsx`:
```typescript
queryKey: ["real-estate", id]
```
So `["real-estate", result.id]` matches both. Good.

### Step 5: Type-check

Run `cd frontend-next && npx tsc --noEmit` to verify no type errors.

## Acceptance Criteria

- [ ] Real estate list shows city names (or documented as backend issue)
- [ ] Real estate list shows customer names (or documented as backend issue)
- [ ] Real estate detail page shows city (or documented as backend issue)
- [ ] Real estate detail page shows customer (or documented as backend issue)
- [ ] Search on real estate list filters results (or documented as backend issue)
- [ ] After editing a real estate property and saving, the detail page shows the updated data
- [ ] Frontend type-checks without errors

## Dependencies

- **Plan 01** (API sorting format) — fixes sorting on the list page
- **Plan 03** (SelectValue render fix) — fixes dropdown ID display on the form pages
