# Plan 03: Fix SelectValue — Dropdown Shows ID Instead of Name

## Objective

Fix the root cause of dropdowns showing the selected item's numeric ID instead of its display name.

## Root Cause

The Base UI React `Select.Value` primitive renders the **raw value string** of the selected
`SelectItem` by default — not the item's children (the display name). So when a user selects
"Home Insurance" (value=`"3"`), the collapsed trigger shows `"3"` instead of `"Home Insurance"`.

To display the name, you must pass a `render` prop to `Select.Value` that maps the value to
the corresponding display name from the options array.

The shadcn `SelectValue` wrapper already spreads `...props`, so `render` passes through
automatically — no change needed to the wrapper component.

## The Fix Pattern

Every `<SelectValue placeholder="..." />` must become:
```tsx
<SelectValue
  placeholder="Select something"
  render={(value) => optionsArray?.find(o => o.id.toString() === value)?.name ?? ""}
/>
```

Where `optionsArray` is the data array used to populate the `SelectItem` elements (e.g., `types`,
`brands`, `models`, `cities`, etc.).

**Important behavior notes:**
- The `render` function receives the current `value` string (the same value you pass to the `Select`'s
  `value` prop).
- Return `""` (empty string) when no match is found — this causes the `placeholder` to be shown.
- The render function MUST handle `undefined`/`null` options arrays gracefully (use optional chaining
  `?.` and nullish coalescing `?? ""`).
- For edit forms loading with initial data: the options array may not be loaded yet when the form
  first renders. The render function returns `""`, showing the placeholder, and once options load,
  React re-renders with the correct name. No special handling needed.

## Files to Read First

| File | Reason |
|------|--------|
| `frontend-next/src/components/ui/select.tsx` | Confirm `...props` spreads to `SelectPrimitive.Value` |
| `frontend-next/src/components/features/insurances/insurance-form.tsx` | Has Type dropdown |
| `frontend-next/src/components/features/estimations/estimation-form.tsx` | Has Type, Customer, Vehicle, RealEstate dropdowns |
| `frontend-next/src/components/features/vehicles/vehicle-form.tsx` | Has Brand, Model, Engine, Fuel, Type, Package, Customer dropdowns |
| `frontend-next/src/components/features/real-estate/real-estate-form.tsx` | Has City, Construction Type, Luxury Class, Usage Type, Customer dropdowns |

## Files to Modify

1. `frontend-next/src/components/features/insurances/insurance-form.tsx`
2. `frontend-next/src/components/features/estimations/estimation-form.tsx`
3. `frontend-next/src/components/features/vehicles/vehicle-form.tsx`
4. `frontend-next/src/components/features/real-estate/real-estate-form.tsx`

> **Prerequisite:** Plan 02 must be completed first (removes company dropdowns that would
> otherwise need this fix).

## Detailed Steps

### Step 1: Fix insurance-form.tsx (Insurance Type dropdown)

Open `frontend-next/src/components/features/insurances/insurance-form.tsx`.

Locate the Insurance Type Select (around lines 148-163). The `SelectValue` is at line 154:
```tsx
<SelectValue placeholder="Select type" />
```

Change it to:
```tsx
<SelectValue
  placeholder="Select type"
  render={(value) => types?.find((t) => t.id.toString() === value)?.name ?? ""}
/>
```

The `types` variable is already available from the `useQuery` call. Verify the variable name is `types` (it is — line 41: `const { data: types } = useQuery(...)`).

### Step 2: Fix estimation-form.tsx

Open `frontend-next/src/components/features/estimations/estimation-form.tsx`.

This file has 4 dropdowns that need fixing (after company removal in Plan 02).

**2a. Customer dropdown (Step 1, around line 202):**
```tsx
<SelectValue placeholder="Search and select a customer" />
```
Change to:
```tsx
<SelectValue
  placeholder="Search and select a customer"
  render={(value) => {
    const c = customers.find((c) => c.id === value);
    return c ? `${c.firstName} ${c.lastName} (${c.nationalId})` : "";
  }}
/>
```
Note: `customers` from the search query is the list of filtered results. The selected customer
might not be in the filtered list if the search term changed. For safety, use the `selectedCustomer`
variable computed at line 88 as the fallback, or use `customerData?.content` directly.

