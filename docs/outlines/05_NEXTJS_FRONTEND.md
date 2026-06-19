# Next.js Frontend Architecture Outline

## Overview

A **Next.js 15+ App Router** application with **SSR** as the default rendering strategy. Replaces the legacy Vue 3 frontend incrementally during migration.

---

## Directory Structure

```
frontend-next/
├── .env.local                    # GATEWAY_URL, AUTH_SECRET, etc.
├── next.config.ts
├── tailwind.config.ts
├── components.json               # shadcn/ui config
├── app/
│   ├── layout.tsx                # Root layout (fonts, providers)
│   ├── page.tsx                  # Landing / redirect
│   ├── (auth)/
│   │   ├── layout.tsx            # Auth layout (centered card)
│   │   ├── login/page.tsx        # Login page
│   │   └── register/page.tsx     # Registration page
│   ├── (dashboard)/
│   │   ├── layout.tsx            # Dashboard layout (sidebar, header)
│   │   ├── page.tsx              # Dashboard home
│   │   ├── customers/
│   │   │   ├── page.tsx          # Customer list (server component)
│   │   │   ├── [id]/page.tsx     # Customer detail
│   │   │   └── new/page.tsx      # New customer form
│   │   ├── insurances/
│   │   │   ├── page.tsx
│   │   │   └── [id]/page.tsx
│   │   ├── estimations/
│   │   │   ├── page.tsx
│   │   │   └── [id]/page.tsx
│   │   └── vehicles/
│   │       ├── page.tsx
│   │       └── [id]/page.tsx
│   └── api/                      # BFF route handlers
│       ├── auth/
│       │   ├── login/route.ts
│       │   └── refresh/route.ts
│       ├── customers/
│       │   ├── route.ts          # GET (list), POST (create)
│       │   └── [id]/route.ts     # GET, PUT, DELETE
│       ├── insurances/
│       │   └── route.ts
│       ├── estimations/
│       │   └── route.ts
│       └── vehicles/
│           └── route.ts
├── components/
│   ├── ui/                       # shadcn/ui primitives
│   │   ├── button.tsx
│   │   ├── input.tsx
│   │   ├── card.tsx
│   │   ├── dialog.tsx
│   │   ├── table.tsx
│   │   ├── select.tsx
│   │   ├── badge.tsx
│   │   └── skeleton.tsx
│   └── features/                 # Feature-specific components
│       ├── customers/
│       │   ├── customer-table.tsx
│       │   └── customer-form.tsx
│       ├── estimations/
│       │   ├── estimation-form.tsx
│       │   └── estimation-status.tsx
│       └── layout/
│           ├── sidebar.tsx
│           └── header.tsx
├── lib/
│   ├── api/                      # API client
│   │   ├── client.ts             # Base fetch wrapper (auth headers, error handling)
│   │   ├── auth.ts               # Auth API calls
│   │   ├── customers.ts          # Customer API calls
│   │   ├── insurances.ts
│   │   ├── estimations.ts
│   │   └── vehicles.ts
│   ├── store/                    # Client state
│   │   ├── auth-store.ts         # Zustand auth store
│   │   └── ui-store.ts           # UI state (sidebar, theme)
│   └── utils.ts                  # cn() helper, formatting
└── public/
    └── images/
```

---

## Key Architectural Decisions

### 1. BFF (Backend-for-Frontend) Pattern
- Route handlers in `app/api/*` proxy requests to the API Gateway.
- Server Components fetch data through the BFF on the server (no direct Gateway calls from the browser).
- Client Components use **React Query** to call the same BFF routes with caching, deduplication, and background refetching.

### 2. Server Components by Default
- All pages are Server Components unless interactivity is required (forms, real-time updates, client-side filtering).
- Data fetching happens in Server Components via `fetch()` calls to the BFF.
- Client boundaries are explicit (`"use client"`).

### 3. Client State Management (Zustand)
- Zustand replaces Pinia for lightweight client state.
- Auth state (token, user info) lives in Zustand, persisted to localStorage.
- Server state is managed by React Query, not Zustand.

### 4. UI Components (shadcn/ui)
- All UI primitives are shadcn/ui (Radix + Tailwind).
- No Bootstrap. No custom CSS components.
- Tailwind CSS v4 for utility-first styling.

### 5. Authentication
- JWT stored in HTTP-only cookie (preferred) or localStorage.
- Auth state checked in root layout/middleware; unauthenticated users redirected to `/login`.
- BFF route handlers validate JWT with Auth Service or Gateway.

---

## Data Flow

```
Browser Request
      │
      ▼
Next.js Server (Server Component)
      │
      ▼
BFF (app/api/* route handler)
      │
      ▼
API Gateway (external)
      │
      ▼
Target Microservice
      │
      ▼ (JSON response)
API Gateway
      │
      ▼
BFF (returns response to Server Component)
      │
      ▼
Server Component renders HTML
      │
      ▼ (if client component needed)
React Query hydrates from same BFF endpoint
```

---

## Migration Strategy

- Legacy Vue app remains operational on `app.legacy.example.com`.
- New Next.js app served from `app.example.com`.
- API Gateway routes based on `Host` header or migration cookie.
- During incremental migration, both frontends coexist and access the same Gateway.
- The legacy frontend is decommissioned only after all routes are migrated and verified.
