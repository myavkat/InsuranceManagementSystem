# Plan: Sprint 8 — Form Validation

**Plan ID:** `sprint8-03-form-validation`
**Priority:** 3 (builds on auth forms from Plan 01)
**Prerequisite Plans:** `sprint8-01-authentication` (login/register forms created there provide the pattern to follow)
**Blocks:** None directly

---

## Objective

Create Zod schemas for all application forms and integrate them with React Hook Form. Migrate the estimation form from manual state to Zod + RHF. Add form state persistence (navigation guard for unsaved changes). Add async validation for national ID uniqueness. This covers subtask 3 from `docs/tasks/11_SPRINT8_ADVANCED_FRONTEND.md`.

---

## Files to Read First

| File | Purpose |
|------|---------|
| `frontend-next/src/components/features/customers/customer-form.tsx` | Reference: Zod + RHF pattern already used for customers |
| `frontend-next/src/components/features/vehicles/vehicle-form.tsx` | Existing vehicle form (check if it uses Zod + RHF or manual) |
| `frontend-next/src/components/features/real-estate/real-estate-form.tsx` | Existing real estate form (check pattern) |
| `frontend-next/src/components/features/insurances/insurance-form.tsx` | Existing insurance form (check pattern) |
| `frontend-next/src/components/features/estimations/estimation-form.tsx` | Multi-step form using manual state — needs migration to Zod + RHF |
| `frontend-next/src/components/features/form-field.tsx` | Reusable form field wrapper (wraps Input + label + error) |
| `frontend-next/src/lib/schemas/auth.ts` | Auth schemas created in Plan 01 (pattern to follow) |
| `frontend-next/src/lib/api/customers.ts` | API types: CustomerRequest fields |
| `frontend-next/src/lib/api/vehicles.ts` | API types: VehicleRequest fields |
| `frontend-next/src/lib/api/realestate.ts` | API types: RealEstateRequest fields |
| `frontend-next/src/lib/api/insurances.ts` | API types: InsuranceRequest fields |
| `frontend-next/src/lib/api/estimations.ts` | API types: EstimationRequest fields |
| `frontend-next/package.json` | Verify: zod 4.4.3, react-hook-form 7.80.0, @hookform/resolvers 5.4.0 |

---

## Technical Context

### Zod 4 vs Zod 3
- Zod 4 uses `.pipe()` for schema transformations and refinements (not `.refine()` which is Zod 3)
- String methods: `z.string().min(1, "message")` (minimum length), `z.string().email("message")`
- Number handling: use `z.coerce.number()` when input comes from form fields (which are always strings)
- Optional fields: `z.string().optional()` — will be `string | undefined`
- Nullable fields: use `.nullable()` if the API expects `null` vs absent

### React Hook Form with Zod Resolver
```typescript
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";

const schema = z.object({ name: z.string().min(1) });
type FormData = z.infer<typeof schema>;

const { register, handleSubmit, formState: { errors } } = useForm<FormData>({
  resolver: zodResolver(schema),
  defaultValues: { name: "" },
});
```

### FormField Component API
```typescript
// From frontend-next/src/components/features/form-field.tsx
interface FormFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
  inputClassName?: string;
}
// Usage: <FormField label="Name" {...register("name")} error={errors.name?.message} />
```

### Navigation Guard Pattern
To prevent accidental navigation away with unsaved changes, use the browser's `beforeunload` event and Next.js navigation interception. The recommended approach:
1. Track "is dirty" state from RHF: `formState.isDirty`
2. On `beforeunload`, show browser's native confirmation dialog
3. For client-side navigation, use `router.events` (Next.js 13+) or wrap links with a click handler that checks `isDirty`

### Existing Form Analysis
- **customer-form.tsx**: ALREADY uses Zod + RHF. Reference implementation.
- **vehicle-form.tsx**, **real-estate-form.tsx**, **insurance-form.tsx**: Need to verify whether they use Zod + RHF. If they use manual state like estimation-form, migrate them.
- **estimation-form.tsx**: Uses manual `useState` for every field. Multi-step wizard. Needs full migration.

---

## Steps

### Step 1: Create centralized Zod schemas directory

- [ ] Create directory `frontend-next/src/lib/schemas/` (may already exist from Plan 01 if `auth.ts` was created there)

### Step 2: Create customer Zod schema (extract from existing form)

