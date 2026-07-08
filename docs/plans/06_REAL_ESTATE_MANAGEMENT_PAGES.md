# Plan: Real Estate Management Pages

## Objective

Build the Real Estate Management feature pages:
- `/real-estate` — paginated table with search
- `/real-estate/[id]` — detail view
- `/real-estate/new` — create form with reference data dropdowns
- `/real-estate/[id]/edit` — edit form

## Prerequisites

- Plan `01_BFF_ROUTE_HANDLERS.md` (BFF routes proxy to Gateway, including `/api/real-estate`)
- Plan `02_API_CLIENT_LIBRARY.md` (real estate types and API functions available)
- Plan `03_SHARED_FEATURE_COMPONENTS.md` (shared components available)

## Files to Read First

- `docs/stories/06_REAL_ESTATE_MANAGEMENT.md` — Real estate scenarios and acceptance criteria
- `docs/outlines/05_NEXTJS_FRONTEND.md` — BFF pattern
- `frontend-next/src/lib/api/realestate.ts` — All real estate types, API functions, and reference data functions
- `frontend-next/src/lib/api/customers.ts` — For customer dropdown in form
- `frontend-next/src/lib/api/reference-data.ts` — For cities dropdown in form
- `frontend-next/src/components/features/vehicles/vehicle-form.tsx` — Pattern to follow (cascading dropdowns pattern)
- `frontend-next/src/components/features/vehicles/vehicle-detail.tsx` — Pattern to follow for detail pages
- `frontend-next/src/components/features/vehicles/vehicle-list.tsx` — Pattern to follow for list pages

## Key Implementation Notes

### Reference Data Dropdowns
The real estate form has three non-cascading reference data dropdowns:
- Construction Type (`getConstructionTypes()`)
- Luxury Class (`getLuxuryClasses()`)
- Usage Type (`getUsageTypes()`)

These are all fetched on mount — no cascading. However, the City dropdown comes from `reference-data` API (`getCities()`).

### Customer Selection
Same pattern as vehicle form — use `useQuery` to fetch customers with search, render in a Select dropdown.

### Validation Rules
- `squareMeters` must be a positive number
- `constructionYear` cannot be in the future
- `address` is required
- `customerId` is required (must link to existing customer)

## Steps

### Step 1: Create directory structure

```
frontend-next/src/app/(dashboard)/real-estate/
frontend-next/src/app/(dashboard)/real-estate/new/
frontend-next/src/app/(dashboard)/real-estate/[id]/
frontend-next/src/app/(dashboard)/real-estate/[id]/edit/
frontend-next/src/components/features/real-estate/
```

### Step 2: Build Real Estate List page

Create `frontend-next/src/components/features/real-estate/real-estate-list.tsx` — Client Component with:
- `useQuery` fetching `getRealEstates(page, pageSize, search)`
- `SearchBar` for address search
- Table columns: Address, City, Square Meters, Construction Year, Customer Name
- Clickable rows → navigate to `/real-estate/[id]`
- `PaginationBar`
- `DataTableSkeleton` loading state
- `EmptyState` with "New Property" button
- `ErrorAlert` with retry

Create `frontend-next/src/app/(dashboard)/real-estate/page.tsx` — Server Component rendering `<RealEstateList />`.

### Step 3: Build Real Estate Detail page

Create `frontend-next/src/components/features/real-estate/real-estate-detail.tsx` — Client Component with:
- `useQuery` fetching `getRealEstate(id)`
- Back button to `/real-estate`
- `PageHeader` with address as title
- Card with all fields in a `<dl>` grid (address, city, district, square meters, construction year, construction type, luxury class, usage type, customer name)
- Edit and Delete action buttons
- `ConfirmDialog` for delete confirmation
- Delete mutation calls `deleteRealEstate(id)`, invalidates cache, redirects to list

Create `frontend-next/src/app/(dashboard)/real-estate/[id]/page.tsx` — Server Component rendering `<RealEstateDetail />`.

### Step 4: Build Real Estate Form (shared)

Create `frontend-next/src/components/features/real-estate/real-estate-form.tsx` — Client Component.

**Zod schema:**
```typescript
const realEstateSchema = z.object({
  address: z.string().min(1, "Address is required"),
  cityId: z.string().optional(),
  district: z.string().optional(),
  squareMeters: z.string()
    .refine((v) => !v || Number(v) > 0, "Must be a positive number"),
  constructionYear: z.string()
    .refine((v) => !v || Number(v) <= new Date().getFullYear(), "Cannot be in the future"),
  constructionTypeId: z.string().optional(),
  luxuryClassId: z.string().optional(),
  usageTypeId: z.string().optional(),
  customerId: z.string().min(1, "Customer is required"),
});
```

**Form layout:** Card-based, same pattern as vehicle/customer forms:
- Address (full width)
- City + District row
- Square Meters + Construction Year row
- Customer dropdown (searchable, required)
- Construction Type + Luxury Class + Usage Type row
- Cancel + Save buttons

### Step 5: Create page files

Create:
- `frontend-next/src/app/(dashboard)/real-estate/new/page.tsx` — renders `<RealEstateForm />`
- `frontend-next/src/app/(dashboard)/real-estate/[id]/edit/page.tsx` — renders `<EditRealEstateForm />`

Create `frontend-next/src/components/features/real-estate/edit-real-estate-form.tsx` — fetches property by ID, passes `initialData` to `RealEstateForm`.

### Step 6: Verify build

Run: `cd frontend-next && npm run build`

## Acceptance Criteria

- [x] `/real-estate` page renders paginated table with search
- [x] `/real-estate/[id]` shows full property details with Edit and Delete buttons
- [x] `/real-estate/new` renders form with all reference data dropdowns populated
- [x] Construction Type, Luxury Class, and Usage Type are dropdowns from API
- [x] City dropdown populated from reference data API
- [x] Customer dropdown is required
- [x] Square meters validates positive number
- [x] Construction year validates not in future
- [x] Address is required
- [x] Delete shows confirmation dialog
- [x] Loading, error, and empty states all handled
- [x] `npm run build` succeeds without TypeScript errors
