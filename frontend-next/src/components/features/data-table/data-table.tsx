"use client";

import { useState, type ReactNode } from "react";
import {
  type ColumnDef,
  type SortingState,
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
import {
  ChevronLeft,
  ChevronRight,
  ArrowUpDown,
  ArrowUp,
  ArrowDown,
  Download,
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

      {/* Pagination — only show when more than one page */}
      {pageCount > 1 && (
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
      )}
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