- [ ] Open `frontend-next/src/components/features/customers/customer-form.tsx` to see the existing inline schema (lines 25-38)
- [ ] Create `frontend-next/src/lib/schemas/customer.ts`:
  ```typescript
  import { z } from "zod";

  export const customerSchema = z.object({
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

  export type CustomerFormData = z.infer<typeof customerSchema>;
  ```
- [ ] Open `frontend-next/src/components/features/customers/customer-form.tsx`
- [ ] Replace the inline `customerSchema` and `CustomerFormData` with imports from `@/lib/schemas/customer`
- [ ] Do the same for `edit-customer-form.tsx` if it has its own inline schema — update to import the shared one
- [ ] **Verify no TypeScript errors** after the change

### Step 3: Create vehicle Zod schema

- [ ] Read `frontend-next/src/lib/api/vehicles.ts` to see the `VehicleRequest` interface fields
- [ ] Create `frontend-next/src/lib/schemas/vehicle.ts`:
  ```typescript
  import { z } from "zod";

  export const vehicleSchema = z.object({
    plate: z.string().min(1, "Plate number is required"),
    brand: z.string().min(1, "Brand is required"),
    model: z.string().min(1, "Model is required"),
    modelYear: z.coerce.number()
      .int("Model year must be a whole number")
      .min(1900, "Model year must be 1900 or later")
      .max(new Date().getFullYear() + 1, "Model year cannot be in the distant future"),
    engineNo: z.string().optional(),
    chassisNo: z.string().optional(),
    color: z.string().optional(),
    customerId: z.string().optional(), // for linking to a customer
  });

  export type VehicleFormData = z.infer<typeof vehicleSchema>;
  ```
- [ ] Open `frontend-next/src/components/features/vehicles/vehicle-form.tsx`
- [ ] If it already uses Zod + RHF, update it to import the shared schema
- [ ] If it uses manual state, migrate it to Zod + RHF following the customer-form pattern exactly:
  1. Import `useForm` + `zodResolver`
  2. Import `vehicleSchema`, `VehicleFormData`
  3. Replace `useState` fields with `register()` calls
  4. Use `FormField` component for each field
  5. Keep the same layout (Card, CardHeader, CardContent, grid)
  6. Wire `handleSubmit` to the mutation
- [ ] Do the same for `edit-vehicle-form.tsx`

### Step 4: Create real-estate Zod schema

- [ ] Read `frontend-next/src/lib/api/realestate.ts` to see the `RealEstateRequest` interface fields
- [ ] Create `frontend-next/src/lib/schemas/real-estate.ts`:
  ```typescript
  import { z } from "zod";

  export const realEstateSchema = z.object({
    address: z.string().min(1, "Address is required"),
    type: z.string().min(1, "Property type is required"),
    area: z.coerce.number()
      .positive("Area must be a positive number"),
    deedNo: z.string().optional(),
    customerId: z.string().optional(),
    // Add any other fields from the API type
  });

  export type RealEstateFormData = z.infer<typeof realEstateSchema>;
  ```
- [ ] Open `frontend-next/src/components/features/real-estate/real-estate-form.tsx`
- [ ] Migrate to Zod + RHF if not already (same pattern as customer-form)
- [ ] Do the same for `edit-real-estate-form.tsx`

### Step 5: Create insurance Zod schemas

- [ ] Read `frontend-next/src/lib/api/insurances.ts` to see various request interfaces
- [ ] Create `frontend-next/src/lib/schemas/insurance.ts`:
  ```typescript
  import { z } from "zod";

  export const insuranceTypeSchema = z.object({
    name: z.string().min(1, "Type name is required"),
    description: z.string().optional(),
  });

  export const insuranceCompanySchema = z.object({
    name: z.string().min(1, "Company name is required"),
    code: z.string().min(1, "Company code is required"),
    contactEmail: z.string().email("Invalid email").optional().or(z.literal("")),
    contactPhone: z.string().optional(),
  });

  export type InsuranceTypeFormData = z.infer<typeof insuranceTypeSchema>;
  export type InsuranceCompanyFormData = z.infer<typeof insuranceCompanySchema>;
  ```
- [ ] Open `frontend-next/src/components/features/insurances/insurance-form.tsx` and migrate to Zod + RHF
- [ ] Do the same for `edit-insurance-form.tsx`

### Step 6: Create estimation Zod schema and migrate the multi-step form

- [ ] Read the estimation form at `frontend-next/src/components/features/estimations/estimation-form.tsx`
- [ ] Analyze the fields across all 4 steps:
  - Step 1 (Customer): `selectedCustomerId` (string, required)
  - Step 2 (Insurance): `selectedTypeId` (string, required), `selectedCompanyId` (string, optional)
  - Step 3 (Link Assets): `selectedVehicleId` (string, optional), `selectedRealEstateId` (string, optional)
  - Step 4 (Review): no new fields
