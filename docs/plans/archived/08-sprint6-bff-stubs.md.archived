# 08 — Sprint 6: BFF Route Handler Stubs

## Status: COMPLETED

## Objective

Create BFF (Backend-for-Frontend) route handler stubs under `app/api/*`. Each stub proxies requests to the API Gateway, forwarding the JWT token and returning the Gateway's response. For this sprint, the stubs return placeholder responses — the actual proxy logic is implemented in a later sprint when the API Gateway is available.

## Prerequisites

- **Plan 04 must be complete** (root layout, env vars including `NEXT_PUBLIC_GATEWAY_URL`)
- **Plan 07 must be complete** (API client types — the BFF stubs use the same `ApiResponse<T>` type)
- Read these files before starting:
  - `docs/outlines/05_NEXTJS_FRONTEND.md` — BFF pattern (Section 1), data flow diagram (Section 4)
  - `docs/outlines/06_API_GATEWAY_AUTH.md` — Gateway route table (which routes exist, which require auth)
  - `frontend-next/AGENTS.md` — Next.js 16 Route Handler docs
  - `frontend-next/node_modules/next/dist/docs/01-app/01-getting-started/15-route-handlers.md` — Route Handler API
  - `frontend-next/node_modules/next/dist/docs/01-app/02-guides/backend-for-frontend.md` — BFF pattern guide
  - `frontend-next/src/lib/api/types.ts` — `ApiResponse<T>` type (stubs use the same envelope)

## Architecture Notes

- **BFF Route Handlers run on the Next.js server** — they are NOT client code. They can access server-only env vars (without `NEXT_PUBLIC_` prefix) and make backend requests that never reach the browser.
- **Each domain gets an optional catch-all route:** `app/api/<domain>/[[...path]]/route.ts` handles both base paths and sub-paths (`/api/vehicles`, `/api/customers/123`, `/api/customers/search?name=...`, etc.). Optional catch-all (`[[...path]]`) is used instead of required catch-all (`[...path]`) to also match the domain root (e.g., `/api/vehicles`).
- **For this sprint: stubs only.** Each handler returns a static JSON success response. The actual Gateway proxying is wired up when the API Gateway service is implemented.
- **Route Handlers cannot coexist with `page.tsx` at the same route segment.** Since the route handlers are at `app/api/*`, pages are at `app/(dashboard)/*` and `app/(auth)/*`, there's no conflict.

## Steps

### Step 1: Create the auth BFF stub

**File to CREATE:** `frontend-next/src/app/api/auth/[...path]/route.ts`

```ts
import { NextResponse } from "next/server";

// Handle all HTTP methods for /api/auth/*
export async function GET() {
  return NextResponse.json({
    success: true,
    message: "Auth BFF stub — GET handler. Will proxy to API Gateway in a future sprint.",
    data: null,
  });
}

export async function POST() {
  return NextResponse.json({
    success: true,
    message: "Auth BFF stub — POST handler. Will proxy to API Gateway in a future sprint.",
    data: null,
  });
}

export async function PUT() {
  return NextResponse.json({
    success: true,
    message: "Auth BFF stub — PUT handler. Will proxy to API Gateway in a future sprint.",
    data: null,
  });
}

export async function DELETE() {
  return NextResponse.json({
    success: true,
    message: "Auth BFF stub — DELETE handler. Will proxy to API Gateway in a future sprint.",
    data: null,
  });
}
```

**Why a catch-all `[...path]` route:** The Gateway exposes:
- `POST /api/auth/login`
- `POST /api/auth/register`
- `POST /api/auth/refresh`
- `POST /api/auth/validate`

A single `[...path]/route.ts` catches all of them. In a future sprint, the handler will read `path` from the route params and forward the request to the matching Gateway endpoint.

### Step 2: Create the customers BFF stub

**File to CREATE:** `frontend-next/src/app/api/customers/[...path]/route.ts`

```ts
import { NextResponse } from "next/server";

export async function GET() {
  return NextResponse.json({
    success: true,
    message: "Customers BFF stub. Will proxy to API Gateway in a future sprint.",
    data: null,
  });
}

export async function POST() {
  return NextResponse.json({
    success: true,
    message: "Customers BFF stub — POST handler. Will proxy to API Gateway in a future sprint.",
    data: null,
  });
}

export async function PUT() {
  return NextResponse.json({
    success: true,
    message: "Customers BFF stub — PUT handler. Will proxy to API Gateway in a future sprint.",
    data: null,
  });
}

export async function DELETE() {
  return NextResponse.json({
    success: true,
    message: "Customers BFF stub — DELETE handler. Will proxy to API Gateway in a future sprint.",
    data: null,
  });
}
```

### Step 3: Create the insurances BFF stub

**File to CREATE:** `frontend-next/src/app/api/insurances/[...path]/route.ts`

```ts
import { NextResponse } from "next/server";

export async function GET() {
  return NextResponse.json({
    success: true,
    message: "Insurances BFF stub. Will proxy to API Gateway in a future sprint.",
    data: null,
  });
}

export async function POST() {
  return NextResponse.json({
    success: true,
    message: "Insurances BFF stub — POST handler. Will proxy to API Gateway in a future sprint.",
    data: null,
  });
}

export async function PUT() {
  return NextResponse.json({
    success: true,
    message: "Insurances BFF stub — PUT handler. Will proxy to API Gateway in a future sprint.",
    data: null,
  });
}

export async function DELETE() {
  return NextResponse.json({
    success: true,
    message: "Insurances BFF stub — DELETE handler. Will proxy to API Gateway in a future sprint.",
    data: null,
  });
}
```

