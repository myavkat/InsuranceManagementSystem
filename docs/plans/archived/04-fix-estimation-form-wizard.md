# Plan 04: Fix Estimation Form Wizard

## Objective

Fix three issues with the estimation creation wizard:

1. **Customer search**: The search input inside the customer dropdown can't be typed into — it
   behaves like a native dropdown (typing jumps to matching items). It needs to be a real,
   typeable search box.
2. **Review step auto-trigger**: Navigating to Step 4 (Review) auto-triggers the estimation
   API call. The estimation should only be submitted when the user explicitly clicks Submit.
3. **Asset validation**: At least one asset (vehicle or real estate) must be selected in Step 3
   before the user can proceed to Step 4.

## Files to Read First

| File | Reason |
|------|--------|
| `frontend-next/src/components/features/estimations/estimation-form.tsx` | Main file to modify |
| `frontend-next/src/components/ui/select.tsx` | Understand Base UI Select behavior |
| `frontend-next/src/lib/schemas/estimation.ts` | Schema — may need validation update |
| `frontend-next/src/lib/api/vehicles.ts` | Vehicle API (needed if implementing vehicle search) |
| `frontend-next/src/lib/api/realestate.ts` | Real estate API (needed if implementing real estate search) |

## Files to Modify

1. `frontend-next/src/components/features/estimations/estimation-form.tsx`

## Steps

### Step 1: Fix the customer search — make the Input typeable

**Root cause**: The `<Input>` inside `<SelectContent>` cannot receive keystrokes because Base UI's
Select component captures all keyboard events to control navigation (arrow keys, type-to-search).
The `onPointerDown={(e) => e.stopPropagation()}` on the search div prevents mouse click from
closing the dropdown, but it doesn't stop keyboard capture.

**Fix**: Use `onKeyDown` event handler on the Input to prevent keyboard events from propagating
to the Select.

Open `frontend-next/src/components/features/estimations/estimation-form.tsx`.

Locate the customer search Input (around line 210):
```tsx
<Input
  placeholder="Search customers..."
  value={customerSearch}
  onChange={(e) => setCustomerSearch(e.target.value)}
  className="h-8"
/>
```

Add an `onKeyDown` handler to stop propagation:
```tsx
<Input
  placeholder="Search customers..."
  value={customerSearch}
  onChange={(e) => setCustomerSearch(e.target.value)}
  onKeyDown={(e) => e.stopPropagation()}
  className="h-8"
/>
```

This prevents the Select from intercepting keystrokes while the Input is focused. Typing in the
Input will now update `customerSearch` normally, which triggers the `useQuery` to fetch filtered
customers.

