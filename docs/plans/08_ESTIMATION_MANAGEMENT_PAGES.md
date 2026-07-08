# Plan: Estimation Management Pages

## Objective

Build the Estimation Management feature pages:
- `/estimations` — list with status badges, customer name, date range filter
- `/estimations/[id]` — detail with full status, premium breakdown, customer info
- `/estimations/new` — multi-step form: select customer → select insurance type/company → optionally link vehicle/real estate → submit

## Prerequisites

- Plan `01_BFF_ROUTE_HANDLERS.md` (BFF routes proxy to Gateway)
- Plan `02_API_CLIENT_LIBRARY.md` (estimation types and API functions available)
- Plan `03_SHARED_FEATURE_COMPONENTS.md` (shared components available)

## Files to Read First

- `docs/stories/04_ESTIMATION_SAGA.md` — Estimation scenarios and acceptance criteria
- `docs/outlines/05_NEXTJS_FRONTEND.md` — BFF pattern
- `docs/outlines/03_SAGA_PATTERN.md` — SAGA flow, estimation status transitions
- `frontend-next/src/lib/api/estimations.ts` — All estimation types and API functions
- `frontend-next/src/lib/api/customers.ts` — For customer dropdown
- `frontend-next/src/lib/api/insurances.ts` — For insurance type/company dropdowns
- `frontend-next/src/lib/api/vehicles.ts` — For vehicle dropdown (optional linkage)
- `frontend-next/src/lib/api/realestate.ts` — For real estate dropdown (optional linkage)
- `frontend-next/src/components/features/status-badge.tsx` — Status badge component
- `frontend-next/src/components/features/customers/customer-list.tsx` — Pattern for list pages

## Key Implementation Notes

### Estimation Status Lifecycle
```
STARTED → COMPLETED (on PremiumCalculated)
STARTED → REJECTED (on *Invalidated, CalculationFailed, or timeout)
```

The `StatusBadge` component already maps these:
- `STARTED` → `secondary` variant
- `COMPLETED` → `default` variant
- `REJECTED` → `destructive` variant

### Multi-Step Form
The "New Estimation" form has 3-4 steps:
1. **Select Customer** — searchable dropdown of customers
2. **Select Insurance** — type dropdown, then company dropdown (filtered by type)
3. **Optional Linkage** — optionally link a vehicle and/or real estate belonging to the selected customer
4. **Review & Submit** — summary of selections, submit button

Implement this as a single page with step state managed by `useState<number>` — not separate routes. Show a step indicator (e.g., "Step 1 of 4").

### Estimation List Filters
- Status filter (All / STARTED / COMPLETED / REJECTED)
- Customer filter (search by customer name/ID)
- Date range filter (from — to)

### SAGA Behavior
After submitting an estimation, it starts in `STARTED` status. The SAGA processes asynchronously — the detail page should show the current status. Consider adding a "Refresh" button or auto-polling with `refetchInterval` in React Query.

For this plan, use `refetchInterval: 5000` (poll every 5 seconds) on the estimation detail page while status is `STARTED`, and stop when terminal (`COMPLETED` or `REJECTED`).

### Premium Display
When the estimation is `COMPLETED`, show the premium prominently. When `REJECTED`, show the error details from the `details` field.

## Steps

### Step 1: Create directory structure

```
frontend-next/src/app/(dashboard)/estimations/
frontend-next/src/app/(dashboard)/estimations/new/
frontend-next/src/app/(dashboard)/estimations/[id]/
frontend-next/src/components/features/estimations/
```

### Step 2: Build Estimation List page

Create `frontend-next/src/components/features/estimations/estimation-list.tsx` — Client Component with:
- `useQuery` fetching `getEstimations({ page, size, status, customerId, dateFrom, dateTo })`
- Filter bar:
  - Status filter: Select dropdown with All/STARTED/COMPLETED/REJECTED options (use local state, not API call)
  - Date range: two `<input type="date">` fields
  - `SearchBar` for customer name search
- Table columns: Customer Name, Insurance Type, Status (StatusBadge), Premium (formatted), Created Date
- Clickable rows → navigate to `/estimations/[id]`
- `PaginationBar`
- `DataTableSkeleton` loading state
- `EmptyState` with "New Estimation" button
- `ErrorAlert` with retry

Create `frontend-next/src/app/(dashboard)/estimations/page.tsx` — Server Component rendering `<EstimationList />`.

