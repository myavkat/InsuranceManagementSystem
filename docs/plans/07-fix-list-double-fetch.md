# Plan: Fix List Component Double-Fetch on SSR Hydration

**Source:** Sprint 8 code review — finding 12

---

## Objective

Add `staleTime` to `useQuery` calls in all 5 list components to prevent an immediate client-side refetch of data that was already fetched by the server (via `serverFetch` / `initialData`).

Currently, every navigation to a list page triggers **two** API calls: one SSR `serverFetch` + one client `useQuery` refetch on mount. This wastes bandwidth and adds latency.

---

## Severity

🟡 **Low-Med** — Performance. Each page load doubles API calls for the first page of data.

---

## Files to Read First

| File | Why |
|------|-----|
| `frontend-next/src/components/features/customers/customer-list.tsx` | Example — the `useQuery` call with `initialData` |
| `frontend-next/src/components/features/estimations/estimation-list.tsx` | Same pattern |
| `frontend-next/src/components/features/insurances/insurance-list.tsx` | Same pattern |
| `frontend-next/src/components/features/real-estate/real-estate-list.tsx` | Same pattern |
| `frontend-next/src/components/features/vehicles/vehicle-list.tsx` | Same pattern |

---

## Steps

### Finding — Fix: No `staleTime` on `useQuery` with `initialData`

**The bug**: Each list component uses `useQuery` with `initialData` (provided by the SSR page component via `serverFetch`). However, `staleTime` defaults to `0` (zero), meaning TanStack Query considers the `initialData` immediately stale. On mount, it fires a refetch to the same API endpoint, causing a redundant network call. The user experiences: server renders data → client hydrates → query refetches → UI updates again with the same data.

**The fix**: Add `staleTime` to each `useQuery` call. A value of `30_000` (30 seconds) is reasonable — the server-provided data is considered fresh for 30 seconds, preventing an immediate refetch while still allowing background updates for longer page visits. Alternatively, use `Infinity` if the data should never auto-refetch (only refetch on explicit invalidation after mutations).

**Change each file as follows**:

- [x] Add `staleTime` to `useQuery` in **customer-list.tsx**
- [x] Add `staleTime` to `useQuery` in **estimation-list.tsx**
- [x] Add `staleTime` to `useQuery` in **insurance-list.tsx**
- [x] Add `staleTime` to `useQuery` in **real-estate-list.tsx**
- [x] Add `staleTime` to `useQuery` in **vehicle-list.tsx**

### File 1: `customer-list.tsx`

Find the `useQuery` call (around line 51). It currently looks like:
```typescript
const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["customers", pagination.pageIndex, pagination.pageSize, search, sortField, sortDirection],
    queryFn: () =>
      getCustomers(pagination.pageIndex, pagination.pageSize, search || undefined, sortField, sortDirection),
    initialData:
      pagination.pageIndex === 0 && !search && !sortField ? initialData : undefined,
  });
```

Add `staleTime`:
```typescript
const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["customers", pagination.pageIndex, pagination.pageSize, search, sortField, sortDirection],
    queryFn: () =>
      getCustomers(pagination.pageIndex, pagination.pageSize, search || undefined, sortField, sortDirection),
    initialData:
      pagination.pageIndex === 0 && !search && !sortField ? initialData : undefined,
    staleTime: 30_000, // SSR data is fresh for 30s — skip immediate refetch
  });
```

### Repeat the same change in:

2. **estimation-list.tsx** — `useQuery` with key `["estimations", ...]` — add `staleTime: 30_000`
3. **insurance-list.tsx** — `useQuery` with key `["insurances", ...]` — add `staleTime: 30_000`
4. **real-estate-list.tsx** — `useQuery` with key `["real-estate", ...]` — add `staleTime: 30_000`
5. **vehicle-list.tsx** — `useQuery` with key `["vehicles", ...]` — add `staleTime: 30_000`

The `useQuery` calls in each file have a similar structure — find the `useQuery({` call and add `staleTime: 30_000,` to the options object.

---

## Acceptance Criteria

- [ ] Navigate to `/dashboard/customers` — browser DevTools Network tab shows only 1 API call (the SSR fetch), not 2
- [ ] Navigate to `/dashboard/estimations` — same (1 call, not 2)
- [ ] Navigate to `/dashboard/insurances` — same
- [ ] Navigate to `/dashboard/real-estate` — same
- [ ] Navigate to `/dashboard/vehicles` — same
- [ ] Change page, sort, or filter on any list → a refetch IS triggered (because the queryKey changes, which bypasses staleTime)
- [ ] After creating/editing a record (e.g., new customer), navigating back to the list → fresh data IS fetched (because `queryClient.invalidateQueries` in the mutation's `onSuccess` invalidates the cache)

---

## Dependencies

- Requires that the SSR page components (`app/(dashboard)/*/page.tsx`) actually pass `initialData` prop. The `staleTime` fix is harmless even if `initialData` is undefined — it just prevents unnecessary refetches of stale-then data.
