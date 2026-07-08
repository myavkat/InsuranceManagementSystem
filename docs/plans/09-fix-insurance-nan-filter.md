# Plan: Fix Insurance Filter NaN Query Parameter (insurance-list.tsx)

**Source:** Sprint 8 code review — finding 14

---

## Objective

Fix a bug in the insurance list where selecting "All types" or "All companies" sends `NaN` as a query parameter to the backend API. The filter dropdown includes `<SelectItem value="all">All types</SelectItem>`, and the state tracks "all" instead of resetting to empty string. When building the API call, `Number("all")` produces `NaN`, which is then appended as `typeId=NaN` or `companyId=NaN`.

---

## Severity

🟡 **Low-Med** — Bug. The backend may silently ignore NaN (producing full results by coincidence) or reject the request as malformed.

---

## Files to Read First

| File | Why |
|------|-----|
| `frontend-next/src/components/features/insurances/insurance-list.tsx` | The file being fixed |
| `frontend-next/src/lib/api/insurances.ts` | The `getInsurances` function — confirms `typeId`/`companyId` are `number | undefined` |

---

## Steps

### Finding — Fix: "All types" / "All companies" select values cause `NaN` query params

**The bug**: The filter dropdowns for insurance type and company include "All types" and "All companies" as `<SelectItem>` options with value `"all"`. When selected:
1. State becomes `typeId = "all"` or `companyId = "all"`
2. API call: `typeId ? Number(typeId) : undefined` evaluates `"all"` as truthy → `Number("all") = NaN`
3. The query parameter `typeId=NaN` is sent to the backend

**The fix**: The select dropdown should NOT include "All types"/"All companies" as select items. Instead, the `<SelectValue placeholder="All types" />` already serves as the "no filter applied" visual when `value` is `undefined`/empty. Remove the "all" items and let selecting nothing (or clearing) reset the state to empty string.

**Exact changes** in `insurance-list.tsx`:

### Change 1 — Remove "All types" item (around line 182)

Find:
```tsx
<SelectContent>
  <SelectItem value="all">All types</SelectItem>
  {types?.map((type) => (
    <SelectItem key={type.id} value={type.id.toString()}>{type.name}</SelectItem>
  ))}
</SelectContent>
```

Replace with:
```tsx
<SelectContent>
  {types?.map((type) => (
    <SelectItem key={type.id} value={type.id.toString()}>{type.name}</SelectItem>
  ))}
</SelectContent>
```

### Change 2 — Remove "All companies" item (around line 199)

Find:
```tsx
<SelectContent>
  <SelectItem value="all">All companies</SelectItem>
  {companies?.map((company) => (
    <SelectItem key={company.id} value={company.id.toString()}>{company.name}</SelectItem>
  ))}
</SelectContent>
```

Replace with:
```tsx
<SelectContent>
  {companies?.map((company) => (
    <SelectItem key={company.id} value={company.id.toString()}>{company.name}</SelectItem>
  ))}
</SelectContent>
```

### Change 3 — Handle clearing the filter

Now that there's no "All" option, users need a way to clear their filter selection. The shadcn/ui `Select` component with `value={typeId || undefined}` already clears when the value becomes empty string — but after selecting a filter, there's no UI to go back to "no filter." Add a clear button or a "Clear filter" option:

**Option A — Add a "Clear" item at the top of each dropdown**:
```tsx
<SelectContent>
  <SelectItem value="">All types</SelectItem>
  {types?.map((type) => (
    <SelectItem key={type.id} value={type.id.toString()}>{type.name}</SelectItem>
  ))}
</SelectContent>
```
(Note: `value=""` with `onValueChange` setting `setTypeId(value ?? "")` means the empty string resets the filter.)

This is simpler and preserves the "All" UX. The key difference from the buggy version: using `value=""` instead of `value="all"`.

---

## Acceptance Criteria

- [x] Select an insurance type filter → API call includes `typeId=<number>` (correct)
- [x] Select "All types" (clear filter) → API call does NOT include `typeId` parameter
- [x] Select an insurance company filter → API call includes `companyId=<number>` (correct)
- [x] Select "All companies" → API call does NOT include `companyId` parameter
- [x] No `NaN` appears in any API request URL parameters
- [x] Existing filter behavior (resetting to page 0 on filter change) is preserved

---

## Dependencies

None — this plan is self-contained.
