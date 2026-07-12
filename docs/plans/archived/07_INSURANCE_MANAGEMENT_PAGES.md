# Plan: Insurance Management Pages

## Objective

Build the Insurance Management feature pages:
- `/insurances` — product list with type/company/status filters
- `/insurances/[id]` — product detail
- `/insurances/new` — create product form
- `/insurances/[id]/edit` — edit product form
- `/insurances/types` — insurance types management (list + create)
- `/insurances/companies` — insurance companies management (list + create)

## Prerequisites

- Plan `01_BFF_ROUTE_HANDLERS.md` (BFF routes proxy to Gateway)
- Plan `02_API_CLIENT_LIBRARY.md` (insurance types and API functions available)
- Plan `03_SHARED_FEATURE_COMPONENTS.md` (shared components available)

## Files to Read First

- `docs/stories/03_INSURANCE_PRODUCTS.md` — Insurance scenarios and acceptance criteria
- `docs/outlines/05_NEXTJS_FRONTEND.md` — BFF pattern
- `frontend/src/lib/api/insurances.ts` — All insurance types and API functions
- `frontend/src/components/features/customers/customer-form.tsx` — Pattern to follow for forms
- `frontend/src/components/features/customers/customer-list.tsx` — Pattern to follow for list pages
- `frontend/src/components/features/customers/customer-detail.tsx` — Pattern to follow for detail pages

## Key Implementation Notes

### Insurance Types and Companies
The Insurance Service has its own types and companies endpoints:
- `GET /api/insurances/types` — list of `InsuranceTypeResponse` (id, name)
- `GET /api/insurances/companies` — list of `InsuranceCompanyResponse` (id, name, rating, isActive)

The task says "Insurance types and companies management pages" — meaning admin pages to view and create these. For creating types/companies, the backend may expose these endpoints or they may be seed-data only. If the backend doesn't have create endpoints for types/companies, show a read-only list page.

Check the API spec: The `02_MICROSERVICES_SPECIFICATIONS.md` lists:
- `GET /api/insurances/types` — list insurance types
- `GET /api/insurances/companies` — list insurance companies

There are no POST endpoints for types/companies in the current spec. So the types/companies pages should be **read-only list pages**.

### Filters
The insurance list page needs filters for type, company, and active status. These are client-side filter state passed as query params to `getInsurances()`.

### Base Premium
The base premium is a positive decimal value. Display with currency formatting.

## Steps

### Step 1: Create directory structure

```
frontend/src/app/(dashboard)/insurances/
frontend/src/app/(dashboard)/insurances/new/
frontend/src/app/(dashboard)/insurances/[id]/
frontend/src/app/(dashboard)/insurances/[id]/edit/
frontend/src/app/(dashboard)/insurances/types/
frontend/src/app/(dashboard)/insurances/companies/
frontend/src/components/features/insurances/
```

### Step 2: Build Insurance Product List page

Create `frontend/src/components/features/insurances/insurance-list.tsx` — Client Component with:
- `useQuery` fetching `getInsurances(page, pageSize, typeId, companyId, search)`
- `useQuery` fetching `getInsuranceTypes()` and `getInsuranceCompanies()` for filter dropdowns
- Filter bar: SearchBar + Type Select + Company Select + Active toggle
- Table columns: Name, Type, Company, Base Premium, Status (Active/Inactive badge)
- Status column uses `StatusBadge` component with ACTIVE/INACTIVE mapping
- Clickable rows → navigate to `/insurances/[id]`
- `PaginationBar`
- `DataTableSkeleton` loading state
- `EmptyState` with "New Product" button
- `ErrorAlert` with retry

Create `frontend/src/app/(dashboard)/insurances/page.tsx` — Server Component rendering `<InsuranceList />`.

### Step 3: Build Insurance Product Detail page

Create `frontend/src/components/features/insurances/insurance-detail.tsx` — Client Component with:
- `useQuery` fetching `getInsurance(id)`
- Back button to `/insurances`
- `PageHeader` with product name as title
- `StatusBadge` showing active/inactive status
- Card with all fields: name, description, type, company, base premium (formatted), status, dates
- Edit and Deactivate/Delete action buttons
- `ConfirmDialog` for deactivation confirmation

Create `frontend/src/app/(dashboard)/insurances/[id]/page.tsx` — Server Component rendering `<InsuranceDetail />`.

### Step 4: Build Insurance Product Form (shared)

Create `frontend/src/components/features/insurances/insurance-form.tsx` — Client Component.

**Zod schema:**
```typescript
const insuranceSchema = z.object({
  name: z.string().min(1, "Name is required"),
  description: z.string().optional(),
  typeId: z.string().min(1, "Insurance type is required"),
  companyId: z.string().min(1, "Company is required"),
  basePremium: z.string()
    .refine((v) => Number(v) > 0, "Must be a positive number"),
  isActive: z.boolean().default(true),
});
```

**Form layout:** Card-based:
- Name (full width)
- Description (full width, textarea if available or regular input)
- Type dropdown (fetched from `getInsuranceTypes()`) + Company dropdown (fetched from `getInsuranceCompanies()`) row
- Base Premium (number input)
- Active toggle (checkbox or switch)
- Cancel + Save buttons

### Step 5: Create Insurance Product page files

Create:
- `frontend/src/app/(dashboard)/insurances/new/page.tsx` — renders `<InsuranceForm />`
- `frontend/src/app/(dashboard)/insurances/[id]/edit/page.tsx` — renders `<EditInsuranceForm />`

Create `frontend/src/components/features/insurances/edit-insurance-form.tsx` — fetches product by ID, passes `initialData` to `InsuranceForm`.

### Step 6: Build Insurance Types page (read-only list)

Create `frontend/src/components/features/insurances/insurance-types-list.tsx` — Client Component with:
- `useQuery` fetching `getInsuranceTypes()`
- Simple table with ID and Name columns
- `DataTableSkeleton` loading state
- `ErrorAlert` with retry

Create `frontend/src/app/(dashboard)/insurances/types/page.tsx` — Server Component rendering `<InsuranceTypesList />`.

### Step 7: Build Insurance Companies page (read-only list)

Create `frontend/src/components/features/insurances/insurance-companies-list.tsx` — Client Component with:
- `useQuery` fetching `getInsuranceCompanies()`
- Table with Name, Rating, Status (Active/Inactive badge) columns
- `DataTableSkeleton` loading state
- `ErrorAlert` with retry

Create `frontend/src/app/(dashboard)/insurances/companies/page.tsx` — Server Component rendering `<InsuranceCompaniesList />`.

### Step 8: Verify build

Run: `cd frontend && npm run build`

## Acceptance Criteria

- [x] `/insurances` page renders filtered table with type/company/status filters
- [x] `/insurances/[id]` shows product details with status badge
- [x] `/insurances/new` renders form with type and company dropdowns
- [x] `/insurances/[id]/edit` pre-fills form with existing data
- [x] Base premium validates positive number
- [x] Active status can be toggled
- [x] `/insurances/types` shows read-only list of insurance types
- [x] `/insurances/companies` shows read-only list of companies with ratings and status
- [x] Deactivate shows confirmation dialog
- [x] Loading, error, and empty states all handled
- [x] `npm run build` succeeds without TypeScript errors
