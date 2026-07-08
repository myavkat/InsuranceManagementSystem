# Plan: Customer Management Pages

## Objective

Build the Customer Management feature pages in the Next.js frontend:
- `/customers` — paginated table with search
- `/customers/new` — create form
- `/customers/[id]` — detail view with linked vehicles and estimation history
- `/customers/[id]/edit` — edit form

## Prerequisites

- Plan `01_BFF_ROUTE_HANDLERS.md` (BFF routes proxy to Gateway)
- Plan `02_API_CLIENT_LIBRARY.md` (customer types and API functions available)
- Plan `03_SHARED_FEATURE_COMPONENTS.md` (shared components available)

## Files to Read First

- `docs/stories/02_CUSTOMER_MANAGEMENT.md` — Customer scenarios and acceptance criteria
- `docs/outlines/05_NEXTJS_FRONTEND.md` — BFF pattern, Server Components by default
- `frontend-next/src/lib/api/customers.ts` — `CustomerResponse`, `CustomerRequest`, API functions
- `frontend-next/src/lib/api/reference-data.ts` — `getCities()`, `getProfessions()`
- `frontend-next/src/lib/api/vehicles.ts` — `VehicleResponse` (for linked vehicles on detail page)
- `frontend-next/src/lib/api/estimations.ts` — `EstimationResponse` (for estimation history on detail page)
- `frontend-next/src/components/features/` — All shared components created in Plan 03
- `frontend-next/src/components/ui/button.tsx`
- `frontend-next/src/components/ui/input.tsx`
- `frontend-next/src/components/ui/table.tsx`
- `frontend-next/src/components/ui/card.tsx`
- `frontend-next/src/components/ui/select.tsx`
- `frontend-next/src/components/ui/skeleton.tsx`
- `frontend-next/src/app/(dashboard)/dashboard/page.tsx` — Example of a page under dashboard layout
- `frontend-next/src/app/(dashboard)/layout.tsx` — Dashboard layout structure

## Context

All pages live under the dashboard layout at `src/app/(dashboard)/`. They automatically get the Sidebar + Header chrome.

**Server Components by default:** List and detail pages are Server Components that fetch data during SSR. Client Components (`"use client"`) are used only for interactive elements: forms, search, pagination.

**Data fetching pattern for Server Components:**
```typescript
// In a Server Component page:
import { getCustomers } from "@/lib/api/customers";

export default async function CustomersPage() {
  const data = await getCustomers(0, 20);
  // render with data
}
```

Wait — `getCustomers()` calls `apiClient()` which reads `useAuthStore.getState()` (Zustand). This works in Server Components because `apiClient` is called from the server but it won't have auth tokens during SSR unless the BFF handles auth forwarding.

**Correction:** Server Components should fetch through the BFF route handlers (`/api/customers`), not call `apiClient()` directly (which calls the Gateway from the server without auth). Instead, use Next.js `fetch()` with relative URLs:

```typescript
// In a Server Component:
const res = await fetch(`${process.env.NEXT_PUBLIC_GATEWAY_URL}/api/customers?page=0&size=20`);
// OR use the BFF:
const res = await fetch(`http://localhost:3000/api/customers?page=0&size=20`);
```

**Simpler approach used in this plan:** All pages that fetch data are Client Components using React Query. This aligns with the task requirement: "Client components using React Query for data fetching, mutations, cache invalidation." Server Components that need data become thin wrappers around Client Components that do the actual fetching.

**Pattern to follow:**
- Page file (Server Component): renders metadata, wraps the Client Component
- Feature component file (Client Component): uses `useQuery`/`useMutation` from React Query

## Steps

### Step 1: Create directory structure

Create these directories:
```
frontend-next/src/app/(dashboard)/customers/
frontend-next/src/app/(dashboard)/customers/new/
frontend-next/src/app/(dashboard)/customers/[id]/
frontend-next/src/app/(dashboard)/customers/[id]/edit/
frontend-next/src/components/features/customers/
```

### Step 2: Build Customer List page

#### 2a. Create the list client component

Create: `frontend-next/src/components/features/customers/customer-list.tsx`

```typescript
"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { getCustomers } from "@/lib/api/customers";
import { Button } from "@/components/ui/button";
import { SearchBar } from "@/components/features/search-bar";
import { PaginationBar } from "@/components/features/pagination-bar";
import { DataTableSkeleton } from "@/components/features/data-table-skeleton";
import { EmptyState } from "@/components/features/empty-state";
import { ErrorAlert } from "@/components/features/error-alert";
import { PageHeader } from "@/components/features/page-header";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table";
import { Users, Plus } from "lucide-react";

