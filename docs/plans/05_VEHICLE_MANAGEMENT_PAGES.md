# Plan: Vehicle Management Pages

## Objective

Build the Vehicle Management feature pages:
- `/vehicles` — paginated table with search
- `/vehicles/[id]` — detail view
- `/vehicles/new` — create form with cascading dropdowns (brand → model → engine/fuel/type/package)
- `/vehicles/[id]/edit` — edit form

## Prerequisites

- Plan `01_BFF_ROUTE_HANDLERS.md` (BFF routes proxy to Gateway)
- Plan `02_API_CLIENT_LIBRARY.md` (vehicle types and API functions available)
- Plan `03_SHARED_FEATURE_COMPONENTS.md` (shared components available)

## Files to Read First

- `docs/stories/05_VEHICLE_MANAGEMENT.md` — Vehicle scenarios and acceptance criteria
- `docs/outlines/05_NEXTJS_FRONTEND.md` — BFF pattern
- `frontend-next/src/lib/api/vehicles.ts` — All vehicle types, API functions, and reference data functions
- `frontend-next/src/lib/api/customers.ts` — For customer dropdown in vehicle form
- `frontend-next/src/components/features/customers/customer-form.tsx` — Pattern to follow for forms
- `frontend-next/src/components/features/customers/customer-detail.tsx` — Pattern to follow for detail pages
- `frontend-next/src/components/features/customers/customer-list.tsx` — Pattern to follow for list pages

## Key Implementation Notes

### Cascading Dropdowns
The vehicle form has cascading dropdowns: selecting a Brand filters the Model dropdown. This requires:
1. `useQuery` to fetch brands on mount
2. `useQuery` to fetch models when `brandId` changes (enabled only when brandId is set)
3. Reset model selection when brand changes

### Reference Data
All reference data (brands, models, engines, fuel types, types, packages) comes from the Vehicle Service via the API client functions created in Plan 02.

### Customer Selection
The vehicle form requires selecting a customer. Use a `useQuery` to fetch the customer list (first page with search) and render a Select dropdown. For large customer lists, use a searchable select pattern.

### Turkish Plate Format
The plate field should validate Turkish plate format: `XX 1234` or `XX 1234 YY`.

### Chassis Number
17 characters alphanumeric.

## Steps

### Step 1: Create directory structure

```
frontend-next/src/app/(dashboard)/vehicles/
frontend-next/src/app/(dashboard)/vehicles/new/
frontend-next/src/app/(dashboard)/vehicles/[id]/
frontend-next/src/app/(dashboard)/vehicles/[id]/edit/
frontend-next/src/components/features/vehicles/
```

### Step 2: Build Vehicle List page

Create `frontend-next/src/components/features/vehicles/vehicle-list.tsx` — Client Component with:
- `useQuery` fetching `getVehicles(page, pageSize, search)`
- `SearchBar` for plate/brand search
- Table columns: Plate, Brand/Model, Customer Name, License Date
- Clickable rows → navigate to `/vehicles/[id]`
- `PaginationBar`
- `DataTableSkeleton` loading state
- `EmptyState` with "New Vehicle" button
- `ErrorAlert` with retry

Create `frontend-next/src/app/(dashboard)/vehicles/page.tsx` — Server Component rendering `<VehicleList />`.

### Step 3: Build Vehicle Detail page

Create `frontend-next/src/components/features/vehicles/vehicle-detail.tsx` — Client Component with:
- `useQuery` fetching `getVehicle(id)`
- Back button to `/vehicles`
- `PageHeader` with plate as title
- Card with all vehicle fields in a `<dl>` grid
- Edit and Delete action buttons
- `ConfirmDialog` for delete confirmation
- Delete mutation calls `deleteVehicle(id)`, invalidates cache, redirects to list

Create `frontend-next/src/app/(dashboard)/vehicles/[id]/page.tsx` — Server Component rendering `<VehicleDetail />`.

### Step 4: Build Vehicle Form (shared)

Create `frontend-next/src/components/features/vehicles/vehicle-form.tsx` — Client Component.

**Zod schema:**
```typescript
const vehicleSchema = z.object({
  plate: z.string()
    .regex(/^\d{2}\s?[A-Z]{1,3}\s?\d{2,4}(\s?[A-Z]{2})?$/, "Invalid Turkish plate format (e.g., 34 ABC 1234)"),
  chassisNumber: z.string()
    .min(17, "Chassis number must be 17 characters")
    .max(17, "Chassis number must be 17 characters")
    .optional()
    .or(z.literal("")),
  licenseFirstDate: z.string().optional(),
  carBrandId: z.string().optional(),
  carModelId: z.string().optional(),
  carEngineId: z.string().optional(),
  carFuelTypeId: z.string().optional(),
  carTypeId: z.string().optional(),
  carPackageId: z.string().optional(),
  customerId: z.string().min(1, "Customer is required"),
});
```

**Cascading dropdown logic:**
- `useQuery` for brands (always enabled)
- `useQuery` for models (enabled: `!!watchBrandId`)
- Watch `carBrandId` with `watch("carBrandId")`
- When brand changes, reset `carModelId` to `""`
- Other reference data dropdowns (engine, fuel type, type, package) are always fetched — no cascading
- Customer dropdown: use `useQuery` to fetch customers with a search input

**Form layout:** Same card-based layout as `customer-form.tsx`:
- Plate + Chassis Number row
- License First Date
- Customer dropdown (searchable)
- Brand → Model row (cascading)
- Engine + Fuel Type row
- Type + Package row
- Cancel + Save buttons

### Step 5: Create page files

Create:
- `frontend-next/src/app/(dashboard)/vehicles/new/page.tsx` — renders `<VehicleForm />`
- `frontend-next/src/app/(dashboard)/vehicles/[id]/edit/page.tsx` — renders `<EditVehicleForm />`

Create `frontend-next/src/components/features/vehicles/edit-vehicle-form.tsx` — fetches vehicle by ID, passes `initialData` to `VehicleForm`.

### Step 6: Verify build

Run: `cd frontend-next && npm run build`

## Acceptance Criteria

- [ ] `/vehicles` page renders paginated table with search
- [ ] `/vehicles/[id]` shows full vehicle details with Edit and Delete buttons
- [ ] `/vehicles/new` renders form with cascading brand→model dropdown
- [ ] Selecting a brand populates the model dropdown
- [ ] Changing brand resets model selection
- [ ] Customer dropdown is required and searchable
- [ ] Plate validates Turkish plate format
- [ ] Chassis number validates 17-character alphanumeric
- [ ] Delete shows confirmation dialog
- [ ] Loading, error, and empty states all handled
- [ ] `npm run build` succeeds without TypeScript errors
