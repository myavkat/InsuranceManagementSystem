# Plan: Sprint 8 — SSR Data Fetching & Streaming

**Plan ID:** `sprint8-02-ssr-data-fetching`
**Priority:** 2 (depends on auth being functional)
**Prerequisite Plans:** `sprint8-01-authentication` (auth must be done so SSR fetches have tokens)
**Blocks:** None directly, but `sprint8-04-data-tables` will benefit from SSR patterns

---

## Objective

Implement server-side rendering (SSR) data fetching patterns across all pages. Add `loading.tsx` streaming boundaries and `error.tsx` error boundaries for every route group. Convert data-fetching pages to Server Components where possible, using parallel `fetch()` calls to BFF routes. This covers subtask 1 from `docs/tasks/11_SPRINT8_ADVANCED_FRONTEND.md`.

---

## Files to Read First

| File | Purpose |
|------|---------|
| `docs/outlines/05_NEXTJS_FRONTEND.md` | BFF pattern, data flow diagram, architecture decisions |
| `frontend-next/src/app/(dashboard)/customers/page.tsx` | Current pattern: Client Component wrapping a list |
| `frontend-next/src/app/(dashboard)/dashboard/page.tsx` | Current dashboard with placeholder stat cards |
| `frontend-next/src/components/features/customers/customer-list.tsx` | Client Component doing client-side data fetching |
| `frontend-next/src/lib/api/customers.ts` | API functions for customers (used as reference) |
| `frontend-next/src/lib/api/vehicles.ts` | API functions for vehicles |
| `frontend-next/src/lib/api/realestate.ts` | API functions for real estate |
| `frontend-next/src/lib/api/insurances.ts` | API functions for insurances |
| `frontend-next/src/lib/api/estimations.ts` | API functions for estimations |
| `frontend-next/src/components/ui/skeleton.tsx` | Existing Skeleton component |
| `frontend-next/src/components/features/data-table-skeleton.tsx` | Table skeleton placeholder |
| `frontend-next/src/app/(dashboard)/loading.tsx` | Existing loading fallback for dashboard group |
| `frontend-next/src/app/(auth)/loading.tsx` | Existing loading fallback for auth group |
| `frontend-next/src/app/globals.css` | Design tokens for styling |

---

## Technical Context

### Key Architecture (from outline)
- **BFF Pattern**: Server Components call the BFF (`frontend-next/src/app/api/*` route handlers) which proxy to the API Gateway. Server Components do NOT call the Gateway directly.
- **Server Components Default**: Pages are Server Components unless they need interactivity. Data fetching happens in Server Components.
- **Streaming**: `loading.tsx` creates a Suspense boundary automatically. Pages that need per-component streaming can use `<Suspense>` with a fallback.

### BFF Proxy Pattern
The existing BFF route handlers are at `frontend-next/src/app/api/{domain}/[...path]/route.ts`. Each exports GET/POST/PUT/DELETE that proxy to the Gateway via `bffProxy()` from `@/lib/api/bff-proxy`.

In a Server Component, you call the BFF directly using the Next.js `fetch()` API:
```typescript
// In a Server Component — use the full URL to the Next.js BFF route
const data = await fetch("http://localhost:3000/api/customers?page=0&size=20", {
  cache: "no-store",  // dynamic data
  headers: { Cookie: cookies().toString() }, // forward auth cookie
}).then(res => res.json());
```

**BUT** — the preferred pattern for Next.js Server Components is to call the BFF route handler logic directly (avoiding an extra HTTP hop). Since the BFF route handler calls `bffProxy()` which does `fetch()` to the Gateway, this is acceptable. For simplicity in this plan, **use the server-to-BFF fetch pattern** described above.

### Cache Strategies (from outline)
- **Dynamic data** (customers, vehicles, estimations, insurances): `cache: "no-store"` — always fresh
- **Reference data** (cities, professions, insurance types, companies): `cache: "force-cache"` — cached aggressively