export function CustomerList() {
  const router = useRouter();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState("");
  const pageSize = 20;

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["customers", page, search],
    queryFn: () => getCustomers(page, pageSize, search || undefined),
  });

  if (isLoading) return <DataTableSkeleton columns={6} />;
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

      <SearchBar
        placeholder="Search by name or national ID..."
        onSearch={(value) => {
          setSearch(value);
          setPage(0);
        }}
      />

      {customers.length === 0 ? (
        <EmptyState
          icon={Users}
          title="No customers found"
          description={search ? "Try adjusting your search." : "Get started by creating a new customer."}
          action={
            !search && (
              <Button onClick={() => router.push("/customers/new")}>
                <Plus className="size-4" />
                New Customer
              </Button>
            )
          }
        />
      ) : (
        <>
          <div className="rounded-md border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>National ID</TableHead>
                  <TableHead>Email</TableHead>
                  <TableHead>Phone</TableHead>
                  <TableHead>City</TableHead>
                  <TableHead>Created</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {customers.map((customer) => (
                  <TableRow
                    key={customer.id}
                    className="cursor-pointer hover:bg-muted/50"
                    onClick={() => router.push(`/customers/${customer.id}`)}
                  >
                    <TableCell className="font-medium">
                      {customer.firstName} {customer.lastName}
                    </TableCell>
                    <TableCell>{customer.nationalId}</TableCell>
                    <TableCell>{customer.email}</TableCell>
                    <TableCell>{customer.phone ?? "—"}</TableCell>
                    <TableCell>{customer.cityName ?? "—"}</TableCell>
                    <TableCell>
                      {new Date(customer.createdAt).toLocaleDateString()}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <PaginationBar
            currentPage={page}
            totalPages={data?.totalPages ?? 1}
            totalElements={data?.totalElements ?? 0}
            pageSize={pageSize}
            onPageChange={setPage}
          />
        </>
      )}
    </div>
  );
}
```

#### 2b. Create the page

Create: `frontend-next/src/app/(dashboard)/customers/page.tsx`

```typescript
import type { Metadata } from "next";
import { CustomerList } from "@/components/features/customers/customer-list";

export const metadata: Metadata = {
  title: "Customers",
};

export default function CustomersPage() {
  return <CustomerList />;
}
```

### Step 3: Build Customer Detail page

#### 3a. Create the detail client component

Create: `frontend-next/src/components/features/customers/customer-detail.tsx`

This component shows:
- Customer personal info in a Card
- Linked vehicles (fetched from vehicles API — for now show placeholder or empty state)
- Estimation history (fetched from estimations API — for now show placeholder or empty state)
- Edit and Delete action buttons

Use React Query to fetch the customer by ID. Use `useQuery` for the customer data, linked vehicles, and estimation history.

Important details:
- Show a "Back to Customers" link at the top
- Use `PageHeader` with customer name as title
- Show `Skeleton` while loading (not `DataTableSkeleton` — this is a detail page)
- Show `ErrorAlert` on failure with retry
- Show `ConfirmDialog` before delete (soft-delete)
- The delete button calls `deleteCustomer(id)` then redirects to `/customers`

```typescript
"use client";

import { useParams, useRouter } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { getCustomer, deleteCustomer } from "@/lib/api/customers";
import { PageHeader } from "@/components/features/page-header";
import { ErrorAlert } from "@/components/features/error-alert";
import { ConfirmDialog } from "@/components/features/confirm-dialog";
import { Skeleton } from "@/components/ui/skeleton";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { ArrowLeft, Pencil, Trash2 } from "lucide-react";
import { useState } from "react";

export function CustomerDetail() {
  const params = useParams();
  const router = useRouter();
  const queryClient = useQueryClient();
  const id = params.id as string;
  const [deleteOpen, setDeleteOpen] = useState(false);

  const { data: customer, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["customer", id],
    queryFn: () => getCustomer(id),
  });

  const deleteMutation = useMutation({
    mutationFn: () => deleteCustomer(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["customers"] });
      router.push("/customers");
    },
  });

  if (isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-8 w-64" />
        <Card>
          <CardContent className="space-y-4 pt-6">
            {Array.from({ length: 8 }).map((_, i) => (
              <Skeleton key={i} className="h-5 w-full" />
            ))}
          </CardContent>
        </Card>
      </div>
    );
  }

  if (isError || !customer) {
    return (
      <ErrorAlert
        message={error instanceof Error ? error.message : "Failed to load customer"}
        onRetry={() => refetch()}
      />
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" onClick={() => router.push("/customers")}>
          <ArrowLeft className="size-4" />
        </Button>
        <PageHeader
          title={`${customer.firstName} ${customer.lastName}`}
          description="Customer details"
          action={
            <div className="flex gap-2">
              <Button variant="outline" onClick={() => router.push(`/customers/${id}/edit`)}>
                <Pencil className="size-4" />
                Edit
              </Button>
              <Button variant="destructive" onClick={() => setDeleteOpen(true)}>
                <Trash2 className="size-4" />
                Delete
              </Button>
            </div>
          }
        />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Personal Information</CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <DetailItem label="First Name" value={customer.firstName} />
            <DetailItem label="Last Name" value={customer.lastName} />
            <DetailItem label="National ID (TCKN)" value={customer.nationalId} />
            <DetailItem label="Email" value={customer.email} />
            <DetailItem label="Phone" value={customer.phone ?? "—"} />
            <DetailItem label="Birth Date" value={customer.birthDate ? new Date(customer.birthDate).toLocaleDateString() : "—"} />
            <DetailItem label="City" value={customer.cityName ?? "—"} />
            <DetailItem label="Profession" value={customer.professionName ?? "—"} />
            <DetailItem label="Address" value={customer.address ?? "—"} className="sm:col-span-2" />
          </dl>
        </CardContent>
      </Card>

      {/* Linked Vehicles — placeholder for now */}
      <Card>
        <CardHeader>
          <CardTitle>Linked Vehicles</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">
            Vehicle linkage will be available when vehicle pages are built.
          </p>
        </CardContent>
      </Card>

      {/* Estimation History — placeholder for now */}
      <Card>
        <CardHeader>
          <CardTitle>Estimation History</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">
            Estimation history will be available when estimation pages are built.
          </p>
        </CardContent>
      </Card>

      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="Delete Customer"
        description={`Are you sure you want to delete ${customer.firstName} ${customer.lastName}? This is a soft delete — the record will be hidden but historical data is preserved.`}
        confirmLabel="Delete"
        onConfirm={() => deleteMutation.mutate()}
        loading={deleteMutation.isPending}
      />
    </div>
  );
}

