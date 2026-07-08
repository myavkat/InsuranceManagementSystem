# Plan: BFF Route Handlers — Real API Gateway Proxy

## Objective

Replace all stub BFF route handlers in `frontend-next/src/app/api/*` with real proxy implementations that forward requests to the API Gateway. Add missing route handlers for `real-estate` and `reference-data` domains.

## Prerequisites

None — this is the first plan in the Sprint 7 sequence.

## Files to Read First

- `docs/outlines/05_NEXTJS_FRONTEND.md` — BFF pattern, data flow
- `docs/outlines/06_API_GATEWAY_AUTH.md` — Gateway routes, auth requirements
- `docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md` — Full API surface per service
- `frontend-next/src/lib/api/client.ts` — The `apiClient()` function and its patterns
- `frontend-next/src/lib/store/auth-store.ts` — How JWT tokens are stored
- `frontend-next/.env.local` — `NEXT_PUBLIC_GATEWAY_URL` variable

## Context

The BFF (Backend-for-Frontend) pattern means Next.js route handlers in `app/api/*` act as a proxy layer between the browser and the API Gateway. Currently, all handlers return stub JSON responses. They need to forward requests to `{NEXT_PUBLIC_GATEWAY_URL}/api/{domain}/{path}` with proper auth headers, request body forwarding, and response transformation.

