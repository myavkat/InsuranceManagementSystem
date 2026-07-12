# Plan 02: Remove Company from Insurance UI + Fix Deactivate Button

## Objective

1. Remove the "Company" column, filter, and form field from all insurance-related pages
2. Wire up the Deactivate button on the insurance detail page

## Files to Read First

| File | Reason |
|------|--------|
| `frontend/src/components/features/insurances/insurance-list.tsx` | List page with Company column + filter |
| `frontend/src/components/features/insurances/insurance-form.tsx` | Form with Company dropdown (used by new + edit) |
| `frontend/src/components/features/insurances/insurance-detail.tsx` | Detail page with Company field + Deactivate button |
| `frontend/src/components/features/estimations/estimation-form.tsx` | Estimation wizard with Company dropdown |
| `frontend/src/lib/api/insurances.ts` | Response types (to confirm `companyName` field) |
| `frontend/src/lib/schemas/insurance.ts` | Zod schema (check if `companyId` is required) |
| `frontend/src/lib/schemas/estimation.ts` | Zod schema (check if `companyId` is required) |

## Files to Modify

1. `frontend/src/components/features/insurances/insurance-list.tsx`
2. `frontend/src/components/features/insurances/insurance-form.tsx`
3. `frontend/src/components/features/insurances/insurance-detail.tsx`
4. `frontend/src/components/features/estimations/estimation-form.tsx`

Possible schema updates:
5. `frontend/src/lib/schemas/insurance.ts`
6. `frontend/src/lib/schemas/estimation.ts`

## Steps

### Step 1: Remove Company from insurance-list.tsx

Open `frontend/src/components/features/insurances/insurance-list.tsx`.

**1a. Remove the "Company" column from the `columns` array.** Delete these lines (currently line 42-45):
```typescript
columnHelper.accessor("companyName", {
  header: "Company",
  cell: (info) => info.getValue() ?? "—",
}),
```

**1b. Remove the `companyId` state variable.** Delete this line (currently line 76):
```typescript
const [companyId, setCompanyId] = useState<string>("");
```

**1c. Remove `companyId` from the query key.** In the `useQuery` call (around line 82), change:
```typescript
queryKey: ["insurances", pagination.pageIndex, pagination.pageSize, search, typeId, companyId, sortField, sortDirection],
```
to:
```typescript
queryKey: ["insurances", pagination.pageIndex, pagination.pageSize, search, typeId, sortField, sortDirection],
```

**1d. Remove `companyId` from the `getInsurances` call.** In the `queryFn` (around lines 83-92), change:
```typescript
getInsurances(
  pagination.pageIndex,
  pagination.pageSize,
  typeId ? Number(typeId) : undefined,
  companyId ? Number(companyId) : undefined,
  search || undefined,
  sortField,
  sortDirection,
),
```
to:
```typescript
getInsurances(
  pagination.pageIndex,
  pagination.pageSize,
  typeId ? Number(typeId) : undefined,
  undefined, // companyId — removed
  search || undefined,
  sortField,
  sortDirection,
),
```

Wait — since `getInsurances` still has the `companyId` parameter (just unused), passing `undefined` is cleaner. But the function signature still expects it. To avoid breaking callers, pass `undefined`.

**1e. Remove `companyId` from the `initialData` condition.** In the `initialData` line (currently around line 94), change:
```typescript
pagination.pageIndex === 0 && !search && !typeId && !companyId && !sortField ? initialData : undefined,
```
to:
```typescript
pagination.pageIndex === 0 && !search && !typeId && !sortField ? initialData : undefined,
```

**1f. Remove the company filter Select from the toolbar.** Delete the entire company `<Select>` block (currently lines 189-205):
```typescript
<Select
  value={companyId || undefined}
  onValueChange={...}
>
  <SelectTrigger className="w-[180px]">
    <SelectValue placeholder="All companies" />
  </SelectTrigger>
  <SelectContent>
    <SelectItem value="">All companies</SelectItem>
    {companies?.map((company) => (
      <SelectItem key={company.id} value={company.id.toString()}>{company.name}</SelectItem>
    ))}
  </SelectContent>
</Select>
```

Also remove the empty-state check that references `companyId`. In line 132, change:
```typescript
{!isLoading && insurances.length === 0 && !search && !typeId && !companyId ? (
```
to:
```typescript
{!isLoading && insurances.length === 0 && !search && !typeId ? (
```

