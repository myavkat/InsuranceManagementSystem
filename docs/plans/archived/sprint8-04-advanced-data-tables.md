# Plan: Sprint 8 — Advanced Data Tables

**Plan ID:** `sprint8-04-advanced-data-tables`
**Priority:** 4 (mostly independent, but SSR from Plan 02 improves initial load)
**Prerequisite Plans:** None required, but `sprint8-02-ssr-data-fetching` provides the initialData pattern used here
**Blocks:** None

---

## Objective

Replace all current plain HTML tables with a reusable TanStack Table (`@tanstack/react-table`) wrapper component. Add server-side pagination, column sorting, multi-column filtering, row selection, CSV export, and responsive column visibility. This covers subtask 4 from `docs/tasks/11_SPRINT8_ADVANCED_FRONTEND.md`.

---

## Files to Read First

| File | Purpose |
|------|---------|
| `frontend-next/src/components/ui/table.tsx` | Current Table primitives (Table, TableHead, TableBody, TableRow, TableCell, TableHeader) |
| `frontend-next/src/components/features/customers/customer-list.tsx` | Current list using plain HTML table — reference for columns and data shape |
| `frontend-next/src/components/features/vehicles/vehicle-list.tsx` | Vehicle list — reference for columns |
| `frontend-next/src/components/features/real-estate/real-estate-list.tsx` | Real estate list — reference for columns |
| `frontend-next/src/components/features/insurances/insurance-list.tsx` | Insurance list — reference for columns |
| `frontend-next/src/components/features/estimations/estimation-list.tsx` | Estimation list — reference for columns |
| `frontend-next/src/components/features/data-table-skeleton.tsx` | Current skeleton for table loading |
| `frontend-next/src/components/features/pagination-bar.tsx` | Current pagination component |
| `frontend-next/src/components/features/search-bar.tsx` | Current search bar (client-side debounced) |
| `frontend-next/src/lib/api/types.ts` | PageResponse<T> interface |
| `frontend-next/src/lib/api/customers.ts` | CustomerResponse type — column definitions |
| `frontend-next/package.json` | Dependencies (NOTE: @tanstack/react-table is NOT installed) |

---

## Technical Context

### TanStack Table v8 (React Table)
- Package: `@tanstack/react-table` (NOT `@tanstack/react-table v9` which is alpha)
- Import: `@tanstack/react-table` — core library
- Key concepts:
  - `useReactTable` — the main hook, returns table instance
  - `createColumnHelper` — type-safe column builder
  - `getCoreRowModel()` — required for basic table functionality
  - `getPaginationRowModel()` — client-side pagination (we use SERVER-side, so skip)
  - `getSortedRowModel()` — client-side sorting (we use SERVER-side)
  - `getFilteredRowModel()` — client-side filtering (we use SERVER-side for global search)
  - `flexRender()` — renders a cell or header based on its definition

### Server-Side Mode
Since we do server-side pagination/sorting/filtering:
- Pagination state is managed externally (via `useState` for page/size, passed to API)
- Sorting state is managed externally (sort field + direction, passed to API as query params)
- Filtering state is managed externally (search string, passed to API)
- The table instance receives: `manualPagination: true`, `manualSorting: true`, `manualFiltering: true`
- API functions accept `page`, `size`, `sort`, `direction`, `search` params

### Styling
- The DataTable wrapper MUST use the existing `@/components/ui/table.tsx` primitives for visual consistency
- Tailwind CSS v4 with shadcn "base-nova" tokens
- Use `cn()` from `@/lib/utils` for conditional classes

### CSV Export
- No library needed — build a simple CSV serializer
- Trigger via a download button with a `Blob` URL
- Handle special characters (commas, quotes, newlines) in cell values

---

## Steps

### Step 1: Install TanStack Table

- [x] Open terminal in `frontend-next/`
- [x] Run: `npm install @tanstack/react-table`
- [x] Verify it's in `package.json` dependencies after install
- [x] **IMPORTANT**: Installed version is v8.21.3 (v8.x)

### Step 2: Create reusable DataTable component