### Existing List Page Pattern (for reference)
Currently, each list page (e.g., `customers/page.tsx`) is a Server Component that renders a Client Component (`CustomerList`). The Client Component uses `useQuery` from React Query to fetch data client-side. This plan converts the pattern so that:
1. The Server Component does the initial fetch and passes data as props
2. The Client Component hydrates from those props (using React Query's `initialData`)

---

## Steps

### Step 1: Create per-route error boundaries (error.tsx)

For each route group, create an `error.tsx` file. This file MUST be a Client Component.

- [ ] Create `frontend-next/src/app/(auth)/error.tsx`:
  ```tsx
  "use client";

  import { useEffect } from "react";
  import { Button } from "@/components/ui/button";
  import { AlertCircle } from "lucide-react";

  export default function AuthErrorPage({
    error,
    reset,
  }: {
    error: Error & { digest?: string };
    reset: () => void;
  }) {
    useEffect(() => {
      console.error("Auth error boundary caught:", error);
    }, [error]);

    return (
      <div className="flex min-h-screen items-center justify-center">
        <div className="flex flex-col items-center gap-4 text-center">
          <AlertCircle className="size-12 text-destructive/70" />
          <div>
            <h2 className="text-lg font-semibold">Something went wrong</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              {error.message || "An unexpected error occurred."}
            </p>
          </div>
          <Button onClick={reset}>Try again</Button>
        </div>
      </div>
    );
  }
  ```

- [ ] Create `frontend-next/src/app/(dashboard)/error.tsx`:
  Same pattern as above, but wrapped in the dashboard shell (inside a `<div className="flex flex-col items-center justify-center py-16">`). Do NOT include the sidebar/header — Next.js error boundaries render inside the parent layout, which already has the sidebar/header.

  ```tsx
  "use client";

  import { useEffect } from "react";
  import { Button } from "@/components/ui/button";
  import { AlertCircle } from "lucide-react";

  export default function DashboardErrorPage({
    error,
    reset,
  }: {
    error: Error & { digest?: string };
    reset: () => void;
  }) {
    useEffect(() => {
      console.error("Dashboard error boundary caught:", error);
    }, [error]);

    return (
      <div className="flex flex-col items-center justify-center py-16 text-center">
        <AlertCircle className="size-12 text-destructive/70" />
        <h2 className="mt-4 text-lg font-semibold">Something went wrong</h2>
        <p className="mt-1 text-sm text-muted-foreground max-w-sm">
          {error.message || "An unexpected error occurred."}
        </p>
        <Button onClick={reset} className="mt-4">Try again</Button>
      </div>
    );
  }
  ```

- [ ] Create `frontend-next/src/app/error.tsx` (global error fallback for the root):
  Same pattern — centered error with reset button. This catches errors outside route groups.

- [ ] Create `frontend-next/src/app/not-found.tsx` (global 404 page):
  ```tsx
  import Link from "next/link";
  import { Button } from "@/components/ui/button";
  import { FileQuestion } from "lucide-react";

  export default function NotFound() {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <div className="flex flex-col items-center gap-4 text-center">
          <FileQuestion className="size-12 text-muted-foreground/50" />
          <div>
            <h2 className="text-lg font-semibold">Page not found</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              The page you're looking for doesn't exist.
            </p>
          </div>
          <Button asChild>
            <Link href="/dashboard">Go to Dashboard</Link>
          </Button>
        </div>
      </div>
    );
  }
  ```

### Step 2: Add loading.tsx for route groups that need them

- [ ] Check `frontend-next/src/app/(dashboard)/loading.tsx` — it already exists. Read it. If it's a skeleton/spinner, keep it. If empty, replace with a simple loading skeleton:
  ```tsx
  import { Skeleton } from "@/components/ui/skeleton";

  export default function DashboardLoading() {
    return (
      <div className="space-y-6">
        <Skeleton className="h-8 w-64" />
        <div className="rounded-md border">
          <div className="p-4 space-y-4">
            {Array.from({ length: 5 }).map((_, i) => (
              <Skeleton key={i} className="h-10 w-full" />
            ))}
          </div>
        </div>
      </div>
    );
  }
  ```

- [ ] Check `frontend-next/src/app/(auth)/loading.tsx` — it already exists. Read it. Same pattern but simpler (just a centered skeleton/spinner for the auth card).

- [ ] Add `loading.tsx` to individual domain directories if they don't inherit from the group loading. Check these paths — they should all be handled by the `(dashboard)/loading.tsx` group-level file. Only add an individual loading if the group one doesn't cover it:
  - `frontend-next/src/app/(dashboard)/customers/loading.tsx` — NOT needed if group-level exists
  - Verify: does `(dashboard)/loading.tsx` exist and cover nested routes? Yes, Next.js group loading.tsx wraps all children.

### Step 3: Convert the Dashboard page to SSR data fetching

- [ ] Open `frontend-next/src/app/(dashboard)/dashboard/page.tsx`
- [ ] The dashboard currently shows placeholder "—" values. Convert it to a Server Component that fetches real summary stats.
- [ ] **Since the BFF routes require auth tokens, and Server Components can't read localStorage**, you have two options:
  1. Read the `auth_token` cookie (set in Plan 01) and forward it to the BFF
  2. Keep the dashboard as a Client Component with `useQuery`
  
  **Decision for this plan**: Keep the dashboard as a Client Component for now (the stats API endpoints may not exist yet). The SSR conversion focuses on list/detail pages where the data fetching pattern is clear. Mark this as deferred.

- [ ] Update the dashboard to at minimum show a meaningful loading state. The `loading.tsx` at the group level already handles this.

### Step 4: Create a shared server-side data fetching utility

- [ ] Create `frontend-next/src/lib/api/server-fetch.ts`:
  ```typescript
  import { cookies } from "next/headers";

  const APP_URL = process.env.NEXT_PUBLIC_APP_URL || "http://localhost:3000";

  /**
   * Fetch data from the Next.js BFF route handler.
   * For use in Server Components only.
   *
   * @param path - BFF API path (e.g., "/api/customers?page=0&size=20")
   * @param options - Cache and revalidation options
   */
  export async function serverFetch<T>(
    path: string,
    options: {
      cache?: RequestCache;
      revalidate?: number | false;
      tags?: string[];
    } = {}
  ): Promise<T> {
    const cookieStore = await cookies();
    const headers: Record<string, string> = {
      Cookie: cookieStore.toString(),
    };

    const url = `${APP_URL}${path}`;
    const res = await fetch(url, {
      headers,
      next: {
        revalidate: options.revalidate,
        tags: options.tags,
      },
      cache: options.cache,
    });

    if (!res.ok) {
      throw new Error(`Server fetch failed: ${res.status} ${res.statusText} for ${path}`);
    }

    const body = await res.json();

    if (!body.success) {
      throw new Error(body.message || `API error for ${path}`);
    }

    return body.data as T;
  }
  ```
- [ ] This utility forwards the auth cookie from the server-side request to the BFF, which then proxies to the Gateway with the token.

### Step 5: Apply SSR pattern to a representative list page (Customers)

- [ ] Open `frontend-next/src/app/(dashboard)/customers/page.tsx`
- [ ] Convert from Client Component to Server Component (remove `"use client"`)
- [ ] Use the `serverFetch` utility to fetch the first page of customers:
  ```tsx
  import type { Metadata } from "next";
  import { serverFetch } from "@/lib/api/server-fetch";
  import type { CustomerResponse, PageResponse } from "@/lib/api/customers";
  import { CustomerList } from "@/components/features/customers/customer-list";

  export const metadata: Metadata = {
    title: "Customers",
  };

  // Revalidate every 30 seconds, or use cache: "no-store" for always-fresh
  export const revalidate = 0; // dynamic — always fetch fresh

  export default async function CustomersPage() {
    let initialData: PageResponse<CustomerResponse> | undefined;

    try {
      initialData = await serverFetch<PageResponse<CustomerResponse>>(
        "/api/customers?page=0&size=20",
        { cache: "no-store" }
      );
    } catch (error) {
      // Let the error boundary handle it
      throw error;
    }

    return <CustomerList initialData={initialData} />;
  }
  ```

- [ ] Open `frontend-next/src/components/features/customers/customer-list.tsx`
- [ ] Modify `CustomerList` to accept an optional `initialData` prop:
  ```tsx
  interface CustomerListProps {
    initialData?: PageResponse<CustomerResponse>;
  }
  ```
- [ ] In the `useQuery` call, pass `initialData`:
  ```tsx
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["customers", page, search],
    queryFn: () => getCustomers(page, pageSize, search || undefined),
    initialData: page === 0 && !search ? initialData : undefined,
  });
  ```
  This way, the first page with no search uses the SSR data, and subsequent pages/searches fetch client-side.
- [ ] **IMPORTANT**: `initialData` in React Query v5 sets the cache entry directly — it does NOT trigger a refetch. This means the first render uses SSR data, and subsequent client-side interactions (pagination, search) trigger fetches as before. This is the correct hydration pattern.

### Step 6: Apply SSR pattern to remaining list pages

Repeat Step 5 pattern for each of these page files:

- [ ] `frontend-next/src/app/(dashboard)/vehicles/page.tsx` + `vehicle-list.tsx`
- [ ] `frontend-next/src/app/(dashboard)/real-estate/page.tsx` + `real-estate-list.tsx`
- [ ] `frontend-next/src/app/(dashboard)/insurances/page.tsx` + `insurance-list.tsx`
- [ ] `frontend-next/src/app/(dashboard)/estimations/page.tsx` + `estimation-list.tsx`

Each follows the exact same pattern:
1. Server Component page fetches `initialData` via `serverFetch`
2. Client Component list accepts `initialData` prop
3. `useQuery` uses `initialData` for first page, no-search state
4. `revalidate = 0` for dynamic data, or set a specific revalidation interval

### Step 7: Add per-page Suspense boundaries for slow dependencies

- [ ] Review each detail page under `(dashboard)/*/[id]/page.tsx` — these are currently Client Components
- [ ] For pages that have multiple independent data dependencies, wrap sections in `<Suspense>`:
  ```tsx
  import { Suspense } from "react";
  import { Skeleton } from "@/components/ui/skeleton";

  // In the page component:
  <div className="space-y-6">
    <Suspense fallback={<Skeleton className="h-48 w-full" />}>
      <CustomerInfoSection customerId={id} />
    </Suspense>
    <Suspense fallback={<Skeleton className="h-32 w-full" />}>
      <CustomerEstimationsSection customerId={id} />
    </Suspense>
  </div>
  ```
- [ ] This enables streaming — the fast sections render immediately while slow sections show skeletons
- [ ] Apply this pattern to the customer detail page: `frontend-next/src/components/features/customers/customer-detail.tsx`
- [ ] If the detail page is a single component fetching all data, skip this step for now and note it for future refinement

### Step 8: Verify server-side rendering behavior

- [ ] Run `npx tsc --noEmit` from `frontend-next/` to verify no TypeScript errors
- [ ] At this point, building (`npm run build` in `frontend-next/`) should:
  - Generate static/dynamic pages based on fetch usage
  - NOT error on server components with `revalidate` or `cache` settings
- [ ] Verify that loading.tsx files are picked up (Next.js does this automatically based on file conventions)

---

## Acceptance Criteria

1. `error.tsx` exists for all route groups (`(auth)`, `(dashboard)`, and root) with a reset button
2. `not-found.tsx` exists at the root level with navigation to dashboard
3. `loading.tsx` exists for `(auth)` and `(dashboard)` route groups with skeleton placeholders
4. `serverFetch` utility created and reusable for all Server Component data fetching
5. All 5 list pages (customers, vehicles, real-estate, insurances, estimations) converted to SSR pattern:
   - Server Component page fetches initial data
   - Client Component list accepts `initialData` and passes to `useQuery`
6. No TypeScript errors
7. Pages render initial HTML from the server (viewable with JavaScript disabled or via `curl`)
8. Client-side navigation (pagination, search) still works via React Query after hydration

---

## Common Mistakes to Avoid

- **DO NOT** try to read `localStorage` in a Server Component — it only exists in the browser
- **DO NOT** call the API Gateway directly from a Server Component — always go through the BFF
- **DO NOT** use `useQuery` or any other hook in a Server Component — hooks are client-only
- **DO NOT** forget the `"use client"` directive on `error.tsx` — error boundaries MUST be Client Components
- **DO NOT** use `cache: "no-store"` for reference data (cities, professions, insurance types) — use `cache: "force-cache"` or set a long `revalidate`
- **DO NOT** inline `loading.tsx` content into page files — Next.js picks it up by file convention only
- **DO NOT** use `fetch` without `await cookies()` to forward auth cookies in Server Components — the BFF won't see the auth token
- **DO NOT** set `initialData` for non-initial page/search states — it should only apply to the first page with no search
- **DO NOT** use `use()` from React 19 without wrapping the promise — `use()` unwraps promises in the render phase
- Server Components CANNOT have `"use client"` — if you need hooks, it must be a Client Component