**1g. Remove the `getInsuranceCompanies` import and query.** 
- In the import at line 7, change:
  ```typescript
  import { getInsurances, getInsuranceTypes, getInsuranceCompanies, type InsuranceResponse } from "@/lib/api/insurances";
  ```
  to:
  ```typescript
  import { getInsurances, getInsuranceTypes, type InsuranceResponse } from "@/lib/api/insurances";
  ```

- Delete the `companies` query (currently lines 103-106):
  ```typescript
  const { data: companies } = useQuery({
    queryKey: ["insurance-companies"],
    queryFn: getInsuranceCompanies,
  });
  ```

### Step 2: Remove Company from insurance-form.tsx

Open `frontend/src/components/features/insurances/insurance-form.tsx`.

**2a. Remove `getInsuranceCompanies` from the import** (line 11):
```typescript
import {
  createInsurance,
  updateInsurance,
  getInsuranceTypes,
  getInsuranceCompanies,  // <-- DELETE THIS LINE
  type InsuranceResponse,
  type InsuranceRequest,
} from "@/lib/api/insurances";
```

Also check line 11 — make sure to keep the closing `}` properly aligned.

**2b. Remove the `companies` query** (lines 46-49):
```typescript
const { data: companies } = useQuery({
  queryKey: ["insurance-companies"],
  queryFn: getInsuranceCompanies,
});
```

**2c. Remove the Company dropdown from the form.** Delete the entire "Company *" block inside the grid div. Currently this is the second half of the grid at lines 171-193:
```typescript
<div className="space-y-1.5">
  <label className="text-sm font-medium">Company *</label>
  <Select
    value={watch("companyId") || undefined}
    onValueChange={(value) => setValue("companyId", value ?? "")}
  >
    <SelectTrigger className="w-full">
      <SelectValue placeholder="Select company" />
    </SelectTrigger>
    <SelectContent>
      {companies?.map((company) => (
        <SelectItem key={company.id} value={company.id.toString()}>
          {company.name}
        </SelectItem>
      ))}
    </SelectContent>
  </Select>
  {errors.companyId?.message && (
    <p className="text-sm text-destructive" role="alert">
      {errors.companyId.message}
    </p>
  )}
</div>
```

