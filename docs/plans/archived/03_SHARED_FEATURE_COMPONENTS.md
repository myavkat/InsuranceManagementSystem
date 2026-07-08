# Plan: Shared Feature Components

## Objective

Build reusable feature-level components that will be shared across all domain pages. These components wrap shadcn/ui primitives with domain-specific behavior and consistent patterns for search, pagination, status display, empty states, error states, and form layouts.

## Prerequisites

- Plan `01_BFF_ROUTE_HANDLERS.md` must be completed (BFF routes proxy to Gateway)
- Plan `02_API_CLIENT_LIBRARY.md` must be completed (types and API functions available)

## Files to Read First

- `frontend-next/src/components/ui/button.tsx` — Button component API
- `frontend-next/src/components/ui/input.tsx` — Input component API
- `frontend-next/src/components/ui/table.tsx` — Table component API (Table, TableHeader, TableBody, TableRow, TableHead, TableCell)
- `frontend-next/src/components/ui/badge.tsx` — Badge component API (variant prop)
- `frontend-next/src/components/ui/skeleton.tsx` — Skeleton component API
- `frontend-next/src/components/ui/card.tsx` — Card component API
- `frontend-next/src/components/ui/dialog.tsx` — Dialog component API
- `frontend-next/src/components/ui/select.tsx` — Select component API
- `frontend-next/src/lib/utils.ts` — `cn()` helper
- `frontend-next/src/lib/api/types.ts` — `PageResponse<T>` type

## Steps

### Step 1: Create `SearchBar` component

Create file: `frontend-next/src/components/features/search-bar.tsx`

- [x] Created `search-bar.tsx` with debounced input and search icon

A controlled search input with debounce. Used at the top of list pages.

```typescript
"use client";

import { Input } from "@/components/ui/input";
import { Search } from "lucide-react";
import { useEffect, useState, useRef } from "react";

interface SearchBarProps {
  placeholder?: string;
  onSearch: (value: string) => void;
  debounceMs?: number;
  defaultValue?: string;
}

export function SearchBar({
  placeholder = "Search...",
  onSearch,
  debounceMs = 300,
  defaultValue = "",
}: SearchBarProps) {
  const [value, setValue] = useState(defaultValue);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    // Cleanup on unmount
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, []);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newValue = e.target.value;
    setValue(newValue);
    if (timerRef.current) clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => {
      onSearch(newValue);
    }, debounceMs);
  };

  return (
    <div className="relative w-full max-w-sm">
      <Search className="absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
      <Input
        placeholder={placeholder}
        value={value}
        onChange={handleChange}
        className="pl-8"
      />
    </div>
  );
}
```

### Step 2: Create `PaginationBar` component

Create file: `frontend-next/src/components/features/pagination-bar.tsx`

- [x] Created `pagination-bar.tsx` with Previous/Next, page info, and "Showing X–Y of Z" text

Page navigation with Previous/Next buttons and page info. Uses the `PageResponse` shape from `@/lib/api/types`.

```typescript
"use client";

import { Button } from "@/components/ui/button";
import { ChevronLeft, ChevronRight } from "lucide-react";

interface PaginationBarProps {
  currentPage: number;   // 0-indexed
  totalPages: number;
  totalElements: number;
  pageSize: number;
  onPageChange: (page: number) => void;
}

export function PaginationBar({
  currentPage,
  totalPages,
  totalElements,
  pageSize,
  onPageChange,
}: PaginationBarProps) {
  if (totalPages <= 1) return null;

  const from = currentPage * pageSize + 1;
  const to = Math.min((currentPage + 1) * pageSize, totalElements);

  return (
    <div className="flex items-center justify-between pt-4">
      <p className="text-sm text-muted-foreground">
        Showing {from}–{to} of {totalElements}
      </p>
      <div className="flex items-center gap-2">
        <Button
          variant="outline"
          size="sm"
          disabled={currentPage === 0}
          onClick={() => onPageChange(currentPage - 1)}
        >
          <ChevronLeft className="size-4" />
          Previous
        </Button>
        <span className="text-sm text-muted-foreground">
          Page {currentPage + 1} of {totalPages}
        </span>
        <Button
          variant="outline"
          size="sm"
          disabled={currentPage >= totalPages - 1}
          onClick={() => onPageChange(currentPage + 1)}
        >
          Next
          <ChevronRight className="size-4" />
        </Button>
      </div>
    </div>
  );
}
```

