# Plan 07: Frontend — Update Estimation to Use insuranceId Instead of insuranceTypeId

## Objective

Update all frontend TypeScript/React code that references `insuranceTypeId` on estimation objects to use `insuranceId` instead. The user now selects a specific insurance **product** (e.g., "CASCO", "TRAFFIC") when creating an estimation, and the insurance type is derived from the selected product.

## Dependencies

- **Plan 03 (Estimation Backend Update) MUST be completed first.** The backend API will reject requests with `insuranceTypeId` and expect `insuranceId` instead.

## Files to Read Before Starting

- `frontend-next/src/lib/schemas/estimation.ts`
- `frontend-next/src/lib/api/estimations.ts`
- `frontend-next/src/components/features/estimations/estimation-form.tsx`
- `frontend-next/src/components/features/estimations/estimation-detail.tsx`
- `frontend-next/src/components/features/estimations/estimation-list.tsx`
- `frontend-next/src/lib/api/insurances.ts` — for the `InsuranceResponse` interface
- `frontend-next/AGENTS.md` — Next.js version-specific guidance

## Technical Context

### Current flow (before change)

1. User creates estimation:
   - Step 1: Select customer
   - Step 2: Select insurance **type** (from `insurance_types`: Vehicle, Real Estate, Health, Life)
   - Step 3: Link assets (vehicle or real estate based on type)
   - Step 4: Review and submit
   - POST body includes `insuranceTypeId` (Integer)

2. User views estimation detail:
   - `EstimationResponse.insuranceTypeId` is displayed
   - `EstimationResponse.insuranceTypeName` is shown (enriched by backend or fallback map)

### New flow (after change)

1. User creates estimation:
   - Step 1: Select customer (no change)
   - Step 2: Select a specific insurance **product** (e.g., "TRAFFIC", "CASCO", "DASK", "HEALTH", "LIFE") from the `insurances` list
   - The insurance type is derived from the selected product (`ins.typeId`)
   - Step 3: Link assets (based on the derived type — same logic)
   - Step 4: Review and submit
   - POST body includes `insuranceId` (UUID string)

2. User views estimation detail:
   - `EstimationResponse.insuranceId` is shown
   - `EstimationResponse.insuranceName` is shown (new field)
   - `EstimationResponse.insuranceTypeName` is shown (already exists, still works)

### What already exists

The `estimation-form.tsx` **already fetches insurances** and lets the user select by insurance product name! Look at the current code:
- It fetches `insurances` via `getInsurances(0, 50)`
- It fetches `types` via `getInsuranceTypes()` (but no longer uses it directly)
- Step 2 has a Select that shows insurance names and derives `typeId` from the selected insurance
- BUT the current code still sends `insuranceTypeId` (the type) to the backend

So the form UI is actually close to what we need. The change is to send `insuranceId` instead of `insuranceTypeId`.

### Key changes needed

1. **Schema:** `insuranceTypeId` → `insuranceId` (string, UUID format)
2. **API types:** `EstimationRequest.insuranceTypeId` → `insuranceId`, `EstimationResponse.insuranceTypeId` → `insuranceId`, add `insuranceName`
3. **Form:** Wire the selected insurance's ID to `insuranceId` field instead of deriving the type
4. **Detail:** Show `insuranceName`, derive `insuranceTypeName` from backend or fallback
5. **List:** Show `insuranceName` in columns

## Steps

### Step 1: Update estimation.ts schema  ✓

Open `frontend-next/src/lib/schemas/estimation.ts`.

**Change:**
```typescript
insuranceTypeId: z.string().min(1, "Insurance type is required"),
```
**To:**
```typescript
insuranceId: z.string().min(1, "Insurance is required"),
```

The complete schema:
```typescript
import { z } from "zod";

export const estimationSchema = z.object({
  customerId: z.string().min(1, "Customer is required"),
  insuranceId: z.string().min(1, "Insurance is required"),
  vehicleId: z.string().optional(),
  realEstateId: z.string().optional(),
});

export type EstimationFormData = z.infer<typeof estimationSchema>;
```