After removing the company dropdown, the grid should only have one column (the Insurance Type). You have two options:
- Change the grid to remove `sm:grid-cols-2` (since there's only one item now)
- Or keep the grid and let the single dropdown render in one column. The grid div becomes a single-column container.

The simplest approach: Change the parent grid element's class from:
```html
<div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
```
to just not wrapping in a grid at all. Remove the opening `<div className="grid grid-cols-1 gap-4 sm:grid-cols-2">` and its closing `</div>` around the remaining Type dropdown. The Type dropdown's own `<div className="space-y-1.5">` already handles spacing.

**2d. Check the schema.** Open `frontend/src/lib/schemas/insurance.ts`. Look for `companyId` in the schema definition. If `companyId` is a required field (`z.string().min(1, ...)`), you need to either:
- Remove it from the schema entirely, OR
- Make it optional (`z.string().optional()`)

Also remove `companyId` from the `defaultValues` in the form (both the `initialData` branch and the empty defaults). And remove `companyId` from the mutation payload construction (`payload` object).

**Step 2d is critical** — if the schema still requires `companyId` but the form no longer collects it, the form submission will fail validation. Check `insuranceSchema` and the `InsuranceFormData` type.

Similarly, remove `companyId` from the `mutationFn` payload object:
```typescript
const payload: InsuranceRequest = {
  name: data.name,
  description: data.description || undefined,
  typeId: Number(data.typeId),
  // companyId: Number(data.companyId),  // <-- DELETE THIS LINE
  basePremium: Number(data.basePremium),
  isActive: data.isActive,
};
```

### Step 3: Remove Company from insurance-detail.tsx

Open `frontend/src/components/features/insurances/insurance-detail.tsx`.

**3a. Remove the "Company" DetailItem.** Delete line 119:
```typescript
<DetailItem label="Company" value={insurance.companyName ?? "—"} />
```

This is the only change for company removal on this page.

**3b. Fix the Deactivate button.** The button and its ConfirmDialog are already wired up structurally (the mutation is defined, the dialog opens on click, the confirm button calls `mutate()`). Investigate why it doesn't work:

Possible issues to check:
1. Is the API call failing silently? Add an `onError` handler to the `deactivateMutation` that shows the error. Currently there's no error handling.
2. Check if the API endpoint for `PUT /api/insurances/{id}` with `{ isActive: false }` actually works — the backend may reject partial updates.
3. Check if a success toast or visual feedback is missing — the dialog closes but the status badge may not update.

**Fix:** Add `onError` logging to the deactivate mutation. Replace the mutation definition (lines 37-43):
```typescript
const deactivateMutation = useMutation({
  mutationFn: () => updateInsurance(id, { isActive: false }),
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ["insurance", id] });
    queryClient.invalidateQueries({ queryKey: ["insurances"] });
    setDeactivateOpen(false);
  },
  onError: (error) => {
    // Log error — could be enhanced with toast notification
    console.error("Failed to deactivate insurance:", error);
  },
});
```

Also, after `invalidateQueries`, add `refetch()` to ensure the detail view updates immediately:
```typescript
onSuccess: () => {
  queryClient.invalidateQueries({ queryKey: ["insurance", id] });
  queryClient.invalidateQueries({ queryKey: ["insurances"] });
  setDeactivateOpen(false);
  refetch(); // Force refetch the detail view to show updated status
},
```

Add `refetch` to the destructured return from `useQuery` — verify it's already destructured at line 24. It is: `const { data: insurance, isLoading, isError, error, refetch } = useQuery(...)`. Good.

### Step 4: Remove Company from estimation-form.tsx

Open `frontend/src/components/features/estimations/estimation-form.tsx`.

**4a. Remove `getInsuranceCompanies` from the import** (line 10):
```typescript
import { getInsuranceTypes, getInsuranceCompanies } from "@/lib/api/insurances";
```
Change to:
```typescript
import { getInsuranceTypes } from "@/lib/api/insurances";
```

**4b. Remove the `companies` query** (lines 82-85):
```typescript
const { data: companies } = useQuery({
  queryKey: ["insurance-companies"],
  queryFn: getInsuranceCompanies,
});
```

**4c. Remove `companyId` from form defaultValues.** In the `useForm` call (around line 58), remove `companyId: ""` from the default values.

**4d. Remove `watchedCompanyId`.** Delete line 65:
```typescript
const watchedCompanyId = watch("companyId");
```

**4e. Remove the Company dropdown from Step 2.** Delete the entire company Select block (lines 275-292):
```typescript
<div className="space-y-1.5">
  <label className="text-sm font-medium">Company</label>
  <Select value={watchedCompanyId || undefined} ...>
    <SelectTrigger className="w-full">
      <SelectValue placeholder="Select company (optional)" />
    </SelectTrigger>
    <SelectContent>
      {companies?.map((company) => (
        <SelectItem key={company.id} value={company.id.toString()}>
          {company.name}
        </SelectItem>
      ))}
    </SelectContent>
  </Select>
</div>
```

**4f. Update the type summary in Step 2** (lines 294-303). Remove the company name display from the summary box:
```typescript
{selectedType && (
  <div className="rounded-lg bg-muted p-3 text-sm">
    <p className="font-medium">{selectedType.name}</p>
    // DELETE the conditional company name paragraph
  </div>
)}
```

**4g. Remove Company from Step 4 review.** Delete the Company block from the review section (lines 404-411):
```typescript
<div>
  <p className="text-sm font-medium text-muted-foreground">Company</p>
  <p className="text-sm">
    {watchedCompanyId
      ? companies?.find((c) => c.id.toString() === watchedCompanyId)?.name
      : "None selected"}
  </p>
</div>
```

**4h. Remove `companyId` from the mutation payload.** In `mutationFn` (around line 95), change:
```typescript
return createEstimation({
  customerId: data.customerId,
  insuranceTypeId: Number(data.insuranceTypeId),
  companyId: data.companyId ? Number(data.companyId) : undefined,  // DELETE THIS LINE
  vehicleId: data.vehicleId || undefined,
  realEstateId: data.realEstateId || undefined,
});
```

**4i. Check the schema.** Open `frontend/src/lib/schemas/estimation.ts`. Look for `companyId` in the schema definition. If present, remove it or make it optional. Update the `EstimationFormData` type accordingly.

### Step 5: Type-check

Run `cd frontend && npx tsc --noEmit` to verify no type errors remain.

## Acceptance Criteria

- [x] Insurance list page has no "Company" column and no "All companies" filter dropdown
- [x] Insurance form (new + edit) has no company dropdown field
- [x] Insurance detail page has no "Company" row in the info section
- [x] Estimation form has no company dropdown in step 2 and no company row in step 4 review
- [x] Clicking the Deactivate button on an insurance detail page opens a confirm dialog
- [x] Confirming deactivation calls the API and updates the page with the new inactive status
- [x] No `getInsuranceCompanies` imports remain in any of the four modified files
- [x] Frontend type-checks without errors

## Dependencies

None. Execute this plan before Plan 03 (SelectValue fix) to avoid fixing company dropdown ID rendering that would be removed anyway.