function DetailItem({ label, value, className }: { label: string; value: string; className?: string }) {
  return (
    <div className={className}>
      <dt className="text-sm font-medium text-muted-foreground">{label}</dt>
      <dd className="mt-0.5 text-sm">{value}</dd>
    </div>
  );
}
```

#### 3b. Create the page

Create: `frontend-next/src/app/(dashboard)/customers/[id]/page.tsx`

```typescript
import type { Metadata } from "next";
import { CustomerDetail } from "@/components/features/customers/customer-detail";

export const metadata: Metadata = {
  title: "Customer Detail",
};

export default function CustomerDetailPage() {
  return <CustomerDetail />;
}
```

### Step 4: Build Customer Form (shared by create and edit)

#### 4a. Create the form component

Create: `frontend-next/src/components/features/customers/customer-form.tsx`

This is a React Hook Form with Zod validation. It's used by both the "new" and "edit" pages.

Key points:
- Uses `react-hook-form` with `@hookform/resolvers` for Zod validation
- Fetches cities and professions from reference data API via `useQuery`
- On create: `POST /api/customers`, redirect to detail page
- On edit: pre-fills form with existing data, `PUT /api/customers/{id}`, redirect to detail page
- All fields use the `FormField` component from shared components
- City and Profession are `<select>` dropdowns using the shadcn Select component

```typescript
"use client";

