# Plan 03: Fix Estimation Form — Steps 2 & 3

## Objective

Fix the estimation wizard:
1. **Step 2** — Show insurance **products** (insurances table) instead of insurance **types**
2. **Step 3** — Conditionally show vehicle OR real estate selector based on selected insurance's type, and wire up actual API calls to load the customer's assets

## Dependencies

- [x] Plan 01 (`01-seed-data-restructure.md`) — needs the new insurance types/products structure
- [x] Plan 02 (`02-asset-api-customer-filter.md`) — needs `customerId` query param on vehicle & real estate endpoints

## Files to Read First

- `frontend/src/components/features/estimations/estimation-form.tsx` — the wizard component
- `frontend/src/lib/schemas/estimation.ts` — Zod validation schema
- `frontend/src/lib/api/insurances.ts` — insurance API client (has `getInsurances()`, `getInsurance(id)`)
- `frontend/src/lib/api/vehicles.ts` — vehicle API client (updated in plan 02)
- `frontend/src/lib/api/realestate.ts` — real estate API client (updated in plan 02)

## Technical Context

- **Frontend stack**: Next.js 16 App Router, React, TypeScript, Tailwind CSS, shadcn/ui (Base UI), React Hook Form + Zod, @tanstack/react-query
- **Component pattern**: `"use client"` component with `useForm<EstimationFormData>()`, `useQuery()` for data fetching, `useMutation()` for submission
- **Routing**: `useRouter().push(`/estimations/${result.id}`)` on success
- **Current wizard flow**: 4 steps — Customer → Insurance → Link Assets → Review
- **Insurance type-to-asset mapping**: Vehicle type → vehicle assets, Real Estate type → real estate assets, Health/Life → no assets needed
- After Plan 01, insurance types are: Vehicle(1), Real Estate(2), Health(3), Life(4)
- After Plan 01, the `InsuranceResponse` has `typeId` field which we'll use to determine step 3 behavior

## Steps

### Step 1: Change Step 2 — Fetch insurances instead of types

Open `estimation-form.tsx`.

**Replace** the import and query:
```typescript
// OLD — remove this
import { getInsuranceTypes } from "@/lib/api/insurances";

// NEW — add this (or reuse existing import)
import { getInsurances, getInsuranceTypes } from "@/lib/api/insurances";
```

**Replace** the `types` query:
```typescript
// OLD
const { data: types } = useQuery({
  queryKey: ["insurance-types"],
  queryFn: getInsuranceTypes,
});

// NEW — fetch insurance products AND their types
const { data: insurances } = useQuery({
  queryKey: ["insurances"],
  queryFn: () => getInsurances(0, 50), // fetch all active products
});

const { data: types } = useQuery({
  queryKey: ["insurance-types"],
  queryFn: getInsuranceTypes,
});
```

> Keep the `types` query too — we need it later to determine the type name from the selected insurance's `typeId`.

### Step 2: Update Step 2 JSX to show insurance products

In Step 2's render block (around line 251-288), **replace** the Select that shows types with one that shows insurances.

The label changes from `"Insurance Type *"` to `"Insurance *"`.

The Select should iterate over `insurances?.content` (or however the paginated response exposes items — check the `PageResponse` type: it has a `content` array).

When an insurance is selected, store its `typeId` for use in step 3. Add a new piece of form state:
```typescript
const [selectedInsuranceTypeId, setSelectedInsuranceTypeId] = useState<number | null>(null);
```

In the Select's `onValueChange`, look up the selected insurance and store both the insurance ID and its type:
```typescript
onValueChange={(value) => {
  setValue("insuranceTypeId", value ?? "", { shouldDirty: true });
  const ins = insurances?.content?.find((i: any) => i.id === value);
  setSelectedInsuranceTypeId(ins?.typeId ?? null);
}}
```

The Select items show `insurance.name` (e.g., "TRAFFIC", "CASCO", "DASK", "HEALTH", "LIFE").

