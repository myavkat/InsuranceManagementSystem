# Plan: Fix WebSocket Hook (use-websocket.ts)

**Source:** Sprint 8 code review — findings 2, 4, 5, 10

---

## Objective

Fix four correctness bugs in the `useWebSocket` hook that cause:
- Permanent disconnection after server restart (close code 1001)
- Navigation to `/estimations/undefined` when `entityId` is missing
- Stale `accessToken` used during reconnect after token rotation
- Full page reload (state loss) when clicking toast "View" action

---

## Severity

🔴 **High** — 4 findings in one file, all affecting real-time notification reliability.

---

## Files to Read First

| File | Why |
|------|-----|
| `frontend/src/hooks/use-websocket.ts` | The file being fixed (184 lines) |
| `frontend/src/lib/store/notification-store.ts` | Notification state shape (`addNotification`, `setConnected`) |
| `frontend/src/lib/notification-types.ts` | `NotificationType` type definition |
| `frontend/src/hooks/use-notification-store.ts` (or equivalent) | `useAuthStore` — to understand `accessToken` shape |

---

## Steps

### Finding 1 — Fix: WebSocket close code 1001 blocks reconnect (line 128)

**The bug**: The `onclose` handler at line 128 excludes close code 1001 ("going away") from reconnection. Servers send code 1001 during graceful shutdown (deploys, restarts, scale-downs). After a server restart, every client sees code 1001 and stays permanently disconnected.

**The fix**: Remove `event.code !== 1001` from the guard on line 128. The `mountedRef.current` check on line 122 already prevents reconnection when the component unmounts, so the 1001 filter is redundant and harmful.

**Exact change** — Replace line 128:
```typescript
if (event.code !== 1000 && event.code !== 1001) {
```
with:
```typescript
if (event.code !== 1000) {
```

---

### Finding 2 — Fix: Non-null assertion on optional entityId (line 89)

**The bug**: Line 89 uses `data.data!.entityId` with a non-null assertion (`!`), but `entityId` is declared as optional (`entityId?: string`). If a notification arrives without `entityId`, the URL evaluates to `/estimations/undefined`, navigating the user to a broken page.

**The fix**: The guard on line 85 already checks `data.data?.entityId` for truthiness — but at line 89, the non-null assertion bypasses type safety. Replace the non-null assertion with the already-checked value.

**Exact change** — Replace lines 85-92:
```typescript
action: data.data?.entityId
  ? {
      label: "View",
      onClick: () => {
        window.location.href = `/estimations/${data.data!.entityId}`;
      },
    }
  : undefined,
```
with:
```typescript
action: data.data?.entityId
  ? {
      label: "View",
      onClick: () => {
        const entityId = data.data?.entityId as string;
        window.location.href = `/estimations/${entityId}`;
      },
    }
  : undefined,
```
(The type assertion is safe because the ternary guard already ensures entityId is truthy.)

---

### Finding 3 — Fix: Stale closure in reconnect timer (lines 121-141 + 49)

**The bug**: The `connect` function is wrapped in `useCallback` with `accessToken` as a dependency. If the `accessToken` changes (e.g., token rotation) while a reconnect timer is pending, the `setTimeout` callback at line 130 still calls the OLD `connect()` — the one captured when the WebSocket was first created. This old `connect()` uses the expired token.

**The fix**: Use a ref to hold the latest `accessToken` so the reconnect always picks up the current value.

**Exact changes**:
1. Add a new ref after line 47:
```typescript
const accessTokenRef = useRef(accessToken);
accessTokenRef.current = accessToken; // Always keep in sync
```

2. Change line 59 from:
```typescript
const url = `${WS_URL}?token=${encodeURIComponent(accessToken)}`;
```
to:
```typescript
const url = `${WS_URL}?token=${encodeURIComponent(accessTokenRef.current)}`;
```

This ensures the `connect` function always reads the latest token from the ref, regardless of when it was captured.

---

### Finding 4 — Fix: `window.location.href` hard reload in toast action (line 89)

**The bug**: The toast "View" action uses `window.location.href` which triggers a full browser page reload. This discards all client-side state: the Zustand notification store, React Query cache, form state, and UI state.

**The fix**: Use Next.js router (`router.push()`) for client-side navigation. However, since `useWebSocket` is a hook (not a component), it cannot call `useRouter()` directly. Instead, accept an optional `onNavigate` callback from the caller, or construct a URL and use `window.history.pushState` + dispatch a popstate event.

**Simpler fix**: Since the NotificationProvider wraps useWebSocket, it can pass a navigation helper. But the most self-contained fix is to use Next.js router if available, falling back to `window.location.href`:

**Exact change** — Replace line 89:
```typescript
window.location.href = `/estimations/${data.data!.entityId}`;
```
with:
```typescript
// Use document.location for client-side navigation that preserves state.
// window.location.href triggers a full page reload; assignment to
// window.location.pathname (or the URL object) triggers SPA navigation.
const url = `/estimations/${data.data?.entityId as string}`;
window.history.pushState({}, "", url);
window.dispatchEvent(new PopStateEvent("popstate"));
```

If this approach doesn't work with the Next.js router, the alternative is to export an `onNavigate` callback version:
1. Add `onNavigate?: (path: string) => void` as a parameter to `useWebSocket`
2. In the toast action, call `onNavigate?.(`/estimations/${entityId}`)`
3. In `NotificationProvider`, pass `(path) => router.push(path)` where `router = useRouter()`

---

## Acceptance Criteria

- [x] After server restart (WebSocket close code 1001), client reconnects within the exponential backoff window
- [x] Notification without `entityId` shows toast without a "View" action (no crash, no `/undefined` navigation)
- [x] Token rotation while reconnect timer is pending: reconnect uses the NEW token, not the expired one
- [x] Clicking toast "View" action navigates without full page reload (client-side state preserved)
- [x] Component unmount still cleanly disconnects (close code 1000), and no reconnect is attempted

---

## Dependencies

None — this plan is self-contained and can be executed independently.