### Step 3: Create `StatusBadge` component

Create file: `frontend-next/src/components/features/status-badge.tsx`

- [x] Created `status-badge.tsx` mapping STARTED/COMPLETED/REJECTED/PENDING/ACTIVE/INACTIVE to badge variants

A badge that shows estimation status with appropriate color variant.

```typescript
import { Badge } from "@/components/ui/badge";

type StatusVariant = "default" | "secondary" | "destructive" | "outline";

interface StatusBadgeProps {
  status: string;
}

const statusMap: Record<string, { label: string; variant: StatusVariant }> = {
  STARTED:    { label: "Started",    variant: "secondary" },
  COMPLETED:  { label: "Completed",  variant: "default" },
  REJECTED:   { label: "Rejected",   variant: "destructive" },
  PENDING:    { label: "Pending",    variant: "secondary" },
  ACTIVE:     { label: "Active",     variant: "default" },
  INACTIVE:   { label: "Inactive",   variant: "outline" },
};

export function StatusBadge({ status }: StatusBadgeProps) {
  const config = statusMap[status] ?? { label: status, variant: "outline" as StatusVariant };
  return <Badge variant={config.variant}>{config.label}</Badge>;
}
```

### Step 4: Create `EmptyState` component

Create file: `frontend-next/src/components/features/empty-state.tsx`

- [x] Created `empty-state.tsx` with icon, title, description, and optional action slot

Shown when a list or search returns no results.

```typescript
import { type LucideIcon, Inbox } from "lucide-react";

interface EmptyStateProps {
  icon?: LucideIcon;
  title: string;
  description?: string;
  action?: React.ReactNode; // e.g., a "Create New" button
}

export function EmptyState({
  icon: Icon = Inbox,
  title,
  description,
  action,
}: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center justify-center py-16 text-center">
      <Icon className="size-12 text-muted-foreground/50" />
      <h3 className="mt-4 text-lg font-medium">{title}</h3>
      {description && (
        <p className="mt-1 text-sm text-muted-foreground max-w-sm">
          {description}
        </p>
      )}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}
```

### Step 5: Create `ErrorAlert` component

Create file: `frontend-next/src/components/features/error-alert.tsx`

- [x] Created `error-alert.tsx` with retry button

Shown when a page or query fails to load.

```typescript
import { AlertCircle } from "lucide-react";
import { Button } from "@/components/ui/button";

interface ErrorAlertProps {
  title?: string;
  message: string;
  onRetry?: () => void;
}

export function ErrorAlert({
  title = "Something went wrong",
  message,
  onRetry,
}: ErrorAlertProps) {
  return (
    <div className="flex flex-col items-center justify-center py-16 text-center">
      <AlertCircle className="size-12 text-destructive/70" />
      <h3 className="mt-4 text-lg font-medium">{title}</h3>
      <p className="mt-1 text-sm text-muted-foreground max-w-sm">{message}</p>
      {onRetry && (
        <Button variant="outline" onClick={onRetry} className="mt-4">
          Try again
        </Button>
      )}
    </div>
  );
}
```

### Step 6: Create `PageHeader` component

Create file: `frontend-next/src/components/features/page-header.tsx`

- [x] Created `page-header.tsx` with title, description, and optional action slot

Consistent page title + action button layout used at the top of every list/detail page.

```typescript
interface PageHeaderProps {
  title: string;
  description?: string;
  action?: React.ReactNode; // e.g., a "New Customer" button
}

export function PageHeader({ title, description, action }: PageHeaderProps) {
  return (
    <div className="flex items-center justify-between">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
        {description && (
          <p className="text-sm text-muted-foreground mt-1">{description}</p>
        )}
      </div>
      {action && <div>{action}</div>}
    </div>
  );
}
```