Better approach — the render function should check both the filtered list and the selected customer
preview data. Keep it simple: use the `selectedCustomer` variable and also handle the case where
rendering a value that's in the current filtered list:
```tsx
<SelectValue
  placeholder="Search and select a customer"
  render={(value) => {
    // Check currently displayed list first
    const fromList = customers.find((c) => c.id === value);
    if (fromList) return `${fromList.firstName} ${fromList.lastName} (${fromList.nationalId})`;
    // Fall back to selectedCustomer (which is also from the list, but may be stale)
    if (selectedCustomer) return `${selectedCustomer.firstName} ${selectedCustomer.lastName} (${selectedCustomer.nationalId})`;
    return "";
  }}
/>
```

**2b. Insurance Type dropdown (Step 2, around line 257):**
```tsx
<SelectValue placeholder="Select insurance type" />
```
Change to:
```tsx
<SelectValue
  placeholder="Select insurance type"
  render={(value) => types?.find((t) => t.id.toString() === value)?.name ?? ""}
/>
```
The `types` variable is available from line 77.

**2c. Vehicle dropdown (Step 3, around line 323):**
This dropdown currently has no actual data being loaded (the search query is wired to `vehicleSearch`
state but there's no `useQuery` for vehicles). The dropdown only shows "Type to search vehicles by
plate" placeholder text. This is a separate bug — the vehicle search API call is not implemented.
For this plan, just add the render prop pattern. If vehicle data is empty, the render returns `""`.
```tsx
<SelectValue
  placeholder="Select a vehicle (optional)"
  render={(value) => {
    // Vehicle search is not yet implemented — placeholder for when it is
    return ""; // or: vehicles?.find(v => v.id === value)?.plate ?? ""
  }}
/>
```

**2d. Real Estate dropdown (Step 3, around line 359):**
Same situation as vehicles — no data query is wired. Add the render prop:
```tsx
<SelectValue
  placeholder="Select a property (optional)"
  render={(value) => {
    // Real estate search is not yet implemented — placeholder for when it is
    return ""; // or: properties?.find(p => p.id === value)?.address ?? ""
  }}
/>
```

### Step 3: Fix vehicle-form.tsx (all dropdowns)

Open `frontend-next/src/components/features/vehicles/vehicle-form.tsx`.

This file has 6-7 dropdowns. The options arrays are loaded via `useQuery` at the top of the component.

**3a. Customer dropdown (around line 248):**
```tsx
<SelectValue placeholder="Search and select a customer" />
```
Change to:
```tsx
<SelectValue
  placeholder="Search and select a customer"
  render={(value) => {
    const c = customers.find((c) => c.id === value);
    if (c) return `${c.firstName} ${c.lastName} (${c.nationalId})`;
    return "";
  }}
/>
```

**3b. Brand dropdown (around line 292):**
```tsx
<SelectValue placeholder="Select a brand" />
```
Change to:
```tsx
<SelectValue
  placeholder="Select a brand"
  render={(value) => brands?.find((b) => b.id.toString() === value)?.name ?? ""}
/>
```

**3c. Model dropdown (around line 312):**
```tsx
<SelectValue
  placeholder={
    !watchBrandId
      ? "Select a brand first"
      : modelsLoading
        ? "Loading models..."
        : "Select a model"
  }
/>
```
Change to:
```tsx
<SelectValue
  placeholder={
    !watchBrandId
      ? "Select a brand first"
      : modelsLoading
        ? "Loading models..."
        : "Select a model"
  }
  render={(value) => models?.find((m) => m.id.toString() === value)?.name ?? ""}
/>
```

**3d. Engine dropdown (around line 343):**
```tsx
<SelectValue placeholder="Select engine" />
```
Change to:
```tsx
<SelectValue
  placeholder="Select engine"
  render={(value) => engines?.find((e) => e.id.toString() === value)?.name ?? ""}
/>
```

**3e. Fuel Type dropdown (around line 361):**
```tsx
<SelectValue placeholder="Select fuel type" />
```
Change to:
```tsx
<SelectValue
  placeholder="Select fuel type"
  render={(value) => fuelTypes?.find((f) => f.id.toString() === value)?.name ?? ""}
/>
```

**3f. Type dropdown (around line 387):**
```tsx
<SelectValue placeholder="Select type" />
```
Change to:
```tsx
<SelectValue
  placeholder="Select type"
  render={(value) => types?.find((t) => t.id.toString() === value)?.name ?? ""}
/>
```

**3g. Package dropdown (around line 401):**
```tsx
<SelectValue placeholder="Select package" />
```
Change to:
```tsx
<SelectValue
  placeholder="Select package"
  render={(value) => packages?.find((p) => p.id.toString() === value)?.name ?? ""}
/>
```

### Step 4: Fix real-estate-form.tsx (all dropdowns)

Open `frontend-next/src/components/features/real-estate/real-estate-form.tsx`.

**4a. City dropdown (around line 182):**
```tsx
<SelectValue placeholder="Select a city" />
```
Change to:
```tsx
<SelectValue
  placeholder="Select a city"
  render={(value) => cities?.find((c) => c.id.toString() === value)?.name ?? ""}
/>
```

**4b. Customer dropdown (around line 235):**
```tsx
<SelectValue placeholder="Search and select a customer" />
```
Change to:
```tsx
<SelectValue
  placeholder="Search and select a customer"
  render={(value) => {
    const c = customers.find((c) => c.id === value);
    if (c) return `${c.firstName} ${c.lastName} (${c.nationalId})`;
    return "";
  }}
/>
```

**4c. Construction Type dropdown (around line 279):**
```tsx
<SelectValue placeholder="Select type" />
```
Change to:
```tsx
<SelectValue
  placeholder="Select type"
  render={(value) => constructionTypes?.find((t) => t.id.toString() === value)?.name ?? ""}
/>
```

**4d. Luxury Class dropdown (around line 298):**
```tsx
<SelectValue placeholder="Select class" />
```
Change to:
```tsx
<SelectValue
  placeholder="Select class"
  render={(value) => luxuryClasses?.find((c) => c.id.toString() === value)?.name ?? ""}
/>
```

**4e. Usage Type dropdown (around line 317):**
```tsx
<SelectValue placeholder="Select type" />
```
Change to:
```tsx
<SelectValue
  placeholder="Select type"
  render={(value) => usageTypes?.find((t) => t.id.toString() === value)?.name ?? ""}
/>
```

### Step 5: Verify the fix works

Start the frontend dev server and verify these scenarios:

1. **New forms**: Select an item in any dropdown → the trigger should display the name, not the ID.
2. **Edit forms**: Load a form with existing data → all dropdowns should show the name of the pre-selected item, not its ID.
3. **Clear selection**: If any dropdown supports clearing, verify it shows the placeholder, not `"undefined"` or blank.
4. **Options not loaded yet**: On edit forms with pre-selected values, while options are still loading, the dropdown may briefly show the placeholder. After options load, it should show the name. This is acceptable behavior.

## Acceptance Criteria

- [ ] Insurance Type dropdown in insurance-form shows type name (e.g., "Health Insurance"), not `"1"`
- [ ] Insurance Type dropdown in estimation-form shows type name, not `"3"`
- [ ] Customer dropdown in estimation-form shows full name + TCKN, not GUID
- [ ] Brand, Model, Engine, Fuel, Type, Package dropdowns in vehicle-form all show names
- [ ] Customer dropdown in vehicle-form shows full name + TCKN
- [ ] City, Construction Type, Luxury Class, Usage Type dropdowns in real-estate-form all show names
- [ ] Customer dropdown in real-estate-form shows full name + TCKN
- [ ] All `render` functions handle missing options (`?.` and `?? ""`) without crashing
- [ ] Frontend type-checks without errors

## Dependencies

- **Plan 02** must be completed first (removes company dropdowns that would otherwise need this fix)