- [ ] Create `frontend-next/src/lib/schemas/estimation.ts`:
  ```typescript
  import { z } from "zod";

  export const estimationSchema = z.object({
    customerId: z.string().min(1, "Customer is required"),
    insuranceTypeId: z.string().min(1, "Insurance type is required"),
    companyId: z.string().optional(),
    vehicleId: z.string().optional(),
    realEstateId: z.string().optional(),
  });

  export type EstimationFormData = z.infer<typeof estimationSchema>;
  ```
- [ ] Open `frontend-next/src/components/features/estimations/estimation-form.tsx`
- [ ] Replace all `useState` calls with a single `useForm<EstimationFormData>`:
  ```typescript
  const {
    register,
    handleSubmit,
    setValue,
    watch,
    formState: { errors },
  } = useForm<EstimationFormData>({
    resolver: zodResolver(estimationSchema),
    defaultValues: {
      customerId: "",
      insuranceTypeId: "",
      companyId: "",
      vehicleId: "",
      realEstateId: "",
    },
  });
  ```
- [ ] Replace `selectedCustomerId` state → `watch("customerId")`
- [ ] Replace `selectedTypeId` state → `watch("insuranceTypeId")`
- [ ] Replace `selectedCompanyId` state → `watch("companyId")`
- [ ] Replace `selectedVehicleId` state → `watch("vehicleId")`
- [ ] Replace `selectedRealEstateId` state → `watch("realEstateId")`
- [ ] Replace all `setSelectedCustomerId(value)` calls → `setValue("customerId", value)`
- [ ] Replace the mutation to use `handleSubmit`:
  ```typescript
  const mutation = useMutation({
    mutationFn: (data: EstimationFormData) => {
      return createEstimation({
        customerId: data.customerId,
        insuranceTypeId: Number(data.insuranceTypeId),
        companyId: data.companyId ? Number(data.companyId) : undefined,
        vehicleId: data.vehicleId || undefined,
        realEstateId: data.realEstateId || undefined,
      });
    },
    onSuccess: (result) => {
      router.push(`/estimations/${result.id}`);
    },
  });

  // In JSX:
  <form onSubmit={handleSubmit((data) => mutation.mutate(data))}>
  ```
- [ ] Keep the step mechanism as-is (step 1-4 with Next/Back buttons) — just wire it to RHF
- [ ] Update step validation:
  - Replace `canProceedStep1` → `!!watch("customerId")`
  - Replace `canProceedStep2` → `!!watch("insuranceTypeId")`