### Step 4: Create the estimations BFF stub

**File to CREATE:** `frontend-next/src/app/api/estimations/[...path]/route.ts`

```ts
import { NextResponse } from "next/server";

export async function GET() {
  return NextResponse.json({
    success: true,
    message: "Estimations BFF stub. Will proxy to API Gateway in a future sprint.",
    data: null,
  });
}

export async function POST() {
  return NextResponse.json({
    success: true,
    message: "Estimations BFF stub — POST handler. Will proxy to API Gateway in a future sprint.",
    data: null,
  });
}

export async function PUT() {
  return NextResponse.json({
    success: true,
    message: "Estimations BFF stub — PUT handler. Will proxy to API Gateway in a future sprint.",
    data: null,
  });
}

export async function DELETE() {
  return NextResponse.json({
    success: true,
    message: "Estimations BFF stub — DELETE handler. Will proxy to API Gateway in a future sprint.",
    data: null,
  });
}
```

### Step 5: Create the vehicles BFF stub

**File to CREATE:** `frontend-next/src/app/api/vehicles/[...path]/route.ts`

```ts
import { NextResponse } from "next/server";

export async function GET() {
  return NextResponse.json({
    success: true,
    message: "Vehicles BFF stub. Will proxy to API Gateway in a future sprint.",
    data: null,
  });
}

export async function POST() {
  return NextResponse.json({
    success: true,
    message: "Vehicles BFF stub — POST handler. Will proxy to API Gateway in a future sprint.",
    data: null,
  });
}

export async function PUT() {
  return NextResponse.json({
    success: true,
    message: "Vehicles BFF stub — PUT handler. Will proxy to API Gateway in a future sprint.",
    data: null,
  });
}

export async function DELETE() {
  return NextResponse.json({
    success: true,
    message: "Vehicles BFF stub — DELETE handler. Will proxy to API Gateway in a future sprint.",
    data: null,
  });
}
```

### Step 6: Verify the route handlers work

Start the dev server:

```bash
cd frontend-next && npm run dev
```

Then test each endpoint with curl (Git Bash) or PowerShell `Invoke-RestMethod`:

```bash
# Test auth stub
curl -s http://localhost:3000/api/auth/login | head -c 200

# Test customers stub
curl -s http://localhost:3000/api/customers/123 | head -c 200

# Test insurances stub
curl -s http://localhost:3000/api/insurances?typeId=1 | head -c 200

# Test estimations stub
curl -s http://localhost:3000/api/estimations | head -c 200

# Test vehicles stub
curl -s http://localhost:3000/api/vehicles | head -c 200
```

Each should return a JSON response with `{"success": true, "message": "...", "data": null}`.

Also verify that non-API routes still work:
- `http://localhost:3000/login` — should show the auth layout
- `http://localhost:3000/dashboard` — should show the dashboard layout

### Step 7: Verify build

```bash
cd frontend-next && npm run build
```

The build should pass. If any route handler causes a conflict error (e.g., "Route conflicts with page"), verify the folder structure — `app/api/*` and `app/(dashboard)/*` should not overlap.

### Step 8: Verify lint

```bash
cd frontend-next && npm run lint
```

Fix any lint errors before marking this plan complete.

### Step 9: Update the landing page redirect

**File to MODIFY:** `frontend-next/src/app/page.tsx`

If Plan 04 used the placeholder version (because `/dashboard` didn't exist yet), now update it to the redirect version:

```tsx
import { redirect } from "next/navigation";

export default function Home() {
  redirect("/dashboard");
}
```

The dashboard route now exists, so this redirect will work.

## Acceptance Criteria

- [x] `frontend-next/src/app/api/auth/[[...path]]/route.ts` exists with GET, POST, PUT, DELETE handlers
- [x] `frontend-next/src/app/api/customers/[[...path]]/route.ts` exists with GET, POST, PUT, DELETE handlers
- [x] `frontend-next/src/app/api/insurances/[[...path]]/route.ts` exists with GET, POST, PUT, DELETE handlers
- [x] `frontend-next/src/app/api/estimations/[[...path]]/route.ts` exists with GET, POST, PUT, DELETE handlers
- [x] `frontend-next/src/app/api/vehicles/[[...path]]/route.ts` exists with GET, POST, PUT, DELETE handlers
- [x] `curl http://localhost:3000/api/auth/login` returns `{"success":true,...}` JSON
- [x] `curl http://localhost:3000/api/customers/123` returns JSON
- [x] `curl http://localhost:3000/api/insurances` returns JSON
- [x] `curl http://localhost:3000/api/estimations` returns JSON
- [x] `curl http://localhost:3000/api/vehicles` returns JSON
- [x] Non-API routes (`/login`, `/dashboard`) still render correctly
- [x] Landing page (`/`) redirects to `/dashboard`
- [x] `npm run build` passes
- [x] `npm run lint` passes
