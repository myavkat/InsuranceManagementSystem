# Plan: Fix Unsaved Changes Hook — Next.js Router Integration (use-unsaved-changes.ts)

**Source:** Sprint 8 code review — finding 8

---

## Objective

Fix the `useUnsavedChanges` hook so it intercepts **Next.js client-side navigation** (router.push, Link clicks, back button) in addition to browser-level navigation (beforeunload for tab close/refresh). Currently, users can lose form data by clicking a sidebar link without any warning.

---

## Severity

🟠 **Medium** — Data loss UX. Users filling forms lose unsaved work when navigating via the app's own sidebar or links, with no warning dialog.

---

## Files to Read First

| File | Why |
|------|-----|
| `frontend-next/src/hooks/use-unsaved-changes.ts` | The file being fixed (26 lines) |
| `frontend-next/src/components/features/customers/customer-form.tsx` | Example caller — `useUnsavedChanges(isDirty)` |
| `frontend-next/src/components/features/estimations/estimation-form.tsx` | Example caller |
| `node_modules/next/dist/docs/` (per `frontend-next/AGENTS.md`) | Next.js 16 navigation docs — check for unsaved-changes API |

---

## Steps

### Finding — Fix: Only intercepts beforeunload, not Next.js router (entire hook)

**The bug**: The hook registers a `beforeunload` event listener (lines 13-25), which fires on tab close, page refresh, and browser back/forward. However, it does NOT intercept Next.js client-side navigation: `router.push()`, `router.replace()`, `<Link>` clicks, or `router.back()`. These are the primary navigation mechanisms users use within the app. When a form is dirty (`isDirty=true`) and the user clicks a sidebar link, the navigation proceeds silently and all form data is lost.

**The fix**: Use Next.js's `useRouter` and `usePathname` hooks, plus a `beforePopState` or navigation-blocking approach. In Next.js App Router, the recommended pattern is to use the `navigator` experimental API (if available) or intercept `router.push` via a wrapper.

**Practical fix for Next.js App Router**:

Since Next.js App Router does not have a built-in `router.events` like Pages Router, the approach is:

1. Patch `window.history.pushState` and `window.history.replaceState` to detect client-side navigation
2. Then listen for `popstate` for back/forward button
3. When dirty, use `window.confirm()` to block navigation

Replace the entire file content with:

```typescript
"use client";

import { useEffect, useRef } from "react";

/**
 * Warn the user before navigating away when the form has unsaved changes.
 * 
 * Covers:
 * - Browser tab close / refresh (beforeunload)
 * - Browser back/forward (popstate)
 * - Next.js client-side Link clicks and router.push (history.pushState)
 *
 * @param isDirty - Whether the form has been modified (from RHF formState.isDirty)
 * @param message - Warning message (used by beforeunload and confirm dialogs)
 */
export function useUnsavedChanges(
  isDirty: boolean,
  message = "You have unsaved changes. Leave anyway?"
) {
  const isDirtyRef = useRef(isDirty);
  isDirtyRef.current = isDirty;

  // 1. Browser-level: warn on tab close / refresh / back button
  useEffect(() => {
    if (!isDirty) return;

    const handler = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = message;
      return message;
    };

    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [isDirty, message]);

  // 2. Next.js client-side: warn on Link clicks and router.push
  // We intercept history.pushState and history.replaceState,
  // which Next.js uses under the hood for client-side navigation.
  useEffect(() => {
    if (!isDirty) return;

    const originalPushState = window.history.pushState.bind(window.history);
    const originalReplaceState = window.history.replaceState.bind(window.history);

    const confirmNavigation = (): boolean => {
      if (isDirtyRef.current) {
        return window.confirm(message);
      }
      return true;
    };

    window.history.pushState = function (...args) {
      if (!confirmNavigation()) return;
      return originalPushState(...args);
    };

    window.history.replaceState = function (...args) {
      if (!confirmNavigation()) return;
      return originalReplaceState(...args);
    };

    // Handle browser back/forward buttons
    const handlePopState = (e: PopStateEvent) => {
      if (isDirtyRef.current && !window.confirm(message)) {
        // Push a new state to cancel the navigation
        window.history.pushState(null, "", window.location.href);
        e.preventDefault();
      }
    };

    window.addEventListener("popstate", handlePopState);

    return () => {
      window.history.pushState = originalPushState;
      window.history.replaceState = originalReplaceState;
      window.removeEventListener("popstate", handlePopState);
    };
  }, [isDirty, message]);
}
```

**Important caveat**: This approach patches `window.history.pushState`. Next.js may have its own navigation interception mechanism in v16 — check `node_modules/next/dist/docs/` per the `frontend-next/AGENTS.md` instruction. If Next.js provides a first-party API (e.g., `useNavigationGuard`), use that instead.

---

## Acceptance Criteria

- [ ] Fill a form (isDirty=true), click a sidebar `<Link>` → confirmation dialog appears
- [ ] Click "Cancel" on the dialog → navigation is blocked, user stays on the form
- [ ] Click "OK" on the dialog → navigation proceeds
- [ ] Fill a form, press browser back button → confirmation dialog appears
- [ ] Fill a form, close the browser tab → browser's built-in "unsaved changes" dialog appears
- [ ] Form is clean (isDirty=false) → no confirmation dialog on any navigation (no regression)
- [ ] Component unmount properly restores original `history.pushState` (no memory leak / broken navigation after form is closed)

---

## Dependencies

None — this plan is self-contained. However, test with the forms that use this hook:
- `customer-form.tsx`
- `insurance-form.tsx`
- `real-estate-form.tsx`
- `vehicle-form.tsx`
- `estimation-form.tsx`