- [ ] The step 4 submit button should call `handleSubmit` (which validates ALL fields, not just the current step's)
- [ ] **IMPORTANT**: Since the multi-step form only shows some fields at a time, RHF's full validation on submit works correctly — it validates ALL fields from ALL steps. If you want per-step validation, you need partial schemas. For simplicity, validate all fields at once on final submit.

### Step 7: Add navigation guard (unsaved changes warning)

- [ ] Create a custom hook `frontend-next/src/hooks/use-unsaved-changes.ts`:
  ```typescript
  "use client";

  import { useEffect, useCallback } from "react";
  import { useRouter } from "next/navigation";

  /**
   * Warn the user before navigating away when the form has unsaved changes.
   *
   * @param isDirty - Whether the form has been modified (from RHF formState.isDirty)
   * @param message - Custom warning message (only used by beforeunload; browsers ignore custom messages in modern versions)
   */
  export function useUnsavedChanges(isDirty: boolean, message = "You have unsaved changes. Leave anyway?") {
    // Browser-level: warn on tab close / refresh / back button
    useEffect(() => {
      if (!isDirty) return;

      const handler = (e: BeforeUnloadEvent) => {
        e.preventDefault();
        // Modern browsers ignore the message string, but setting returnValue triggers the dialog
        e.returnValue = message;
        return message;
      };

      window.addEventListener("beforeunload", handler);
      return () => window.removeEventListener("beforeunload", handler);
    }, [isDirty, message]);

    // Next.js client-side navigation interception:
    // This approach uses a click interceptor on all links.
    // Alternative: window.confirm in a custom Link wrapper.
    //
    // For this plan, the beforeunload handler is sufficient.
    // The application doesn't have in-app navigation between pages while editing
    // (forms are on their own routes), so the beforeunload covers the main cases.
  }
  ```
- [ ] Add the hook to each form component:
  ```typescript
  const { formState: { isDirty } } = useForm(...);
  useUnsavedChanges(isDirty);
  ```
- [ ] Add this to: `customer-form.tsx`, `vehicle-form.tsx`, `real-estate-form.tsx`, `insurance-form.tsx`, `estimation-form.tsx`, and both edit-form variants

### Step 8: Add async validation for national ID uniqueness (Customer form)

- [ ] Create `frontend-next/src/lib/api/customers.ts` — add a new function if it doesn't exist:
  ```typescript
  export async function checkNationalId(nationalId: string): Promise<boolean> {
    // Returns true if the nationalId is available (not taken)
    // The backend should have an endpoint like GET /api/customers/check-national-id?id=xxx
    // If the endpoint doesn't exist yet, skip this step and note it as TODO
    try {
      const result = await apiClient<{ available: boolean }>(
        `/api/customers/check-national-id?nationalId=${encodeURIComponent(nationalId)}`
      );
      return result.available;
    } catch {
      return false; // Assume taken if check fails
    }
  }
  ```
- [ ] In the customer schema, add async validation using RHF's `useForm` async validation (not Zod, since Zod is synchronous):
  - Use React Hook Form's `trigger` + `setError` pattern for async validation on blur
  - Or use Zod's `z.string().refine()` with a promise (Zod 4 supports async refinements via `.refine(async (val) => ..., { message: "..." })`)
- [ ] In `customer-form.tsx`, add a blur handler on the nationalId field:
  ```typescript
  const handleNationalIdBlur = async (e: React.FocusEvent<HTMLInputElement>) => {
    const value = e.target.value;
    if (value.length === 11 && /^\d{11}$/.test(value)) {
      const available = await checkNationalId(value);
      if (!available) {
        setError("nationalId", { message: "This TCKN is already registered" });
      }
    }
  };
  ```
- [ ] Pass this to `FormField` via the standard `onBlur` prop: `{...register("nationalId", { onBlur: handleNationalIdBlur })}`

### Step 9: Verify all forms

- [ ] Run `npx tsc --noEmit` from `frontend-next/` to check for TypeScript errors
- [ ] Verify each form:
  1. All fields have labels
  2. Inline errors appear per-field (not just at the top)
  3. Submit button is disabled while `isSubmitting` or `mutation.isPending`
  4. Loading state shows during reference data fetch
  5. Error state shows API errors via `ErrorAlert`
  6. Empty/initial state shows placeholder text in fields
- [ ] Verify the estimation multi-step form still navigates through steps correctly after RHF migration

---

## Acceptance Criteria

1. All Zod schemas centralized in `frontend-next/src/lib/schemas/` — one file per domain:
   - `auth.ts` (from Plan 01)
   - `customer.ts`
   - `vehicle.ts`
   - `real-estate.ts`
   - `insurance.ts`
   - `estimation.ts`
2. All forms use Zod + React Hook Form — no form uses raw `useState` for field values
3. `estimation-form.tsx` fully migrated from manual state to RHF
4. `customer-form.tsx` imports the shared schema instead of inline
5. All edit-form variants import shared schemas
6. `useUnsavedChanges` hook created and added to all form components
7. National ID async validation added to customer form (blur trigger)
8. All forms show: inline validation errors, loading skeleton during data fetch, ErrorAlert on API error, disabled submit while submitting
9. No TypeScript errors
10. Multi-step estimation form navigation still works after RHF migration

---

## Common Mistakes to Avoid

- **DO NOT** use `z.coerce.number()` without `.int()` — form fields are strings, coercion can produce decimals
- **DO NOT** forget to call `setValue` after `onValueChange` in Select components — RHF doesn't know about shadcn Select natively
- **DO NOT** use Zod 3 `.refine()` — Zod 4 uses `.pipe()` for schema refinement chains
- **DO NOT** inline schemas in form components — import from `@/lib/schemas/`
- **DO NOT** forget to register Select fields with RHF — use `setValue` + `watch` pattern (as shown in customer-form.tsx Select usage)
- **DO NOT** call `handleSubmit` until the final step in multi-step forms unless you want full validation
- **DO NOT** remove the `"use client"` directive from form components — RHF and hooks require client
- **DO NOT** use `useState` for form field values after migration — use `watch()` / `setValue()` / `register()`
- **DO NOT** create separate schema copies for edit forms — edit forms can use the same schema, just populate `defaultValues` from `initialData`
