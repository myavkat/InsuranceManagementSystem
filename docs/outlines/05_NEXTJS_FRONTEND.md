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
- All UI primitives are shadcn/ui (Base UI React + Tailwind CSS), configured with `style: "base-nova"`.
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
Microservice
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
