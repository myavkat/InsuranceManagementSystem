# Next.js Frontend Architecture Outline

## Overview

A **Next.js 16 App Router** application with **SSR** as the default rendering strategy.

---

## Directory Structure

```
frontend-next/
├── .env.local                    # GATEWAY_URL, AUTH_SECRET, etc.
├── next.config.ts
├── postcss.config.mjs
├── eslint.config.mjs
├── components.json               # shadcn/ui config (style: "base-nova")
├── tsconfig.json
├── src/
│   ├── app/
│   │   ├── layout.tsx            # Root layout (fonts, providers)
│   │   ├── page.tsx              # Landing / redirect
│   │   ├── globals.css
│   │   └── favicon.ico
│   ├── components/
│   │   └── ui/                   # shadcn/ui primitives (Base UI React)
│   │       ├── button.tsx
│   │       ├── input.tsx
│   │       ├── card.tsx
│   │       ├── dialog.tsx
│   │       ├── table.tsx
│   │       ├── select.tsx
│   │       ├── badge.tsx
│   │       └── skeleton.tsx
│   └── lib/
│       └── utils.ts              # cn() helper
└── public/
    └── images/
```

> **Note:** Additional directories (`(auth)/`, `(dashboard)/`, `api/` BFF route handlers, `components/features/`, `lib/api/`, `lib/store/`) will be added incrementally as features are implemented.

---

## Key Architectural Decisions

### 1. Server-Side Data Fetching
- Server Components fetch data directly from the API Gateway via `serverFetch()` in `lib/api/server-fetch.ts`.
- The API Gateway URL is configured via `NEXT_PUBLIC_GATEWAY_URL` environment variable.
- Authorization headers are forwarded from the incoming request (set by `middleware.ts` from the `auth_token` cookie).
- Client Components use **React Query** via `apiClient()` in `lib/api/client.ts` which also calls the Gateway directly, with the JWT from the Zustand auth store.
- Legacy BFF route handlers in `app/api/*` have been removed. Previously they proxied requests to the API Gateway, but Next.js Server Components running server-side can call the Gateway directly without an extra network hop.

### 2. Server Components by Default
- All pages are Server Components unless interactivity is required (forms, real-time updates, client-side filtering).
- Data fetching happens in Server Components via `fetch()` calls to the BFF.
- Client boundaries are explicit (`"use client"`).

### 3. Client State Management (Zustand)
- Zustand replaces Pinia for lightweight client state.
- Auth state (token, user info) lives in Zustand, persisted to localStorage.
- Server state is managed by React Query, not Zustand.

### 4. UI Components (shadcn/ui)
- All UI primitives are shadcn/ui (Base UI React + Tailwind CSS), configured with `style: "base-nova"`.
- No Bootstrap. No custom CSS components.
- Tailwind CSS v4 for utility-first styling.

### 5. Authentication
- JWT stored in HTTP-only cookie (preferred) or localStorage.
- Auth state checked in root layout/middleware; unauthenticated users redirected to `/login`.
- The API Gateway validates JWTs and forwards authenticated requests to downstream microservices.

---

## Data Flow

```
Browser Request
      │
      ├── Server Component
      │         │
      │         ▼
      │   API Gateway ──► Microservice ──► (JSON response)
      │         │
      │         ◄──────────────┘
      │         │
      │         ▼
      │   Server Component renders HTML
      │
      └── Client Component ("use client")
                │
                ▼
          React Query ──► apiClient() ──► API Gateway ──► Microservice
                │                                           │
                ◄──────────────────────────────────────────────┘
                │
                ▼
          Client Component hydrates / re-renders
```

---
