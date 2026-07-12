# Plan: Sprint 8 — Real-time Notifications

**Plan ID:** `sprint8-05-notifications`
**Priority:** 5 (depends on auth for authenticated WebSocket connection)
**Prerequisite Plans:** `sprint8-01-authentication` (auth token needed for WebSocket auth)
**Blocks:** None

---

## Objective

Add real-time notification capabilities: WebSocket connection to the API Gateway, a Zustand store for notification state with badge count, and a toast component (Sonner) for transient notifications. Wire notification types for estimation status changes, validation failures, and system alerts. This covers subtask 2 from `docs/tasks/11_SPRINT8_ADVANCED_FRONTEND.md`.

---

## Files to Read First

| File | Purpose |
|------|---------|
| `frontend/src/components/layout/header.tsx` | Existing header with Bell icon (placeholder), user section |
| `frontend/src/lib/store/ui-store.ts` | Existing UI store pattern (Zustand) |
| `frontend/src/lib/store/auth-store.ts` | Auth store — used to get accessToken for WebSocket auth |
| `frontend/src/lib/providers/index.tsx` | Root providers wrapper — new providers added here |
| `frontend/src/app/layout.tsx` | Root layout where toast/sonner provider mounts |
| `frontend/package.json` | Dependencies (NOTE: sonner is NOT installed yet) |
| `frontend/components.json` | shadcn/ui config |
| `docs/outlines/05_NEXTJS_FRONTEND.md` | Architecture: Zustand for client state |

---

## Technical Context

### Sonner (Toast Library)
- Package: `sonner` (not `react-hot-toast` or `react-toastify`)
- Import: `import { Toaster, toast } from "sonner"`
- Usage: `toast.success("Message")`, `toast.error("Message")`, `toast("Message")`
- The `<Toaster />` component mounts once at the root layout level
- Sonner is recommended because it's the standard companion to shadcn/ui and has a small bundle size (~2KB gzipped)

### WebSocket Approach
The task mentions "WebSocket connection from frontend to API Gateway (or dedicated push service)." Since the backend may not have a WebSocket endpoint yet, this plan builds the client-side infrastructure that's ready to connect when the backend endpoint is available:

1. A custom React hook `useWebSocket` that manages connection lifecycle
2. Connection authenticated via JWT from the auth store
3. Reconnection with exponential backoff
4. Message dispatch to the Zustand notification store

### Notification Types
From the task spec:
- **Estimation status changes** — e.g., "Estimation #1234 approved", "Estimation #5678 rejected"
- **Validation failures** — e.g., "Validation failed for customer John Doe: missing required documents"
- **System alerts** — e.g., "Scheduled maintenance in 30 minutes", "New system update available"

### Zustand Store Design
The notification store holds:
- `notifications: Notification[]` — persisted list of received notifications
- `unreadCount: number` — badge count for the header Bell icon
- `addNotification(notification)` — adds a notification, increments unread
- `markAsRead(id)` — marks one notification as read
- `markAllAsRead()` — clears unread count
- `clearNotifications()` — removes all notifications

---

## Steps

### Step 1: Install sonner

- [x] Open terminal in `frontend/`
- [x] Run: `npm install sonner`
- [x] Verify `sonner` appears in `package.json` dependencies

### Step 2: Create the notification Zustand store

- [x] Create `frontend/src/lib/store/notification-store.ts`:

### Step 3: Create the WebSocket hook