### Step 3: Build Estimation Detail page

Create `frontend-next/src/components/features/estimations/estimation-detail.tsx` — Client Component.

This is the most important detail page because it shows SAGA status. Key behaviors:
- `useQuery` with `refetchInterval: status === "STARTED" ? 5000 : false` (poll while in progress)
- Show status prominently with `StatusBadge`
- If `COMPLETED`: show premium in a highlighted card
- If `REJECTED`: show error details from the `details` field
- Customer info card (name, email, phone)
- Insurance info card (type, company)
- Vehicle info card (if linked — plate, brand/model)
- Real Estate info card (if linked — address)
- Timeline of events (created date, updated date)

Create `frontend-next/src/app/(dashboard)/estimations/[id]/page.tsx` — Server Component rendering `<EstimationDetail />`.

### Step 4: Build Estimation Form (multi-step)

Create `frontend-next/src/components/features/estimations/estimation-form.tsx` — Client Component.

**State:**
```typescript
const [step, setStep] = useState(1);
// Step 1 state
const [selectedCustomerId, setSelectedCustomerId] = useState<string>("");
// Step 2 state
const [selectedTypeId, setSelectedTypeId] = useState<string>("");
const [selectedCompanyId, setSelectedCompanyId] = useState<string>("");
// Step 3 state
const [selectedVehicleId, setSelectedVehicleId] = useState<string>("");
const [selectedRealEstateId, setSelectedRealEstateId] = useState<string>("");
```

**Step 1 — Select Customer:**
- Searchable Select dropdown of customers (fetched via `useQuery` with search)
- Next button enabled only when customer is selected
- Show selected customer's name below dropdown

**Step 2 — Select Insurance:**
- Type dropdown (fetched from `getInsuranceTypes()`)
- Company dropdown (fetched from `getInsuranceCompanies()` — no type filtering on frontend since API may not support it; just show all active companies)
- Next button enabled only when type is selected
- Show selected type and company names

**Step 3 — Optional Linkage:**
- "Link a Vehicle (optional)" — Select dropdown of vehicles for the selected customer (fetch with customerId filter if available, otherwise all)
- "Link Real Estate (optional)" — Select dropdown of real estate properties for the selected customer
- "Skip" button to go to next step without linking
- Next button always enabled

**Step 4 — Review & Submit:**
- Summary of all selections:
  - Customer: name
  - Insurance Type: name / Company: name
  - Vehicle: plate (or "None")
  - Real Estate: address (or "None")
- Submit button → calls `createEstimation()` mutation
- On success: redirect to `/estimations/[newId]`

**Step indicator:**
```typescript
const steps = [
  { number: 1, label: "Customer" },
  { number: 2, label: "Insurance" },
  { number: 3, label: "Link Assets" },
  { number: 4, label: "Review" },
];
```

Render step indicator at top of form with current step highlighted.

**Zod validation (on submit):**
```typescript
const estimationSchema = z.object({
  customerId: z.string().min(1, "Customer is required"),
  insuranceTypeId: z.string().min(1, "Insurance type is required"),
  companyId: z.string().optional(),
  vehicleId: z.string().optional(),
  realEstateId: z.string().optional(),
});
```

Convert type ID to number before sending to API.

### Step 5: Create page file

Create `frontend-next/src/app/(dashboard)/estimations/new/page.tsx` — Server Component rendering `<EstimationForm />`.

### Step 6: Verify build

Run: `cd frontend-next && npm run build`

## Acceptance Criteria

- [ ] `/estimations` page renders table with status badges, customer names, and filters
- [ ] Status filter (All/STARTED/COMPLETED/REJECTED) works
- [ ] Date range filter works
- [ ] `/estimations/[id]` shows full estimation detail with status badge
- [ ] Detail page polls every 5 seconds while status is STARTED
- [ ] Detail page stops polling when status is COMPLETED or REJECTED
- [ ] COMPLETED estimation shows premium prominently
- [ ] REJECTED estimation shows error details
- [ ] `/estimations/new` has multi-step form with step indicator
- [ ] Step 1: Customer selection (searchable dropdown)
- [ ] Step 2: Insurance type and company selection
- [ ] Step 3: Optional vehicle/real estate linkage
- [ ] Step 4: Review summary and submit
- [ ] Submit creates estimation and redirects to detail page
- [ ] Loading, error, and empty states all handled
- [ ] `npm run build` succeeds without TypeScript errors