### Step 7: Create `ConfirmDialog` component

Create file: `frontend-next/src/components/features/confirm-dialog.tsx`

- [x] Created `confirm-dialog.tsx` wrapping Dialog with confirm/cancel buttons and loading state

A reusable confirmation dialog for destructive actions (delete, deactivate).

```typescript
"use client";

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";

interface ConfirmDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description: string;
  confirmLabel?: string;
  cancelLabel?: string;
  variant?: "destructive" | "default";
  onConfirm: () => void;
  loading?: boolean;
}

export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel = "Confirm",
  cancelLabel = "Cancel",
  variant = "destructive",
  onConfirm,
  loading = false,
}: ConfirmDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={loading}
          >
            {cancelLabel}
          </Button>
          <Button
            variant={variant}
            onClick={onConfirm}
            disabled={loading}
          >
            {loading ? "Loading..." : confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
```

### Step 8: Create `DataTableSkeleton` component

Create file: `frontend-next/src/components/features/data-table-skeleton.tsx`

- [x] Created `data-table-skeleton.tsx` with configurable columns and rows

A skeleton placeholder shown while table data loads.

```typescript
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

interface DataTableSkeletonProps {
  columns: number;
  rows?: number;
}

export function DataTableSkeleton({ columns, rows = 5 }: DataTableSkeletonProps) {
  return (
    <div className="rounded-md border">
      <Table>
        <TableHeader>
          <TableRow>
            {Array.from({ length: columns }).map((_, i) => (
              <TableHead key={i}>
                <Skeleton className="h-4 w-24" />
              </TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {Array.from({ length: rows }).map((_, rowIdx) => (
            <TableRow key={rowIdx}>
              {Array.from({ length: columns }).map((_, colIdx) => (
                <TableCell key={colIdx}>
                  <Skeleton className="h-4 w-full" />
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
```

### Step 9: Create `FormField` component

Create file: `frontend-next/src/components/features/form-field.tsx`

- [x] Created `form-field.tsx` with label, forwarded ref, and error display

A wrapper that pairs a label, input, and error message. Uses `react-hook-form`'s `register()` return.

```typescript
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";
import { type InputHTMLAttributes, forwardRef } from "react";

interface FormFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
  inputClassName?: string;
}

export const FormField = forwardRef<HTMLInputElement, FormFieldProps>(
  function FormField({ label, error, className, inputClassName, id, ...props }, ref) {
    const fieldId = id ?? props.name;
    return (
      <div className={cn("space-y-1.5", className)}>
        <label
          htmlFor={fieldId}
          className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70"
        >
          {label}
        </label>
        <Input
          ref={ref}
          id={fieldId}
          className={cn(error && "border-destructive", inputClassName)}
          aria-invalid={!!error}
          {...props}
        />
        {error && (
          <p className="text-sm text-destructive" role="alert">
            {error}
          </p>
        )}
      </div>
    );
  }
);
```

### Step 10: Verify the build compiles

Run: `cd frontend-next && npm run build`

Fix any TypeScript errors before marking this plan complete.

- [x] Build succeeds without TypeScript errors

## Acceptance Criteria

- [x] `search-bar.tsx` exists with debounced input and search icon
- [x] `pagination-bar.tsx` exists with Previous/Next, page info, and "Showing X–Y of Z" text
- [x] `status-badge.tsx` exists mapping STARTED/COMPLETED/REJECTED/PENDING/ACTIVE/INACTIVE to badge variants
- [x] `empty-state.tsx` exists with icon, title, description, and optional action slot
- [x] `error-alert.tsx` exists with retry button
- [x] `page-header.tsx` exists with title, description, and optional action slot
- [x] `confirm-dialog.tsx` exists wrapping Dialog with confirm/cancel buttons and loading state
- [x] `data-table-skeleton.tsx` exists with configurable columns and rows
- [x] `form-field.tsx` exists with label, forwarded ref, and error display
- [x] `npm run build` succeeds without TypeScript errors
- [x] All components use `"use client"` directive where they use hooks or event handlers
