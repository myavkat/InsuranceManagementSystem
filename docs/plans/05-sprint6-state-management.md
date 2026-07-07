# 05 — Sprint 6: State Management Setup (Zustand + React Query)

## Status: COMPLETED

## Objective

Create Zustand stores for authentication and UI state, set up the React Query client provider, and wrap the root layout with all providers so every page has access to state management.

## Prerequisites

- **Plan 04 must be complete** (root layout exists and builds)
- Read these files before starting:
  - `docs/outlines/05_NEXTJS_FRONTEND.md` — Section 3 on Zustand + React Query conventions
  - `frontend-next/package.json` — confirm `zustand` (^5.0.14) and `@tanstack/react-query` (^5.101.0) are in dependencies
  - `frontend-next/src/app/layout.tsx` — current root layout (you will modify this)
  - `frontend-next/AGENTS.md` — Next.js 16 docs reference

## Architecture Notes

- **Zustand** manages client-only state (auth tokens, UI preferences). It replaces what would be Pinia in a Vue app.
- **React Query (TanStack Query v5)** manages server state (data fetched from the BFF). Server state does NOT go in Zustand.
- The Zustand `persist` middleware stores auth state to `localStorage` so the token survives page reloads.
- All providers are Client Components (`"use client"`) — they must be wrapped in a separate client boundary file.

## Steps

### Step 1: Create the auth store

**File to CREATE:** `frontend-next/src/lib/store/auth-store.ts`

```ts
import { create } from "zustand";
import { persist } from "zustand/middleware";

export interface UserInfo {
  userId: string;
  username: string;
  email: string;
  roles: string[];
}

export interface AuthState {
  // State
  accessToken: string | null;
  refreshToken: string | null;
  expiresAt: number | null; // Unix timestamp in milliseconds
  user: UserInfo | null;
  isAuthenticated: boolean;

  // Actions
  login: (accessToken: string, refreshToken: string, expiresIn: number, user: UserInfo) => void;
  logout: () => void;
  setAccessToken: (token: string, expiresIn: number) => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      // Initial state
      accessToken: null,
      refreshToken: null,
      expiresAt: null,
      user: null,
      isAuthenticated: false,

      // Login action — called after successful POST /api/auth/login
      login: (accessToken, refreshToken, expiresIn, user) =>
        set({
          accessToken,
          refreshToken,
          expiresAt: Date.now() + expiresIn * 1000,
          user,
          isAuthenticated: true,
        }),

      // Logout action — clears all auth state
      logout: () =>
        set({
          accessToken: null,
          refreshToken: null,
          expiresAt: null,
          user: null,
          isAuthenticated: false,
        }),

      // Refresh token rotation — called after successful POST /api/auth/refresh
      setAccessToken: (token, expiresIn) =>
        set({
          accessToken: token,
          expiresAt: Date.now() + expiresIn * 1000,
        }),
    }),
    {
      name: "ims-auth-storage", // localStorage key
      // Only persist these fields to localStorage
      partialize: (state) => ({
        accessToken: state.accessToken,
        refreshToken: state.refreshToken,
        expiresAt: state.expiresAt,
        user: state.user,
        isAuthenticated: state.isAuthenticated,
      }),
    }
  )
);
```

**Key decisions:**
- `expiresIn` from the auth response is in seconds (per `docs/outlines/06_API_GATEWAY_AUTH.md` — 15-minute access token expiry). The store converts to milliseconds by multiplying by 1000.
- `persist` middleware with `partialize` ensures only serializable fields hit localStorage (not the action functions).
- The token expiry check happens in the API client layer (Plan 07), not in the store itself.

### Step 2: Create the UI store

**File to CREATE:** `frontend-next/src/lib/store/ui-store.ts`

```ts
import { create } from "zustand";

type Theme = "light" | "dark" | "system";

export interface UIState {
  // Sidebar
  sidebarOpen: boolean;
  toggleSidebar: () => void;
  setSidebarOpen: (open: boolean) => void;

  // Theme
  theme: Theme;
  setTheme: (theme: Theme) => void;
}

export const useUIStore = create<UIState>()((set) => ({
  // Initial state
  sidebarOpen: true,
  theme: "system",

  // Sidebar actions
  toggleSidebar: () => set((state) => ({ sidebarOpen: !state.sidebarOpen })),
  setSidebarOpen: (open) => set({ sidebarOpen: open }),

  // Theme actions
  setTheme: (theme) => set({ theme }),
}));
```