- [x] Create directory `frontend-next/src/components/features/data-table/`
- [x] Create `frontend-next/src/components/features/data-table/data-table.tsx`:

  ```tsx
  "use client";

  import { useState, type ReactNode } from "react";
  import {
    type ColumnDef,
    type SortingState,
    type ColumnFiltersState,
    type VisibilityState,
    type RowSelectionState,
    flexRender,
    getCoreRowModel,
    useReactTable,
  } from "@tanstack/react-table";
  import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
  } from "@/components/ui/table";
  import { Button } from "@/components/ui/button";
  import { Input } from "@/components/ui/input";
  import {
    ChevronLeft,
    ChevronRight,
    ArrowUpDown,
    ArrowUp,
    ArrowDown,
    Download,
    Columns3,
  } from "lucide-react";
  import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
  } from "@/components/ui/select";
  import { cn } from "@/lib/utils";

  // --- Types ---

  export interface DataTablePaginationState {
    pageIndex: number;
    pageSize: number;
  }

  export interface DataTableSortingState {
    id: string;
    desc: boolean;
  }

  // Props shared by all table features
  interface DataTableProps<TData, TValue> {
    // Core table config
    columns: ColumnDef<TData, TValue>[];
    data: TData[];

    // Server-side state
    pageCount: number;
    totalElements: number;
    pagination: DataTablePaginationState;
    sorting: DataTableSortingState[];
    globalFilter: string;

    // Callbacks — the parent manages state, the table notifies on changes
    onPaginationChange: (pagination: DataTablePaginationState) => void;
    onSortingChange: (sorting: DataTableSortingState[]) => void;
    onGlobalFilterChange: (filter: string) => void;

    // Optional: enable row selection
    enableRowSelection?: boolean;
    onRowSelectionChange?: (selection: RowSelectionState) => void;

    // Optional: row click handler
    onRowClick?: (row: TData) => void;

    // Optional: CSV export
    enableCsvExport?: boolean;
    csvFileName?: string;

    // Optional: loading state
    isLoading?: boolean;

    // Optional: toolbar content (buttons, filters) injected above the table
    toolbar?: ReactNode;
  }

  // --- Component ---

  export function DataTable<TData, TValue>({
    columns,
    data,
    pageCount,
    totalElements,
    pagination,
    sorting,
    globalFilter,
    onPaginationChange,
    onSortingChange,
    onGlobalFilterChange,
    enableRowSelection = false,
    onRowSelectionChange,
    onRowClick,
    enableCsvExport = false,
    csvFileName = "export.csv",
    isLoading = false,
    toolbar,
  }: DataTableProps<TData, TValue>) {
    const [columnVisibility, setColumnVisibility] = useState<VisibilityState>({});
    const [rowSelection, setRowSelection] = useState<RowSelectionState>({});

    const table = useReactTable({
      data,
      columns,
      getCoreRowModel: getCoreRowModel(),
      // Server-side: tell the table we manage these externally
      manualPagination: true,
      manualSorting: true,
      manualFiltering: true,
      pageCount,
      state: {
        pagination: {
          pageIndex: pagination.pageIndex,
          pageSize: pagination.pageSize,
        },
        sorting,
        globalFilter,
        columnVisibility,
        rowSelection,
      },
      onPaginationChange: (updater) => {
        if (typeof updater === "function") {
          const current = { pageIndex: pagination.pageIndex, pageSize: pagination.pageSize };
          const next = updater(current);
          onPaginationChange({ pageIndex: next.pageIndex, pageSize: next.pageSize });
        } else {
          onPaginationChange({ pageIndex: updater.pageIndex, pageSize: updater.pageSize });
        }
      },
      onSortingChange: (updater) => {
        const next = typeof updater === "function" ? updater(sorting) : updater;
        onSortingChange(next);
      },
      onGlobalFilterChange,
      onColumnVisibilityChange: setColumnVisibility,
      onRowSelectionChange: (updater) => {
        const next = typeof updater === "function" ? updater(rowSelection) : updater;
        setRowSelection(next);
        onRowSelectionChange?.(next);
      },
      enableRowSelection,
    });

    // --- CSV Export ---
    const handleCsvExport = () => {
      // Get visible columns (exclude selection column)
      const visibleColumns = table.getVisibleLeafColumns().filter(
        (col) => col.id !== "select"
      );

      // Header row
      const headerRow = visibleColumns
        .map((col) => {
          const header = col.columnDef.header;
          return typeof header === "string" ? header : col.id;
        })
        .map(escapeCsvField)
        .join(",");

      // Data rows
      const dataRows = table.getRowModel().rows.map((row) =>
        visibleColumns
          .map((col) => {
            const value = row.getValue(col.id);
            return escapeCsvField(value != null ? String(value) : "");
          })
          .join(",")
      );

      const csv = [headerRow, ...dataRows].join("\n");
      const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = csvFileName;
      link.click();
      URL.revokeObjectURL(url);
    };

    const from = pagination.pageIndex * pagination.pageSize + 1;
    const to = Math.min((pagination.pageIndex + 1) * pagination.pageSize, totalElements);

    return (
      <div className="space-y-4">
        {/* Toolbar */}
        <div className="flex items-center gap-2 flex-wrap">
          {toolbar}
          {enableCsvExport && (
            <Button variant="outline" size="sm" onClick={handleCsvExport} className="ml-auto">
              <Download className="size-4" />
              Export CSV
            </Button>
          )}
        </div>

        {/* Table */}
        <div className="rounded-md border">
          <Table>
            <TableHeader>
              {table.getHeaderGroups().map((headerGroup) => (
                <TableRow key={headerGroup.id}>
                  {headerGroup.headers.map((header) => (
                    <TableHead
                      key={header.id}
                      className={cn(
                        header.column.getCanSort() && "cursor-pointer select-none"
                      )}
                      onClick={header.column.getCanSort()
                        ? header.column.getToggleSortingHandler()
                        : undefined
                      }
                    >
                      <div className="flex items-center gap-1">
                        {flexRender(header.column.columnDef.header, header.getContext())}
                        {header.column.getCanSort() && (
                          <SortIcon sorted={header.column.getIsSorted()} />
                        )}
                      </div>
                    </TableHead>
                  ))}
                </TableRow>
              ))}
            </TableHeader>
            <TableBody>
              {isLoading ? (
                // Loading skeleton rows
                Array.from({ length: pagination.pageSize }).map((_, i) => (
                  <TableRow key={`skeleton-${i}`}>
                    {columns.map((_, j) => (
                      <TableCell key={j}>
                        <div className="h-4 w-full animate-pulse rounded bg-muted" />
                      </TableCell>
                    ))}
                  </TableRow>
                ))
              ) : table.getRowModel().rows.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={columns.length} className="h-24 text-center">
                    No results found.
                  </TableCell>
                </TableRow>
              ) : (
                table.getRowModel().rows.map((row) => (
                  <TableRow
                    key={row.id}
                    data-state={row.getIsSelected() && "selected"}
                    className={cn(onRowClick && "cursor-pointer")}
                    onClick={() => onRowClick?.(row.original)}
                  >
                    {row.getVisibleCells().map((cell) => (
                      <TableCell key={cell.id}>
                        {flexRender(cell.column.columnDef.cell, cell.getContext())}
                      </TableCell>
                    ))}
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </div>

        {/* Pagination */}
        <div className="flex items-center justify-between">
          <p className="text-sm text-muted-foreground">
            {totalElements > 0
              ? `Showing ${from}–${to} of ${totalElements}`
              : "No results"}
          </p>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={pagination.pageIndex === 0}
              onClick={() =>
                onPaginationChange({
                  pageIndex: pagination.pageIndex - 1,
                  pageSize: pagination.pageSize,
                })
              }
            >
              <ChevronLeft className="size-4" />
              Previous
            </Button>
            <span className="text-sm text-muted-foreground">
              Page {pagination.pageIndex + 1} of {pageCount || 1}
            </span>
            <Button
              variant="outline"
              size="sm"
              disabled={pagination.pageIndex >= pageCount - 1}
              onClick={() =>
                onPaginationChange({
                  pageIndex: pagination.pageIndex + 1,
                  pageSize: pagination.pageSize,
                })
              }
            >
              Next
              <ChevronRight className="size-4" />
            </Button>
            {/* Page size selector */}
            <Select
              value={String(pagination.pageSize)}
              onValueChange={(value) =>
                onPaginationChange({
                  pageIndex: 0,
                  pageSize: Number(value),
                })
              }
            >
              <SelectTrigger className="w-20">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {[10, 20, 50, 100].map((size) => (
                  <SelectItem key={size} value={String(size)}>
                    {size}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>
      </div>
    );
  }

  // --- Helpers ---

  function SortIcon({ sorted }: { sorted: false | "asc" | "desc" }) {
    if (sorted === "asc") return <ArrowUp className="size-4 text-foreground" />;
    if (sorted === "desc") return <ArrowDown className="size-4 text-foreground" />;
    return <ArrowUpDown className="size-4 text-muted-foreground/40" />;
  }

  function escapeCsvField(value: string): string {
    if (value.includes(",") || value.includes('"') || value.includes("\n")) {
      return `"${value.replace(/"/g, '""')}"`;
    }
    return value;
  }
  ```

### Step 3: Create column helpers for reusability

- [x] Create `frontend-next/src/components/features/data-table/column-helpers.ts`:
  ```typescript
  import { createColumnHelper } from "@tanstack/react-table";
  import { format } from "date-fns"; // only if date-fns is installed; check package.json

  // If date-fns is NOT installed, use a simple format function:
  export function formatDate(dateStr: string): string {
    if (!dateStr) return "—";
    try {
      return new Date(dateStr).toLocaleDateString("en-US", {
        year: "numeric",
        month: "short",
        day: "numeric",
      });
    } catch {
      return dateStr;
    }
  }

  // Generic column helper factory
  export function createColumns<T>() {
    return createColumnHelper<T>();
  }
  ```

### Step 4: Migrate the Customers list to use DataTable

- [x] Open `frontend-next/src/components/features/customers/customer-list.tsx`
- [x] The current component manages `page` and `search` state, passes them to `useQuery`, renders a plain `<Table>` manually.
- [x] Replace the component logic:
  1. Keep `page` and `search` state (these become `pagination.pageIndex` and `globalFilter`)
  2. Add `sorting` state: `const [sorting, setSorting] = useState<DataTableSortingState[]>([]);`
  3. Update the `useQuery` call to include sort params in the query key and API call
  4. Replace the `<Table>...</Table>` JSX with `<DataTable>` component
  5. Define columns using `createColumnHelper<CustomerResponse>()`

- [x] **Full replacement logic for `customer-list.tsx`**:
  ```tsx
  "use client";

  import { useState } from "react";
  import { useQuery } from "@tanstack/react-query";
  import { useRouter } from "next/navigation";
  import { createColumnHelper } from "@tanstack/react-table";
  import { getCustomers, type CustomerResponse } from "@/lib/api/customers";
  import { Button } from "@/components/ui/button";
  import { SearchBar } from "@/components/features/search-bar";
  import { EmptyState } from "@/components/features/empty-state";
  import { ErrorAlert } from "@/components/features/error-alert";
  import { PageHeader } from "@/components/features/page-header";
  import {
    DataTable,
    type DataTablePaginationState,
    type DataTableSortingState,
  } from "@/components/features/data-table/data-table";
  import { formatDate } from "@/components/features/data-table/column-helpers";
  import { Users, Plus } from "lucide-react";
  import type { PageResponse } from "@/lib/api/types";

  const columnHelper = createColumnHelper<CustomerResponse>();

  const columns = [
    columnHelper.accessor("firstName", {
      header: "First Name",
      cell: (info) => (
        <span className="font-medium">
          {info.getValue()} {info.row.original.lastName}
        </span>
      ),
      enableSorting: true,
    }),
    columnHelper.accessor("lastName", {
      header: "Last Name",
      enableSorting: true,
    }),
    columnHelper.accessor("nationalId", {
      header: "National ID",
      enableSorting: true,
    }),
    columnHelper.accessor("email", {
      header: "Email",
      enableSorting: true,
    }),
    columnHelper.accessor("phone", {
      header: "Phone",
      cell: (info) => info.getValue() ?? "—",
    }),
    columnHelper.accessor("cityName", {
      header: "City",
      cell: (info) => info.getValue() ?? "—",
    }),
    columnHelper.accessor("createdAt", {
      header: "Created",
      cell: (info) => formatDate(info.getValue()),
      enableSorting: true,
    }),
  ];

  interface CustomerListProps {
    initialData?: PageResponse<CustomerResponse>;
  }

  export function CustomerList({ initialData }: CustomerListProps) {
    const router = useRouter();
    const [pagination, setPagination] = useState<DataTablePaginationState>({
      pageIndex: 0,
      pageSize: 20,
    });
    const [sorting, setSorting] = useState<DataTableSortingState[]>([]);
    const [search, setSearch] = useState("");

    const sortField = sorting[0]?.id;
    const sortDirection = sorting[0]?.desc ? "desc" : "asc";

    const { data, isLoading, isError, error, refetch } = useQuery({
      queryKey: ["customers", pagination.pageIndex, pagination.pageSize, search, sortField, sortDirection],
      queryFn: () =>
        getCustomers(pagination.pageIndex, pagination.pageSize, search || undefined, sortField, sortDirection),
      initialData:
        pagination.pageIndex === 0 && !search && !sortField ? initialData : undefined,
    });

    if (isError) {
      return (
        <ErrorAlert
          message={error instanceof Error ? error.message : "Failed to load customers"}
          onRetry={() => refetch()}
        />
      );
    }

    const customers = data?.content ?? [];

    return (
      <div className="space-y-6">
        <PageHeader
          title="Customers"
          description="Manage customer records"
          action={
            <Button onClick={() => router.push("/customers/new")}>
              <Plus className="size-4" />
              New Customer
            </Button>
          }
        />

        {!isLoading && customers.length === 0 && !search ? (
          <EmptyState
            icon={Users}
            title="No customers found"
            description="Get started by creating a new customer."
            action={
              <Button onClick={() => router.push("/customers/new")}>
                <Plus className="size-4" />
                New Customer
              </Button>
            }
          />
        ) : (
          <DataTable
            columns={columns}
            data={customers}
            pageCount={data?.totalPages ?? 1}
            totalElements={data?.totalElements ?? 0}
            pagination={pagination}
            sorting={sorting}
            globalFilter={search}
            onPaginationChange={setPagination}
            onSortingChange={setSorting}
            onGlobalFilterChange={(value) => {
              setSearch(value);
              setPagination({ pageIndex: 0, pageSize: pagination.pageSize });
            }}
            onRowClick={(customer) => router.push(`/customers/${customer.id}`)}
            enableCsvExport
            csvFileName="customers.csv"
            isLoading={isLoading}
            toolbar={
              <SearchBar
                placeholder="Search by name or national ID..."
                onSearch={(value) => {
                  setSearch(value);
                  setPagination({ pageIndex: 0, pageSize: pagination.pageSize });
                }}
              />
            }
          />
        )}
      </div>
    );
  }
  ```

### Step 5: Update API functions to accept sort parameters

- [x] Open `frontend-next/src/lib/api/customers.ts`
- [x] Update `getCustomers` to accept sort params:
  ```typescript
  export async function getCustomers(
    page = 0,
    size = 20,
    search?: string,
    sort?: string,
    direction?: string,
  ): Promise<PageResponse<CustomerResponse>> {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (search) params.set("search", search);
    if (sort) params.set("sort", sort);
    if (direction) params.set("direction", direction);
    return apiClient<PageResponse<CustomerResponse>>(
      `/api/customers?${params.toString()}`
    );
  }
  ```
- [x] Repeat for all other domain API files:
  - [x] `frontend-next/src/lib/api/vehicles.ts` — `getVehicles(page, size, search?, sort?, direction?)`
  - [x] `frontend-next/src/lib/api/realestate.ts` — `getRealEstates(page, size, search?, sort?, direction?)`
  - [x] `frontend-next/src/lib/api/insurances.ts` — check existing function signatures, add sort/direction
  - [x] `frontend-next/src/lib/api/estimations.ts` — check existing function signatures, add sort/direction

### Step 6: Migrate remaining list pages to use DataTable

Repeat the pattern from Step 4 for each remaining list component. The core pattern is the same:

- [x] **Vehicles**: Open `frontend-next/src/components/features/vehicles/vehicle-list.tsx`
  - Define columns for: plate, brand, model, modelYear, customer, createdAt
  - Enable CSV export: `csvFileName="vehicles.csv"`
  - Row click navigates to `/vehicles/${vehicle.id}`

- [x] **Real Estate**: Open `frontend-next/src/components/features/real-estate/real-estate-list.tsx`
  - Define columns for: address, type, area, deedNo, customer, createdAt
  - Enable CSV export: `csvFileName="real-estate.csv"`
  - Row click navigates to `/real-estate/${item.id}`

- [x] **Insurances**: Open `frontend-next/src/components/features/insurances/insurance-list.tsx`
  - Define columns for: type name, description, status, createdAt
  - Enable CSV export: `csvFileName="insurances.csv"`
  - Row click navigates to `/insurances/${item.id}`

- [x] **Estimations**: Open `frontend-next/src/components/features/estimations/estimation-list.tsx`
  - Define columns for: estimation ID, customer name, insurance type, status, createdAt
  - Enable CSV export: `csvFileName="estimations.csv"`
  - Row click navigates to `/estimations/${item.id}`

- [x] **Insurance Types** (sub-page): Open `frontend-next/src/components/features/insurances/insurance-types-list.tsx`
  - Migrate if it uses a table

- [x] **Insurance Companies** (sub-page): Open `frontend-next/src/components/features/insurances/insurance-companies-list.tsx`
  - Migrate if it uses a table

### Step 7: Update page components to pass initialData

- [x] After the DataTable migration, update each Server Component page (from Plan 02) to pass `initialData` to the migrated list component
- [x] Verify that `initialData` in the Server Component page matches the `PageResponse<X>` type expected by the list

### Step 8: Add responsive column visibility

- [x] In each list component, add default column visibility for mobile:
  ```typescript
  const [columnVisibility, setColumnVisibility] = useState<VisibilityState>({});
  ```
  But this is handled inside the DataTable component already in Step 2.
- [x] In each column definition, optionally add `enableHiding: true` to allow hiding via the UI
- [x] For mobile responsiveness, the DataTable wraps in `<div className="rounded-md border">` and the Table component already has `overflow-x-auto` — this provides horizontal scroll on small screens, which is the simplest responsive pattern

### Step 9: Verify

- [x] Run `npx tsc --noEmit` from `frontend-next/` to check for TypeScript errors
- [x] Verify the DataTable renders with sortable column headers (click to sort)
- [x] Verify pagination works (Next/Previous buttons, page size selector)
- [x] Verify search filter resets pagination to page 0
- [x] Verify CSV export downloads a valid CSV file
- [x] Verify row click navigation works
- [x] Verify loading state shows skeleton rows
- [x] Verify empty state shows "No results found"

---

## Acceptance Criteria

1. `@tanstack/react-table` installed (v8.x)
2. Reusable `DataTable` component created at `frontend-next/src/components/features/data-table/data-table.tsx`
3. Column helper utilities at `frontend-next/src/components/features/data-table/column-helpers.ts`
4. All 5+ list pages migrated to use `DataTable`:
   - CustomerList
   - VehicleList
   - RealEstateList
   - InsuranceList
   - EstimationList
   - InsuranceTypesList (if applicable)
   - InsuranceCompaniesList (if applicable)
5. All API functions updated to accept `sort` and `direction` parameters
6. Server-side pagination, sorting, and filtering work across all tables
7. CSV export functional on all tables
8. Loading state shows skeleton rows during data fetch
9. Empty state handled inside DataTable component
10. No TypeScript errors
11. Responsive: horizontal scroll works on mobile viewports (test at 375px width)

---

## Common Mistakes to Avoid

- **DO NOT** use `@tanstack/react-table` v9 alpha API — the stable version is v8
- **DO NOT** use client-side pagination (`getPaginationRowModel()`) — we do server-side
- **DO NOT** use client-side sorting (`getSortedRowModel()`) — we do server-side
- **DO NOT** forget to pass `manualPagination: true`, `manualSorting: true`, `manualFiltering: true` to `useReactTable` — otherwise the table will try to handle them client-side
- **DO NOT** mutate the data array directly — `onPaginationChange` and `onSortingChange` should call the parent's setState
- **DO NOT** forget to reset `pageIndex` to 0 when search filter or page size changes
- **DO NOT** use `flexRender` directly on cells — always go through `header.getContext()` or `cell.getContext()`
- **DO NOT** define columns inside the component — define them at module level to avoid re-creation on every render
- **DO NOT** use `<a>` for download — use `Blob` + `URL.createObjectURL` + programmatic click
- **DO NOT** remove the `"use client"` directive from the DataTable — it uses hooks
- **DO NOT** import from `@radix-ui/*` — use `@/components/ui/*` which wraps `@base-ui/react`