> **Important**: The `insuranceTypeId` field in the form schema and backend is an integer (the type ID), but currently step 2 stores a type ID directly. After this change, step 2 selects an insurance PRODUCT (UUID string). We need to adjust what gets submitted.
>
> **Decision**: Store the selected product ID temporarily in a separate variable, and set `insuranceTypeId` from the product's `typeId`. The form field `insuranceTypeId` still needs to hold the type ID (integer) for the backend `EstimationRequest`.

### Step 3: Update the selected-entity preview in step 2

The preview card below the select currently shows `selectedType?.name`. Change it to show the selected insurance's name and description:
```tsx
{selectedInsurance && (
  <div className="rounded-lg bg-muted p-3 text-sm">
    <p className="font-medium">{selectedInsurance.name}</p>
    <p className="text-muted-foreground">{selectedInsurance.description ?? ""}</p>
  </div>
)}
```

### Step 4: Update Step 2 progression check

Change `canProceedStep2` to check that a valid insurance type ID has been derived:
```typescript
const canProceedStep2 = watchedTypeId !== "" && selectedInsuranceTypeId !== null;
```

### Step 5: Add vehicle search query (customer-filtered)

Add a new `useQuery` to fetch vehicles filtered by the selected customer. This query should be **enabled** only when the dropdown is open AND a customer is selected:

```typescript
const { data: vehicleData } = useQuery({
  queryKey: ["vehicles", "customer", watchedCustomerId, vehicleSearch],
  queryFn: () => getVehicles(0, 20, vehicleSearch || undefined, undefined, undefined, watchedCustomerId),
  enabled: vehicleDropdownOpen && watchedCustomerId !== "",
});
```

### Step 6: Add real estate search query (customer-filtered)

Same pattern for real estate:
```typescript
const { data: realEstateData } = useQuery({
  queryKey: ["real-estate", "customer", watchedCustomerId, realEstateSearch],
  queryFn: () => getRealEstates(0, 20, realEstateSearch || undefined, undefined, undefined, watchedCustomerId),
  enabled: realEstateDropdownOpen && watchedCustomerId !== "",
});
```

### Step 7: Conditional rendering in Step 3

In Step 3's JSX (around lines 291-392), wrap each asset selector in a condition based on `selectedInsuranceTypeId`:

```
Insurance type ID 1 (Vehicle) → show ONLY the vehicle selector
Insurance type ID 2 (Real Estate) → show ONLY the real estate selector
Insurance type ID 3 (Health) or 4 (Life) → show a message: "No asset linking required for this insurance type"
```

```tsx
{selectedInsuranceTypeId === 1 && (
  <div className="space-y-1.5">
    <label className="text-sm font-medium">Link a Vehicle *</label>
    {/* Vehicle Select with actual data */}
  </div>
)}

{selectedInsuranceTypeId === 2 && (
  <div className="space-y-1.5">
    <label className="text-sm font-medium">Link Real Estate *</label>
    {/* Real Estate Select with actual data */}
  </div>
)}

{(selectedInsuranceTypeId === 3 || selectedInsuranceTypeId === 4) && (
  <div className="rounded-lg bg-muted p-4 text-sm text-muted-foreground">
    No asset linking is required for this insurance type. You can proceed to review.
  </div>
)}
```

### Step 8: Wire up vehicle Select with real data

Replace the placeholder vehicle Select content (lines 315-332) with actual data from `vehicleData?.content`:

```tsx
<SelectContent>
  <div className="flex items-center gap-2 px-2 pb-2" onPointerDown={(e) => e.stopPropagation()}>
    <Search className="size-4 text-muted-foreground shrink-0" />
    <Input
      placeholder="Search by plate..."
      value={vehicleSearch}
      onChange={(e) => setVehicleSearch(e.target.value)}
      onKeyDown={(e) => e.stopPropagation()}
      className="h-8"
    />
  </div>
  {!vehicleData?.content?.length ? (
    <div className="px-2 py-4 text-center text-sm text-muted-foreground">
      {vehicleSearch ? "No vehicles found" : "Type to search your vehicles..."}
    </div>
  ) : (
    vehicleData.content.map((v) => (
      <SelectItem key={v.id} value={v.id}>
        {v.plate} — {v.carBrandName} {v.carModelName}
      </SelectItem>
    ))
  )}
</SelectContent>
```

