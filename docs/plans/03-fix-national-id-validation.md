# Plan: Fix National ID Validation (customer-form.tsx + customers.ts)

**Source:** Sprint 8 code review — findings 3, 9

---

## Objective

Fix two bugs in the TCKN (Turkish National ID) availability check:
- Race condition: stale async responses set wrong validation error
- Network errors are silently treated as "TCKN already registered"

---

## Severity

🔴 **High + Medium** — Race condition causes incorrect validation error display; network errors block form submission with false "taken" errors.

---

## Files to Read First

| File | Why |
|------|-----|
| `frontend-next/src/components/features/customers/customer-form.tsx` | The form component with the blur handler (266 lines) |
| `frontend-next/src/lib/api/customers.ts` | The `checkNationalId` API function (88 lines) |
| `frontend-next/src/lib/api/client.ts` | The `apiClient` wrapper — to understand error handling |

---

## Steps

### Finding 1 — Fix: Race condition in blur handler (customer-form.tsx lines 105-113)

**The bug**: `handleNationalIdBlur` is an `async` function that issues a network request with no cancellation or deduplication. If the user blurs TCKN "11111111111" (request A dispatched), then quickly retypes and blurs "22222222222" (request B dispatched), request A may return AFTER request B. Response B correctly shows no error for "22222222222" (it's available), but when response A arrives late it calls `setError("nationalId", ...)` with "This TCKN is already registered" — even though the field now contains the valid, available "22222222222".

**The fix**: Use an `AbortController` to cancel the previous request when a new blur occurs.

**Exact change** — Add a ref for the abort controller, and use it in the blur handler:

1. Add a new import at the top (useRef is already imported from react):
   (No import change needed — `useRef` is already imported at line 3)

2. Add a ref after line 50 (after `setError` destructuring):
```typescript
const nationalIdAbortRef = useRef<AbortController | null>(null);
```

3. Replace `handleNationalIdBlur` (lines 105-113):
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
with:
```typescript
const handleNationalIdBlur = async (e: React.FocusEvent<HTMLInputElement>) => {
    const value = e.target.value;
    if (value.length === 11 && /^\d{11}$/.test(value)) {
      // Cancel any in-flight check from a previous blur
      if (nationalIdAbortRef.current) {
        nationalIdAbortRef.current.abort();
      }
      const controller = new AbortController();
      nationalIdAbortRef.current = controller;

      try {
        const available = await checkNationalId(value, controller.signal);
        // Only set error if this is still the latest blur (not aborted)
        if (!controller.signal.aborted && !available) {
          setError("nationalId", { message: "This TCKN is already registered" });
        }
      } catch (err: unknown) {
        // If aborted, ignore — a newer blur is in flight
        if (err instanceof DOMException && err.name === "AbortError") return;
        // On real network error, don't block submission with a false error
        console.error("National ID check failed:", err);
      }
    }
  };
```

---

### Finding 2 — Fix: `checkNationalId` returns `false` on network error (customers.ts lines 76-87)

**The bug**: The `catch` block at line 84 returns `false` ("assume taken if check fails"). This means any transient network error, backend outage, or deployment-in-progress causes the form to display "This TCKN is already registered" and blocks submission. The user cannot create a customer record even though the TCKN is actually available.

**The fix**: Accept an `AbortController` signal (for the race condition fix above), and throw on real errors rather than silently returning false. Let the caller (form component) handle the error case appropriately.

**Exact change** — Replace `checkNationalId` (lines 76-87):
```typescript
export async function checkNationalId(nationalId: string): Promise<boolean> {
  // Returns true if the nationalId is available (not taken)
  // The backend should have an endpoint like GET /api/customers/check-national-id?id=xxx
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
with:
```typescript
export async function checkNationalId(
  nationalId: string,
  signal?: AbortSignal,
): Promise<boolean> {
  // Returns true if the nationalId is available (not taken).
  // Throws on network/HTTP errors — callers should handle errors gracefully
  // rather than silently treating them as "taken".
  const result = await apiClient<{ available: boolean }>(
    `/api/customers/check-national-id?nationalId=${encodeURIComponent(nationalId)}`,
    { signal },
  );
  return result.available;
}
```

Note: This requires `apiClient` in `client.ts` to accept an optional `signal` parameter. Check the existing `apiClient` signature — if it doesn't accept `signal`, you may need to add it as an optional field in the options object, or pass it via `fetch`'s `init.signal`.

---

## Acceptance Criteria

- [x] Rapidly blur two different TCKN values: only the validation result for the LATEST value is displayed — no stale error
- [x] Backend is unreachable during the check: no "already registered" error is displayed; the form remains submittable
- [x] Form submission with a valid TCKN still works (including when `checkNationalId` succeeds)
- [x] Editing an existing customer (where `initialData` is provided) does NOT trigger the availability check on blur (the check should only fire for new records — verified: `isEdit` guard added to `handleNationalIdBlur`)

---

## Dependencies

- Depends on `apiClient` in `frontend-next/src/lib/api/client.ts` supporting an `AbortSignal` parameter. ✅ Verified: `apiClient` already accepts `options: RequestInit` and spreads it into `fetch()`, so `AbortSignal` passes through natively. No changes needed.