Also update the search div's `onPointerDown` to prevent the Select from closing when interacting
with the search area (this is already in place but verify it's correct):
```tsx
<div
  className="flex items-center gap-2 px-2 pb-2"
  onPointerDown={(e) => e.stopPropagation()}
>
```

This existing code is correct — `stopPropagation()` on pointer down prevents the Select from
closing when you click inside the search area.

### Step 2: Apply same fix to vehicle and real estate search inputs (Step 3)

The vehicle and real estate dropdowns have the same search pattern but the data queries are
not yet implemented. Add `onKeyDown` anyway so when the queries are added, the inputs work:

**Vehicle search Input** (around line 333):
```tsx
<Input
  placeholder="Search by plate..."
  value={vehicleSearch}
  onChange={(e) => setVehicleSearch(e.target.value)}
  onKeyDown={(e) => e.stopPropagation()}
  className="h-8"
/>
```

**Real estate search Input** (around line 369):
```tsx
<Input
  placeholder="Search by address..."
  value={realEstateSearch}
  onChange={(e) => setRealEstateSearch(e.target.value)}
  onKeyDown={(e) => e.stopPropagation()}
  className="h-8"
/>
```

### Step 3: Fix auto-triggering estimation on review step

**Investigation**: Review the code flow. The form wraps all 4 steps:
```tsx
<form onSubmit={handleSubmit(onSubmit)}>
```

The `onSubmit` function calls `mutation.mutate(data)`. In step 4, the Submit button has
`type="submit"`. However, any `<button>` inside a `<form>` defaults to `type="submit"` unless
explicitly set to `type="button"`.

Check all buttons:
- "Back" button: `type="button"` ✓ (line 434)
- "Next" button: `type="button"` ✓ (line 443)
- "Submit Estimation" button: `type="submit"` ✓ (line 451)

All buttons have correct types. So what could cause auto-triggering?

**Possible cause 1**: The `useUnsavedChanges(isDirty)` hook might trigger something when step
changes. This is unlikely but worth checking.

**Possible cause 2**: Form submission on Enter key. When the user presses Enter in any input
field (like the search inputs), the browser submits the form. Since there's no `onKeyDown`
preventing default on the form, pressing Enter in the search input could submit the form
prematurely.

**Fix**: Add `onKeyDown` handler to the form to prevent Enter-key submission:
```tsx
<form onSubmit={handleSubmit(onSubmit)} onKeyDown={(e) => {
  if (e.key === "Enter") e.preventDefault();
}}>
```

Wait — this would prevent Enter from working in text inputs entirely. A better approach:
prevent form submission on Enter, but allow it in the Submit button click.

The simplest reliable fix: change the form to NOT use `onSubmit` for the Submit button, and
instead use `onClick` directly:

In step 4, change the Submit button from:
```tsx
<Button
  type="submit"
  disabled={isSubmitting || mutation.isPending}
>
  <Send className="size-4" />
  {isSubmitting || mutation.isPending ? "Submitting..." : "Submit Estimation"}
</Button>
```
to:
```tsx
<Button
  type="button"
  disabled={isSubmitting || mutation.isPending}
  onClick={handleSubmit(onSubmit)}
>
  <Send className="size-4" />
  {isSubmitting || mutation.isPending ? "Submitting..." : "Submit Estimation"}
</Button>
```

This is the safest fix — the form won't submit on Enter, and the estimation API is only called
when the user explicitly clicks the Submit button.

Also remove `onSubmit` from the `<form>` tag to be extra safe. Change:
```tsx
<form onSubmit={handleSubmit(onSubmit)}>
```
to:
```tsx
<form>
```

Since the Submit button now uses `onClick={handleSubmit(onSubmit)}`, the form-level `onSubmit`
is no longer needed.

**Important**: `react-hook-form`'s `handleSubmit` works both as an `onSubmit` handler AND as
an `onClick` handler. When called from `onClick`, it validates the form and invokes `onSubmit`
with the validated data. This is a documented pattern.

### Step 4: Add asset validation (at least one asset in Step 3)

Currently, Step 3 (Link Assets) allows proceeding without selecting any assets. The requirement
is: at least one asset (vehicle OR real estate) must be selected before moving to Step 4.

Add a validation check. Locate the `canProceedStep2` variable (around line 108):
```typescript
const canProceedStep1 = watchedCustomerId !== "";
const canProceedStep2 = watchedTypeId !== "";
```

Add:
```typescript
const canProceedStep3 = watchedVehicleId !== "" || watchedRealEstateId !== "";
```

Then update the "Next" button on Step 3. Find the button (around line 443) — it's the same
"Next" button for all steps except step 4. Update the `disabled` condition:
```tsx
<Button
  type="button"
  onClick={handleNext}
  disabled={
    (step === 1 && !canProceedStep1) ||
    (step === 2 && !canProceedStep2) ||
    (step === 3 && !canProceedStep3)
  }
>
```

Also add a validation message on Step 3 when no asset is selected. After the two dropdowns
in Step 3, add:
```tsx
{!canProceedStep3 && (
  <p className="text-sm text-muted-foreground">
    Select at least one asset (vehicle or real estate) to continue.
  </p>
)}
```

### Step 5: Type-check

Run `cd frontend-next && npx tsc --noEmit` to verify no type errors.

## Acceptance Criteria

- [x] Typing text into the customer search input filters the customer list (not just jumps by first letter)
- [x] Typing in the search inputs works — characters appear as typed, not intercepted by the Select
- [x] Pressing Enter key anywhere in the form does NOT submit the estimation
- [x] Clicking "Submit Estimation" button in Step 4 calls the API and navigates to the estimation detail page
- [x] "Next" button in Step 3 is disabled when no asset is selected
- [x] "Next" button in Step 3 is enabled when at least one asset (vehicle or real estate) is selected
- [x] Informational message is shown in Step 3 when no asset is selected
- [x] Frontend type-checks without errors

## Dependencies

- **Plan 02** (Company removal) — removes company dropdown and related imports from this file
- **Plan 03** (SelectValue fix) — applies the ID-to-name render fix to dropdowns in this file