### Step 9: Wire up real estate Select with real data

Same pattern as vehicles but showing address and city:
```tsx
{realEstateData.content.map((re) => (
  <SelectItem key={re.id} value={re.id}>
    {re.address}{re.cityName ? `, ${re.cityName}` : ""}
  </SelectItem>
))}
```

### Step 10: Update step 3 progression check

Change `canProceedStep3` to match the conditional logic:
```typescript
const canProceedStep3 =
  (selectedInsuranceTypeId === 1 && watchedVehicleId !== "") ||
  (selectedInsuranceTypeId === 2 && watchedRealEstateId !== "") ||
  (selectedInsuranceTypeId === 3 || selectedInsuranceTypeId === 4);
```

This makes Health and Life types always able to proceed (no asset required).

### Step 11: Update Step 3 summary

The summary at the bottom of Step 3 (lines 380-391) should reflect the conditional state:
```tsx
<p><span className="text-muted-foreground">Insurance:</span> {selectedInsurance?.name}</p>
{selectedInsuranceTypeId === 1 && (
  <p><span className="text-muted-foreground">Vehicle:</span> {watchedVehicleId ? "Selected" : "Not selected"}</p>
)}
{selectedInsuranceTypeId === 2 && (
  <p><span className="text-muted-foreground">Real Estate:</span> {watchedRealEstateId ? "Selected" : "Not selected"}</p>
)}
```

### Step 12: Update Step 4 Review

In Step 4 (lines 396-423), update the review summary similarly — show insurance name instead of type name, and conditionally show vehicle/real estate.

### Step 13: Update the `selectedType` references for review

Replace `selectedType` variable:
```typescript
// OLD
const selectedType = types?.find((t) => t.id.toString() === watchedTypeId);

// NEW
const selectedInsurance = insurances?.content?.find((i) => i.id === selectedInsuranceId);
```

Where `selectedInsuranceId` is the UUID of the selected product (stored separately from `insuranceTypeId`).

### Step 14: Update the validation schema if needed

Open `frontend/src/lib/schemas/estimation.ts`. The current schema is:
```typescript
export const estimationSchema = z.object({
  customerId: z.string().min(1, "Customer is required"),
  insuranceTypeId: z.string().min(1, "Insurance type is required"),
  companyId: z.string().optional(),
  vehicleId: z.string().optional(),
  realEstateId: z.string().optional(),
});
```

No changes needed here — the schema still uses `insuranceTypeId` (the type's integer ID), which we set from the selected product's `typeId`. The asset validation is handled in the backend (Plan 04).

### Step 15: Handle the extra state variable

Add near the other state declarations:
```typescript
const [selectedInsuranceId, setSelectedInsuranceId] = useState<string>("");
const [selectedInsuranceTypeId, setSelectedInsuranceTypeId] = useState<number | null>(null);
```

## Acceptance Criteria

- [x] Step 2 shows a dropdown with insurance products: "TRAFFIC", "CASCO", "DASK", "HEALTH", "LIFE"
- [x] Selecting a product stores its `typeId` to drive step 3 behavior
- [x] Step 3 shows ONLY vehicle selector when Vehicle-type insurance selected
- [x] Step 3 shows ONLY real estate selector when Real Estate-type insurance selected
- [x] Step 3 shows "No asset required" message for Health/Life types
- [x] Vehicle selector loads the customer's vehicles by plate search using `customerId` filter
- [x] Real estate selector loads the customer's properties by address search using `customerId` filter
- [x] Can proceed to Step 4 from Step 3 with Health/Life (no asset needed)
- [x] Can proceed to Step 4 from Step 3 with Vehicle type only when a vehicle is selected
- [x] Can proceed to Step 4 from Step 3 with Real Estate type only when a real estate is selected
- [x] Step 4 Review shows the selected insurance product name and linked asset
- [x] Submitting the form sends correct `insuranceTypeId` (integer type ID) to the backend