import { useRouter } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { createCustomer, updateCustomer, type CustomerResponse } from "@/lib/api/customers";
import { getCities, getProfessions } from "@/lib/api/reference-data";
import { FormField } from "@/components/features/form-field";
import { PageHeader } from "@/components/features/page-header";
import { ErrorAlert } from "@/components/features/error-alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { ArrowLeft, Save } from "lucide-react";
import { useEffect } from "react";

const customerSchema = z.object({
  firstName: z.string().min(1, "First name is required"),
  lastName: z.string().min(1, "Last name is required"),
  nationalId: z.string()
    .min(11, "TCKN must be 11 digits")
    .max(11, "TCKN must be 11 digits")
    .regex(/^\d{11}$/, "TCKN must be exactly 11 digits"),
  email: z.string().email("Invalid email address"),
  phone: z.string().optional(),
  birthDate: z.string().optional(),
  address: z.string().optional(),
  cityId: z.string().optional(),
  professionId: z.string().optional(),
});

type CustomerFormData = z.infer<typeof customerSchema>;

interface CustomerFormProps {
  initialData?: CustomerResponse; // If provided, we're editing
}

export function CustomerForm({ initialData }: CustomerFormProps) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const isEdit = !!initialData;

  const { data: cities, isLoading: citiesLoading } = useQuery({
    queryKey: ["cities"],
    queryFn: getCities,
  });

  const { data: professions, isLoading: professionsLoading } = useQuery({
    queryKey: ["professions"],
    queryFn: getProfessions,
  });

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<CustomerFormData>({
    resolver: zodResolver(customerSchema),
    defaultValues: initialData
      ? {
          firstName: initialData.firstName,
          lastName: initialData.lastName,
          nationalId: initialData.nationalId,
          email: initialData.email,
          phone: initialData.phone ?? "",
          birthDate: initialData.birthDate ?? "",
          address: initialData.address ?? "",
          cityId: initialData.cityId?.toString() ?? "",
          professionId: initialData.professionId?.toString() ?? "",
        }
      : {
          firstName: "",
          lastName: "",
          nationalId: "",
          email: "",
          phone: "",
          birthDate: "",
          address: "",
          cityId: "",
          professionId: "",
        },
  });

  const mutation = useMutation({
    mutationFn: (data: CustomerFormData) => {
      const payload = {
        ...data,
        cityId: data.cityId ? Number(data.cityId) : undefined,
        professionId: data.professionId ? Number(data.professionId) : undefined,
      };
      if (isEdit && initialData) {
        return updateCustomer(initialData.id, payload);
      }
      return createCustomer(payload as any);
    },
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ["customers"] });
      router.push(`/customers/${result.id}`);
    },
  });

  const onSubmit = (data: CustomerFormData) => {
    mutation.mutate(data);
  };

  if (citiesLoading || professionsLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-8 w-64" />
        <Card>
          <CardContent className="space-y-4 pt-6">
            {Array.from({ length: 6 }).map((_, i) => (
              <Skeleton key={i} className="h-10 w-full" />
            ))}
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" onClick={() => router.back()}>
          <ArrowLeft className="size-4" />
        </Button>
        <PageHeader
          title={isEdit ? "Edit Customer" : "New Customer"}
          description={isEdit ? "Update customer information" : "Create a new customer record"}
        />
      </div>

      {mutation.isError && (
        <ErrorAlert
          message={mutation.error instanceof Error ? mutation.error.message : "Failed to save customer"}
        />
      )}

      <form onSubmit={handleSubmit(onSubmit)}>
        <Card>
          <CardHeader>
            <CardTitle>Customer Information</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <FormField
                label="First Name"
                {...register("firstName")}
                error={errors.firstName?.message}
                placeholder="John"
              />
              <FormField
                label="Last Name"
                {...register("lastName")}
                error={errors.lastName?.message}
                placeholder="Doe"
              />
            </div>

            <FormField
              label="National ID (TCKN)"
              {...register("nationalId")}
              error={errors.nationalId?.message}
              placeholder="12345678901"
              maxLength={11}
            />

            <FormField
              label="Email"
              type="email"
              {...register("email")}
              error={errors.email?.message}
              placeholder="john.doe@example.com"
            />

            <FormField
              label="Phone"
              {...register("phone")}
              error={errors.phone?.message}
              placeholder="+90 555 123 4567"
            />

            <FormField
              label="Birth Date"
              type="date"
              {...register("birthDate")}
              error={errors.birthDate?.message}
            />

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              {/* City dropdown */}
              <div className="space-y-1.5">
                <label className="text-sm font-medium">City</label>
                <Select
                  value={watch("cityId") || undefined}
                  onValueChange={(value) => setValue("cityId", value)}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder="Select a city" />
                  </SelectTrigger>
                  <SelectContent>
                    {cities?.map((city) => (
                      <SelectItem key={city.id} value={city.id.toString()}>
                        {city.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              {/* Profession dropdown */}
              <div className="space-y-1.5">
                <label className="text-sm font-medium">Profession</label>
                <Select
                  value={watch("professionId") || undefined}
                  onValueChange={(value) => setValue("professionId", value)}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder="Select a profession" />
                  </SelectTrigger>
                  <SelectContent>
                    {professions?.map((prof) => (
                      <SelectItem key={prof.id} value={prof.id.toString()}>
                        {prof.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            <FormField
              label="Address"
              {...register("address")}
              error={errors.address?.message}
              placeholder="Full address"
            />

            <div className="flex justify-end gap-2 pt-4">
              <Button type="button" variant="outline" onClick={() => router.back()}>
                Cancel
              </Button>
              <Button type="submit" disabled={isSubmitting || mutation.isPending}>
                <Save className="size-4" />
                {isSubmitting || mutation.isPending ? "Saving..." : "Save"}
              </Button>
            </div>
          </CardContent>
        </Card>
      </form>
    </div>
  );
}
```

#### 4b. Create the "New Customer" page

Create: `frontend-next/src/app/(dashboard)/customers/new/page.tsx`

```typescript
import type { Metadata } from "next";
import { CustomerForm } from "@/components/features/customers/customer-form";

export const metadata: Metadata = {
  title: "New Customer",
};

export default function NewCustomerPage() {
  return <CustomerForm />;
}
```

#### 4c. Create the "Edit Customer" page

Create: `frontend-next/src/app/(dashboard)/customers/[id]/edit/page.tsx`

```typescript
import type { Metadata } from "next";
import { EditCustomerForm } from "@/components/features/customers/edit-customer-form";

export const metadata: Metadata = {
  title: "Edit Customer",
};

export default function EditCustomerPage() {
  return <EditCustomerForm />;
}
```

Create: `frontend-next/src/components/features/customers/edit-customer-form.tsx`

This is a thin wrapper that fetches the existing customer and passes it to `CustomerForm`:

```typescript
"use client";

import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { getCustomer } from "@/lib/api/customers";
import { CustomerForm } from "./customer-form";
import { ErrorAlert } from "@/components/features/error-alert";
import { Skeleton } from "@/components/ui/skeleton";
import { Card, CardContent } from "@/components/ui/card";

export function EditCustomerForm() {
  const params = useParams();
  const id = params.id as string;

  const { data: customer, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["customer", id],
    queryFn: () => getCustomer(id),
  });

  if (isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-8 w-64" />
        <Card>
          <CardContent className="space-y-4 pt-6">
            {Array.from({ length: 6 }).map((_, i) => (
              <Skeleton key={i} className="h-10 w-full" />
            ))}
          </CardContent>
        </Card>
      </div>
    );
  }

  if (isError || !customer) {
    return (
      <ErrorAlert
        message={error instanceof Error ? error.message : "Failed to load customer"}
        onRetry={() => refetch()}
      />
    );
  }

  return <CustomerForm initialData={customer} />;
}
```

### Step 5: Verify the build compiles

Run: `cd frontend-next && npm run build`

Fix any TypeScript errors before marking this plan complete.

## Acceptance Criteria

- [ ] `/customers` page renders a paginated table with search
- [ ] `/customers/new` renders a form with all fields and validation
- [ ] `/customers/[id]` shows full customer details in a card layout
- [ ] `/customers/[id]/edit` pre-fills form with existing data
- [ ] TCKN field validates 11-digit numeric format
- [ ] Email field validates email format
- [ ] City and Profession are dropdown selects populated from reference data API
- [ ] Delete shows a confirmation dialog, then soft-deletes and redirects to list
- [ ] Loading states (skeleton) shown while data fetches
- [ ] Error states shown with retry button
- [ ] Empty state shown when list has no results
- [ ] Form shows validation errors inline below each field
- [ ] `npm run build` succeeds without TypeScript errors