The `apiClient` function in `src/lib/api/client.ts` is the **client-side** fetch wrapper — it calls the Gateway directly from the browser with JWT tokens. The BFF handlers serve **Server Components** that fetch during SSR (where there's no browser localStorage). The BFF handlers must:
1. Read the JWT from the incoming request's `Authorization` header (forwarded by the browser)
2. Proxy to the Gateway with that same header
3. Return the Gateway's JSON response as-is (the Gateway already wraps responses in `ApiResponse<T>` envelope)

**Important:** The BFF does NOT add new auth logic — it simply forwards the `Authorization` header the client sent.

## Steps

### Step 1: Create the shared BFF proxy utility

Create file: `frontend-next/src/lib/api/bff-proxy.ts`

This is a single shared function that all BFF route handlers will use. It:
- Reads the HTTP method, path params, search params, request body, and headers from the incoming Next.js request
- Constructs the target Gateway URL using `NEXT_PUBLIC_GATEWAY_URL`
- Forwards `Authorization`, `Content-Type`, and other relevant headers
- Makes a `fetch()` to the Gateway (server-side, so no CORS issues)
- Returns the Gateway's JSON response

```typescript
import { NextRequest, NextResponse } from "next/server";

const GATEWAY_URL = process.env.NEXT_PUBLIC_GATEWAY_URL || "http://localhost:8080";

export async function bffProxy(
  request: NextRequest,
  domain: string,
  pathSegments: string[]
): Promise<NextResponse> {
  // Build the target path: /api/{domain}[/{pathSegments...}]
  const pathSuffix = pathSegments.length > 0 ? `/${pathSegments.join("/")}` : "";
  const targetUrl = `${GATEWAY_URL}/api/${domain}${pathSuffix}`;

  // Preserve query string
  const searchParams = request.nextUrl.searchParams.toString();
  const fullUrl = searchParams ? `${targetUrl}?${searchParams}` : targetUrl;

  // Forward relevant headers
  const headers: Record<string, string> = {};
  const authHeader = request.headers.get("authorization");
  if (authHeader) headers["Authorization"] = authHeader;
  const contentType = request.headers.get("content-type");
  if (contentType) headers["Content-Type"] = contentType;

  // Read request body (for POST/PUT/PATCH)
  let body: string | undefined;
  if (["POST", "PUT", "PATCH"].includes(request.method)) {
    try {
      body = await request.text();
    } catch {
      body = undefined;
    }
  }

  try {
    const response = await fetch(fullUrl, {
      method: request.method,
      headers,
      body,
    });

    const data = await response.json();
    return NextResponse.json(data, { status: response.status });
  } catch (error) {
    return NextResponse.json(
      {
        success: false,
        message: "Gateway unreachable. Please ensure the API Gateway is running.",
        data: null,
      },
      { status: 502 }
    );
  }
}
```

### Step 2: Update each existing BFF route handler

For each domain below, replace the stub content of `route.ts` with the proxy implementation.

#### 2a. Auth — `frontend-next/src/app/api/auth/[...path]/route.ts`

Replace contents with:

```typescript
import { NextRequest } from "next/server";
import { bffProxy } from "@/lib/api/bff-proxy";

export async function GET(request: NextRequest, { params }: { params: Promise<{ path: string[] }> }) {
  const { path } = await params;
  return bffProxy(request, "auth", path ?? []);
}
export async function POST(request: NextRequest, { params }: { params: Promise<{ path: string[] }> }) {
  const { path } = await params;
  return bffProxy(request, "auth", path ?? []);
}
export async function PUT(request: NextRequest, { params }: { params: Promise<{ path: string[] }> }) {
  const { path } = await params;
  return bffProxy(request, "auth", path ?? []);
}
export async function DELETE(request: NextRequest, { params }: { params: Promise<{ path: string[] }> }) {
  const { path } = await params;
  return bffProxy(request, "auth", path ?? []);
}
```

#### 2b. Customers — `frontend-next/src/app/api/customers/[...path]/route.ts`

Same pattern, domain: `"customers"`.

#### 2c. Insurances — `frontend-next/src/app/api/insurances/[...path]/route.ts`

Same pattern, domain: `"insurances"`.

#### 2d. Estimations — `frontend-next/src/app/api/estimations/[...path]/route.ts`

Same pattern, domain: `"estimations"`.

#### 2e. Vehicles — `frontend-next/src/app/api/vehicles/[...path]/route.ts`

Same pattern, domain: `"vehicles"`.

### Step 3: Create missing BFF route handlers

#### 3a. Real Estate

Create directory: `frontend-next/src/app/api/real-estate/[...path]/`

Create file: `frontend-next/src/app/api/real-estate/[...path]/route.ts`

Same proxy pattern, domain: `"real-estate"`.

#### 3b. Reference Data

Create directory: `frontend-next/src/app/api/reference-data/[...path]/`

Create file: `frontend-next/src/app/api/reference-data/[...path]/route.ts`

Same proxy pattern, domain: `"reference-data"`.

### Step 4: Update Sidebar to include Real Estate nav item

File: `frontend-next/src/components/layout/sidebar.tsx`

Add `Building2` icon import from `lucide-react` (line ~16):
```typescript
import {
  LayoutDashboard,
  Users,
  Shield,
  Calculator,
  Car,
  Building2,       // <-- ADD THIS
  PanelLeftClose,
  PanelLeft,
} from "lucide-react";
```

Add Real Estate entry to `navItems` array (after Vehicles, around line 28):
```typescript
const navItems: NavItem[] = [
  { href: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { href: "/customers", label: "Customers", icon: Users },
  { href: "/insurances", label: "Insurances", icon: Shield },
  { href: "/estimations", label: "Estimations", icon: Calculator },
  { href: "/vehicles", label: "Vehicles", icon: Car },
  { href: "/real-estate", label: "Real Estate", icon: Building2 },  // <-- ADD THIS
];
```

### Step 5: Verify the build compiles

Run: `cd frontend-next && npm run build`

Fix any TypeScript errors before marking this plan complete.

## Acceptance Criteria

- [x] `bff-proxy.ts` utility exists and handles 502 errors gracefully
- [x] All 5 existing BFF route handlers (auth, customers, insurances, estimations, vehicles) proxy to Gateway instead of returning stubs
- [x] New `real-estate` BFF route handler exists and proxies correctly
- [x] New `reference-data` BFF route handler exists and proxies correctly
- [x] Sidebar includes "Real Estate" navigation item with Building2 icon
- [x] `npm run build` succeeds without TypeScript errors