### Step 2: Update estimations.ts API types  ✓

Open `frontend-next/src/lib/api/estimations.ts`.

**A. Update `EstimationResponse` interface:**
- Change `insuranceTypeId: number;` to `insuranceId: string;`
- Add `insuranceName?: string;` (keep `insuranceTypeName?: string;`)

**B. Update `EstimationRequest` interface:**
- Change `insuranceTypeId: number;` to `insuranceId: string;`

### Step 3: Update estimation-form.tsx  ✓

Open `frontend-next/src/components/features/estimations/estimation-form.tsx`.

**A. Update form default values:**
```typescript
defaultValues: {
  customerId: "",
  insuranceTypeId: "",  // OLD
  vehicleId: "",
  realEstateId: "",
},
```
Change to:
```typescript
defaultValues: {
  customerId: "",
  insuranceId: "",       // NEW
  vehicleId: "",
  realEstateId: "",
},
```

**B. Update watch variable:**
```typescript
const watchedTypeId = watch("insuranceTypeId");  // OLD
```
Change to:
```typescript
const watchedInsuranceId = watch("insuranceId");  // NEW
```

**C. Update canProceed checks:**

Find:
```typescript
const canProceedStep2 = watchedTypeId !== "" && selectedInsuranceTypeId !== null;
```
Change to:
```typescript
const canProceedStep2 = watchedInsuranceId !== "" && selectedInsuranceTypeId !== null;
```

Find:
```typescript
const canProceedStep3 = ...  // uses selectedInsuranceTypeId — this is fine, keep it
```
The `selectedInsuranceTypeId` is still derived from the selected insurance product's `typeId`. That logic is correct and doesn't need to change.

**D. Update the mutation function:**
```typescript
mutationFn: (data: EstimationFormData) => {
  return createEstimation({
    customerId: data.customerId,
    insuranceTypeId: Number(data.insuranceTypeId),  // OLD
    vehicleId: data.vehicleId || undefined,
    realEstateId: data.realEstateId || undefined,
  });
},
```
Change to:
```typescript
mutationFn: (data: EstimationFormData) => {
  return createEstimation({
    customerId: data.customerId,
    insuranceId: data.insuranceId,  // NEW — string UUID, no Number() conversion
    vehicleId: data.vehicleId || undefined,
    realEstateId: data.realEstateId || undefined,
  });
},
```

**E. Update the Step 2 insurance Select's onValueChange:**
```typescript
onValueChange={(value) => {
  setSelectedInsuranceId(value ?? "");
  const ins = insurances?.content?.find((i) => i.id === value);
  const typeId = ins?.typeId ?? null;
  setSelectedInsuranceTypeId(typeId);
  setValue("insuranceTypeId", typeId?.toString() ?? "", { shouldDirty: true });  // OLD
}}
```
Change to:
```typescript
onValueChange={(value) => {
  setSelectedInsuranceId(value ?? "");
  const ins = insurances?.content?.find((i) => i.id === value);
  const typeId = ins?.typeId ?? null;
  setSelectedInsuranceTypeId(typeId);
  setValue("insuranceId", value ?? "", { shouldDirty: true });  // NEW — use insurance ID
}}
```

**F. Update error display for the insurance field:**
```typescript
{errors.insuranceTypeId?.message && (  // OLD
  <p class="text-sm text-destructive" role="alert">
    {errors.insuranceTypeId.message}
  </p>
)}
```
Change to:
```typescript
{errors.insuranceId?.message && (  // NEW
  <p className="text-sm text-destructive" role="alert">
    {errors.insuranceId.message}
  </p>
)}
```

### Step 4: Update estimation-detail.tsx  ✓

Open `frontend-next/src/components/features/estimations/estimation-detail.tsx`.

**A. Update the insurance type name resolution:**

The detail component has a client-side fallback `INSURANCE_TYPE_NAMES` map. Keep this for backward compatibility but modify the resolution:

Current:
```typescript
const resolvedInsuranceTypeName = estimation?.insuranceTypeName
  ?? (estimation?.insuranceTypeId ? INSURANCE_TYPE_NAMES[estimation.insuranceTypeId] : null);
```

New (the backend now returns `insuranceTypeName` directly, and we also have `insuranceName`):
```typescript
const resolvedInsuranceTypeName = estimation?.insuranceTypeName ?? null;
const resolvedInsuranceName = estimation?.insuranceName ?? null;
```

(The `INSURANCE_TYPE_NAMES` fallback map can be removed since `insuranceTypeId` is no longer on the response.)

**B. Update the Insurance Information card:**

Find:
```typescript
<DetailItem label="Insurance Type" value={resolvedInsuranceTypeName ?? "—"} />
```

Replace with:
```typescript
<DetailItem label="Insurance" value={resolvedInsuranceName ?? "—"} />
<DetailItem label="Insurance Type" value={resolvedInsuranceTypeName ?? "—"} />
```

This shows both the specific product name (e.g., "CASCO") and its type (e.g., "Vehicle").

**C. Remove the `INSURANCE_TYPE_NAMES` constant** since it's no longer needed:
```typescript
const INSURANCE_TYPE_NAMES: Record<number, string> = {
  1: "Vehicle",
  2: "Real Estate",
  3: "Health",
  4: "Life",
};
```
Delete this block.

### Step 5: Update estimation-list.tsx  ✓

Open `frontend-next/src/components/features/estimations/estimation-list.tsx`.

**A. Update column definitions:**

The list currently has an `insuranceTypeName` column. The backend's `EstimationResponse` already includes `insuranceTypeName`, so the list column should continue to work. However, the `INSURANCE_TYPE_NAMES` fallback map references `estimation.insuranceTypeId` which no longer exists.

Find:
```typescript
const INSURANCE_TYPE_NAMES: Record<number, string> = {
  1: "Vehicle",
  2: "Real Estate",
  3: "Health",
  4: "Life",
};
```
Delete this block.

Find in the `enrichedData` useMemo:
```typescript
insuranceTypeName: estimation.insuranceTypeName ?? INSURANCE_TYPE_NAMES[estimation.insuranceTypeId] ?? undefined,
```
Change to:
```typescript
insuranceTypeName: estimation.insuranceTypeName ?? undefined,
```

**B. Add an `insuranceName` column** (optional but recommended):

Add a new column for insurance product name:
```typescript
columnHelper.accessor("insuranceName", {
  header: "Insurance",
  cell: (info) => info.getValue() ?? "—",
}),
```

Place it before the `insuranceTypeName` column.

### Step 6: Verify TypeScript compilation  ✓

```bash
cd frontend-next && npx tsc --noEmit
```

Fix any type errors. Common issues:
- ~~`insuranceTypeId` does not exist on type `EstimationResponse` → use `insuranceId`~~
- ~~`Number(data.insuranceTypeId)` on a string UUID → use `data.insuranceId` directly~~

TypeScript compilation passes with zero errors.

### Step 7: Verify the build  ⚠️ (pre-existing issue)

```bash
cd frontend-next && npm run build
```

## Acceptance Criteria

- [x] `estimation.ts` schema uses `insuranceId: z.string()` instead of `insuranceTypeId`
- [x] `EstimationRequest` interface uses `insuranceId: string` instead of `insuranceTypeId: number`
- [x] `EstimationResponse` interface uses `insuranceId: string`, has `insuranceName?: string`
- [x] Form Step 2 sends the selected insurance product's ID as `insuranceId`
- [x] Form validation uses `insuranceId` field name
- [x] Detail page shows both insurance product name and insurance type name
- [x] List page uses backend-provided `insuranceTypeName` without client-side fallback to `insuranceTypeId`
- [x] `INSURANCE_TYPE_NAMES` fallback maps removed from detail and list components
- [x] `npx tsc --noEmit` passes
- [ ] `npm run build` — pre-existing issue on `/customers` page (uses `headers()` without `force-dynamic`), unrelated to estimation changes