- [x] Create directory `frontend/src/hooks/` (may already exist from Plan 03)
- [x] Create `frontend/src/hooks/use-websocket.ts`:

  import { useEffect, useRef, useCallback } from "react";
  import { useAuthStore } from "@/lib/store/auth-store";
  import { useNotificationStore, type NotificationType } from "@/lib/store/notification-store";
  import { toast } from "sonner";

  // The WebSocket endpoint on the API Gateway
  // NOTE: This URL will need to be updated when the backend WebSocket endpoint is available.
  // For now, use a configurable URL that can be set via environment variable.
  const WS_URL = process.env.NEXT_PUBLIC_WS_URL || "ws://localhost:8080/ws/notifications";

  // Reconnection config
  const MAX_RECONNECT_DELAY = 30_000; // 30 seconds
  const INITIAL_RECONNECT_DELAY = 1_000; // 1 second

  // Expected message format from the server
  interface ServerNotification {
    type: NotificationType;
    title: string;
    message: string;
    data?: {
      entityType?: string;
      entityId?: string;
    };
  }

  /**
   * Custom hook for WebSocket notifications.
   *
   * Connects to the notification WebSocket endpoint with JWT auth,
   * handles reconnection with exponential backoff, and dispatches
   * incoming messages to the notification store and toast system.
   *
   * Call this ONCE in a top-level provider or layout component.
   */
  export function useWebSocket() {
    const accessToken = useAuthStore((s) => s.accessToken);
    const addNotification = useNotificationStore((s) => s.addNotification);
    const setConnected = useNotificationStore((s) => s.setConnected);

    const wsRef = useRef<WebSocket | null>(null);
    const reconnectDelayRef = useRef(INITIAL_RECONNECT_DELAY);
    const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const mountedRef = useRef(true);

    const connect = useCallback(() => {
      if (!accessToken) return; // Don't connect without auth

      // Clean up existing connection
      if (wsRef.current) {
        wsRef.current.close();
        wsRef.current = null;
      }

      // Build URL with auth token as query param (alternative: send token in first message)
      const url = `${WS_URL}?token=${encodeURIComponent(accessToken)}`;

      try {
        const ws = new WebSocket(url);
        wsRef.current = ws;

        ws.onopen = () => {
          if (!mountedRef.current) return;
          setConnected(true);
          reconnectDelayRef.current = INITIAL_RECONNECT_DELAY; // Reset on successful connection
        };

        ws.onmessage = (event) => {
          if (!mountedRef.current) return;

          try {
            const data: ServerNotification = JSON.parse(event.data);

            // Add to persistent notification store
            addNotification(data);

            // Show toast for transient notification
            const toastMessage = `${data.title}: ${data.message}`;
            switch (data.type) {
              case "estimation_status":
                toast(data.title, {
                  description: data.message,
                  action: data.data?.entityId
                    ? { label: "View", onClick: () => {
                        window.location.href = `/estimations/${data.data!.entityId}`;
                      }}
                    : undefined,
                });
                break;
              case "validation_failure":
                toast.error(data.title, {
                  description: data.message,
                  duration: 6000, // Keep error toasts longer
                });
                break;
              case "system_alert":
                toast(data.title, {
                  description: data.message,
                  duration: 5000,
                });
                break;
              default:
                toast(data.title, { description: data.message });
            }
          } catch {
            // Ignore malformed messages — don't crash the WebSocket handler
            console.warn("Received malformed WebSocket message:", event.data);
          }
        };

        ws.onerror = () => {
          // The onclose handler will fire after this and handle reconnection
          // Don't call setConnected(false) here — wait for onclose
        };

        ws.onclose = (event) => {
          if (!mountedRef.current) return;
          setConnected(false);
          wsRef.current = null;

          // Only reconnect on abnormal closure (not intentional close)
          // event.code 1000 = normal closure, 1001 = going away
          if (event.code !== 1000 && event.code !== 1001) {
            const delay = reconnectDelayRef.current;
            reconnectTimerRef.current = setTimeout(() => {
              if (mountedRef.current) {
                connect();
              }
            }, delay);

            // Exponential backoff: double the delay, cap at MAX
            reconnectDelayRef.current = Math.min(delay * 2, MAX_RECONNECT_DELAY);
          }
        };
      } catch {
        // WebSocket constructor can throw (e.g., invalid URL)
        if (mountedRef.current) {
          const delay = reconnectDelayRef.current;
          reconnectTimerRef.current = setTimeout(() => {
            if (mountedRef.current) {
              connect();
            }
          }, delay);
          reconnectDelayRef.current = Math.min(delay * 2, MAX_RECONNECT_DELAY);
        }
      }
    }, [accessToken, addNotification, setConnected]);

    // Connect when token changes, disconnect on unmount
    useEffect(() => {
      mountedRef.current = true;

      if (accessToken) {
        connect();
      }

      return () => {
        mountedRef.current = false;

        // Clear reconnect timer
        if (reconnectTimerRef.current) {
          clearTimeout(reconnectTimerRef.current);
          reconnectTimerRef.current = null;
        }

        // Close WebSocket cleanly
        if (wsRef.current) {
          wsRef.current.close(1000, "Component unmounted");
          wsRef.current = null;
        }

        setConnected(false);
      };
    }, [accessToken, connect, setConnected]);
  }
  ```

### Step 4: Create the notification provider component

- [x] Create `frontend/src/components/features/notification-provider.tsx`:

### Step 5: Mount Sonner Toaster and NotificationProvider in root layout

- [x] Open `frontend/src/lib/providers/index.tsx`
- [x] Add the `NotificationProvider` wrapper:
  ```tsx
  "use client";

  import { type ReactNode } from "react";
  import { QueryProvider } from "./query-provider";
  import { ThemeProvider } from "./theme-provider";
  import { NotificationProvider } from "@/components/features/notification-provider";

  export function Providers({ children }: { children: ReactNode }) {
    return (
      <ThemeProvider>
        <QueryProvider>
          <NotificationProvider>{children}</NotificationProvider>
        </QueryProvider>
      </ThemeProvider>
    );
  }
  ```

- [x] Open `frontend/src/app/layout.tsx`
- [x] Import and mount the `Toaster` component from sonner:
  ```tsx
  import { Toaster } from "sonner";
  ```
- [x] Add `<Toaster />` inside the `<body>` after `<Providers>`:
  ```tsx
  <body className="min-h-full flex flex-col">
    <Providers>{children}</Providers>
    <Toaster
      position="bottom-right"
      richColors
      closeButton
      toastOptions={{
        duration: 4000,
        classNames: {
          toast: "font-sans text-sm",
        },
      }}
    />
  </body>
  ```
- [x] The `Toaster` props:
  - `position="bottom-right"` — standard for desktop apps
  - `richColors` — uses semantic colors (success green, error red, info blue)
  - `closeButton` — manual dismiss button on each toast
  - `duration: 4000` — 4 seconds, within the 3-5s range recommended by UX guidelines

### Step 6: Update the Header with notification badge

- [x] Open `frontend/src/components/layout/header.tsx`
- [x] Import the notification store:
  ```typescript
  import { useNotificationStore } from "@/lib/store/notification-store";
  ```
- [x] Read `unreadCount` from the store:
  ```typescript
  const unreadCount = useNotificationStore((s) => s.unreadCount);
  ```
- [x] Replace the placeholder Bell button (around line 47-49):
  ```tsx
  {/* Notifications */}
  <Button variant="ghost" size="icon" aria-label={`Notifications${unreadCount > 0 ? ` (${unreadCount} unread)` : ""}`} className="relative">
    <Bell />
    {unreadCount > 0 && (
      <span className="absolute -top-0.5 -right-0.5 flex size-4 items-center justify-center rounded-full bg-destructive text-[10px] font-medium text-destructive-foreground">
        {unreadCount > 99 ? "99+" : unreadCount}
      </span>
    )}
  </Button>
  ```
- [x] Add a click handler to the Bell button that navigates to a future notifications page (or just marks all as read for now):
  ```typescript
  const { markAllAsRead } = useNotificationStore();
  const handleBellClick = () => {
    markAllAsRead();
    // Future: router.push("/notifications");
  };
  ```
- [x] Wire `onClick={handleBellClick}` to the Bell button

### Step 7: Add WebSocket connection status indicator (optional enhancement)

- [ ] In the header, read `isConnected` from the notification store (skipped — nice-to-have)
- [ ] Optionally show a small green dot on the Bell icon when connected, or a gray dot when disconnected (skipped — nice-to-have)
- [x] This is a nice-to-have; skip if it adds complexity

### Step 8: Create a notification types constants file

- [x] Create `frontend/src/lib/notification-types.ts`:
  ```typescript
  /**
   * Notification type constants used across the app.
   * These match the expected event types from the backend WebSocket endpoint.
   */
  export const NOTIFICATION_TYPES = {
    ESTIMATION_STATUS: "estimation_status",
    VALIDATION_FAILURE: "validation_failure",
    SYSTEM_ALERT: "system_alert",
  } as const;

  export type NotificationType = typeof NOTIFICATION_TYPES[keyof typeof NOTIFICATION_TYPES];
  ```
- [x] Update `notification-store.ts` to import from this file instead of defining the type inline (already done — store imports from external types file)

### Step 9: Verify and test

- [x] Run `npx tsc --noEmit` from `frontend/` to check for TypeScript errors (passed with no errors)
- [ ] Verify the Toaster renders in the root layout (visible after first toast)
- [x] Verify the notification store initializes with empty state
- [x] Verify the header shows the Bell icon with no badge (unreadCount = 0)
- [x] Verify the WebSocket hook does NOT crash when the backend is unavailable (it should silently retry in the background)
- [ ] Manually test by dispatching a notification from the browser console:
  ```javascript
  // In browser DevTools console:
  useNotificationStore.getState().addNotification({
    type: "estimation_status",
    title: "Estimation Approved",
    message: "Estimation #1234 has been approved.",
  });
  ```
  This should: increment the badge count, show a toast, and add to the notifications list.

---

## Acceptance Criteria

1. `sonner` installed and `<Toaster />` mounted in root layout
2. `notification-store.ts` created with: notifications array, unreadCount, addNotification, markAsRead, markAllAsRead, clearAll
3. `use-websocket.ts` hook created with:
   - Authenticated WebSocket connection using JWT from auth store
   - Reconnection with exponential backoff (1s → 2s → 4s → ... → 30s max)
   - JSON message parsing with error handling
   - Toast display for each notification type
   - Clean disconnect on unmount
4. `NotificationProvider` wraps children and initializes WebSocket
5. Header shows badge count on Bell icon when `unreadCount > 0`
6. Clicking Bell marks all as read (clears badge)
7. Toast notifications appear for: estimation status, validation failures, system alerts
8. No crashes when backend WebSocket endpoint is unavailable (graceful reconnection)
9. No TypeScript errors
10. WebSocket connection is established only after authentication (waits for accessToken)

---

## Common Mistakes to Avoid

- **DO NOT** forget to include `"use client"` in the WebSocket hook and notification provider — they use browser APIs and React hooks
- **DO NOT** create multiple WebSocket connections — the hook should only connect once (guarded by useEffect)
- **DO NOT** forget to clean up on unmount — close WebSocket and clear timers in the useEffect return
- **DO NOT** block the UI during WebSocket reconnection — it runs in the background silently
- **DO NOT** toast on successful connection/reconnection — it's noise. Only toast on incoming notifications.
- **DO NOT** store sensitive data in the notification store — it's in-memory only
- **DO NOT** use `window.location.href` inside a `useCallback` without considering Next.js router — but for navigation from a toast action, `window.location.href` is acceptable since the toast lives outside the React tree
- **DO NOT** call `addNotification` directly in render — it's a side effect, use it inside event handlers or effects
- **DO NOT** forget to check `mountedRef.current` before updating state in async WebSocket callbacks — prevents React warnings about setState on unmounted component
- **DO NOT** use `sonner` without the `<Toaster />` component — toasts won't render
