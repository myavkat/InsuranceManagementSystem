# Plan: Fix Dead Sort Interactions (insurance-companies-list.tsx + insurance-types-list.tsx)

**Source:** Sprint 8 code review — finding 15

---

## Objective

Fix two list components where column headers show sort icons and appear interactive (`enableSorting: true`), but clicking them does nothing — the `onSortingChange` callback is a no-op. This creates a dead-interaction UX.

---

## Severity

🟡 **Low-Med** — UX. Sortable columns appear clickable but are silently non-functional.

---

## Files to Read First

| File | Why |
|------|-----|
| `frontend-next/src/components/features/insurances/insurance-companies-list.tsx` | First file to fix (86 lines) |
| `frontend-next/src/components/features/insurances/insurance-types-list.tsx` | Second file to fix (80 lines) |
| `frontend-next/src/components/features/data-table/data-table.tsx` | DataTable component — to understand `onSortingChange` prop contract |

---

## Steps

### Background

Both `InsuranceCompaniesList` and `InsuranceTypesList` fetch ALL data in a single query (no pagination, no server-side filtering — `getInsuranceCompanies()` and `getInsuranceTypes()` return arrays, not pages). They use the `DataTable` with hardcoded pagination state (`pageSize: 100`, `pageIndex: 0`) and no-op callbacks for `onPaginationChange`, `onSortingChange`, and `onGlobalFilterChange`.

The columns are defined with `enableSorting: true` (e.g., `Name` column in both components), but since the data is static (all records fetched at once), sorting should happen **client-side**. Currently, the `DataTable` has `manualSorting: true` which delegates sorting to the server. With a no-op `onSortingChange`, clicking a sortable column header does nothing.

### Approach

There are two valid fixes. Pick ONE.

---

### Option A — Enable client-side sorting (simplest)

Remove `enableSorting: true` from the column definitions. The sort icons won't render, so users won't click them. This eliminates the dead interaction.

**Change in `insurance-companies-list.tsx`**:

Find column definitions (~lines 14-24). Change:
```typescript
const columns: ColumnDef<InsuranceCompanyResponse, any>[] = [
  columnHelper.accessor("name", {
    header: "Name",
    cell: (info) => <span className="font-medium">{info.getValue()}</span>,
    enableSorting: true,
  }),
  // ...
];
```
Remove `enableSorting: true` from the `name` column.

**Change in `insurance-types-list.tsx`**:

Same — find the `name` column with `enableSorting: true` and remove it.

---

### Option B — Enable real client-side sorting (more functional)

Remove `manualSorting: true` from the DataTable OR implement actual client-side sort state management. Since the lists use a single `useQuery` that returns all data, client-side sorting is appropriate.

However, `manualSorting: true` is hardcoded inside `DataTable` (data-table.tsx line 116-118). To enable client-side sorting, the `DataTable` would need a `manualSorting` prop. Since that's a bigger change, Option A is preferred for now.

---

### Exact changes (Option A)

#### File 1: `insurance-companies-list.tsx`

Find the `columns` array. Currently:
```typescript
const columns: ColumnDef<InsuranceCompanyResponse, any>[] = [
  columnHelper.accessor("name", {
    header: "Name",
    cell: (info) => <span className="font-medium">{info.getValue()}</span>,
    enableSorting: true,
  }),
  columnHelper.accessor("rating", {
    header: "Rating",
    cell: (info) => info.getValue()?.toString() ?? "—",
  }),
  columnHelper.accessor("isActive", {
    header: "Status",
    cell: (info) => <StatusBadge status={info.getValue() ? "ACTIVE" : "INACTIVE"} />,
  }),
];
```

Change `enableSorting: true` to `enableSorting: false` or remove it entirely (default is false):
```typescript
const columns: ColumnDef<InsuranceCompanyResponse, any>[] = [
  columnHelper.accessor("name", {
    header: "Name",
    cell: (info) => <span className="font-medium">{info.getValue()}</span>,
  }),
  columnHelper.accessor("rating", {
    header: "Rating",
    cell: (info) => info.getValue()?.toString() ?? "—",
  }),
  columnHelper.accessor("isActive", {
    header: "Status",
    cell: (info) => <StatusBadge status={info.getValue() ? "ACTIVE" : "INACTIVE"} />,
  }),
];
```

#### File 2: `insurance-types-list.tsx`

Find the `columns` array. Currently:
```typescript
const columns: ColumnDef<InsuranceTypeResponse, any>[] = [
  columnHelper.accessor("id", {
    header: "ID",
    cell: (info) => info.getValue(),
  }),
  columnHelper.accessor("name", {
    header: "Name",
    cell: (info) => <span className="font-medium">{info.getValue()}</span>,
    enableSorting: true,
  }),
];
```

Remove `enableSorting: true` from the `name` column.

---

## Acceptance Criteria

- [ ] Insurance Companies List: no sort icons appear on column headers — no dead interactions
- [ ] Insurance Types List: no sort icons appear on column headers — no dead interactions
- [ ] Data tables still render correctly (no regression)
- [ ] CSV export still works (no regression — `enableCsvExport` is passed separately)

## Dependencies

None — this plan is self-contained.