**Key decisions:**
- UI state does NOT use `persist` middleware — sidebar state resets on reload (acceptable for UI preferences). If persistence is desired later, wrap with `persist` and add a `partialize`.
- Theme defaults to `"system"` — follows OS preference via CSS `prefers-color-scheme`. Applying the theme is handled by adding/removing the `.dark` class on `<html>` (which existing `globals.css` already supports).

### Step 3: Create the React Query provider

**File to CREATE:** `frontend-next/src/lib/providers/query-provider.tsx`

```tsx
"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";

function makeQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        // Data is considered fresh for 30 seconds — avoids refetching on every mount
        staleTime: 30 * 1000,
        // Retry failed queries up to 2 times with exponential backoff
        retry: 2,
        // Don't refetch when the browser window regains focus (avoid unnecessary requests)
        refetchOnWindowFocus: false,
      },
      mutations: {
        // Retry failed mutations once
        retry: 1,
      },
    },
  });
}

let browserQueryClient: QueryClient | undefined;

function getQueryClient() {
  // Server: always create a new QueryClient (prevents cross-request state leakage)
  if (typeof window === "undefined") {
    return makeQueryClient();
  }
  // Browser: reuse the same QueryClient across the app lifecycle
  if (!browserQueryClient) {
    browserQueryClient = makeQueryClient();
  }
  return browserQueryClient;
}

export function QueryProvider({ children }: { children: ReactNode }) {
  const [queryClient] = useState(getQueryClient);

  return (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}
```

**Key decisions:**
- `makeQueryClient()` is defined outside the component to avoid recreating on every render.
- Server/client split (`typeof window`) ensures each SSR request gets a fresh `QueryClient` so data from one user's request doesn't leak to another.
- `staleTime: 30s` is the default recommended by TkDodo (TanStack Query maintainer) for dashboards — it balances freshness with performance.
- This file is a Client Component (`"use client"`) because `QueryClientProvider` uses React Context which requires client-side React.

### Step 4: Create the providers barrel component

**File to CREATE:** `frontend-next/src/lib/providers/index.tsx`

```tsx
"use client";

import { type ReactNode } from "react";
import { QueryProvider } from "./query-provider";

export function Providers({ children }: { children: ReactNode }) {
  return <QueryProvider>{children}</QueryProvider>;
}
```

**Why a separate barrel:** This pattern allows adding more providers later (theme provider, toast provider, etc.) without modifying the root layout again. Just wrap them here.

### Step 5: Wrap root layout with Providers

**File to MODIFY:** `frontend-next/src/app/layout.tsx`

Open the file. You will add the `Providers` import and wrap `{children}` with it.

**Current body (lines 25–32):**
```tsx
export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="en"
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
    >
      <body className="min-h-full flex flex-col">{children}</body>
    </html>
  );
}
```

**Target:**
```tsx
import { Providers } from "@/lib/providers";

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="en"
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
      suppressHydrationWarning
    >
      <body className="min-h-full flex flex-col">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
```

**Changes:**
1. Add `import { Providers } from "@/lib/providers";` after the existing imports.
2. Wrap `{children}` with `<Providers>...</Providers>`.
3. Add `suppressHydrationWarning` to `<html>` — this suppresses the React hydration warning caused by theme toggling (the `.dark` class may differ between server and client render when using system theme preference). This is the standard Next.js approach for theme toggling.

Do NOT change:
- The `metadata` export (updated in Plan 04)
- The `geistSans` / `geistMono` font declarations
- The className on `<html>` or `<body>`

### Step 6: Verify build

Run from repo root:

```bash
cd frontend-next && npm run build
```

Fix any TypeScript errors. Common issues:
- If `zustand` types aren't found, verify `node_modules/zustand` exists and `npm install` if needed.
- If `@tanstack/react-query` types aren't found, verify `node_modules/@tanstack/react-query` exists and `npm install` if needed.

### Step 7: Verify lint

Run from repo root:

```bash
cd frontend-next && npm run lint
```

Fix any lint errors before marking this plan complete.

## Acceptance Criteria

- [x] `frontend-next/src/lib/store/auth-store.ts` exists with `useAuthStore` export
- [x] `frontend-next/src/lib/store/ui-store.ts` exists with `useUIStore` export
- [x] `frontend-next/src/lib/providers/query-provider.tsx` exists with `QueryProvider`
- [x] `frontend-next/src/lib/providers/index.tsx` exists with `Providers` barrel export
- [x] Root layout imports and wraps children with `<Providers>`
- [x] Root layout has `suppressHydrationWarning` on `<html>`
- [x] `npm run build` passes
- [x] `npm run lint` passes
- [ ] Auth store persists to localStorage (verify manually: open browser devtools → Application → Local Storage → look for `ims-auth-storage` key after calling `login()`)
