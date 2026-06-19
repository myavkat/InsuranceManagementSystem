# Task: Sprint 6 — Next.js Base Architecture

## Context Anchors
- Read Blueprint: @docs/outlines/05_NEXTJS_FRONTEND.md
- Read Blueprint: @docs/outlines/06_API_GATEWAY_AUTH.md

## Objective
Build the foundational layer of the Next.js frontend: App Router layout, BFF layer, state management, API client, authentication, and UI component library.

### Subtasks

1. **Configure Next.js Foundation**
   - App Router with root layout.tsx (fonts, global providers).
   - Tailwind CSS configuration with shadcn/ui theme (colors, radii, spacing).
   - Environment variables: `NEXT_PUBLIC_GATEWAY_URL`, `AUTH_SECRET`.
   - TypeScript strict mode enabled.

2. **Build Layout Components**
   - Auth layout: centered card layout for login/register pages.
   - Dashboard layout: sidebar (nav links, collapse), header (user menu, notifications), main content area.
   - Responsive breakpoints (mobile-first with collapsible sidebar).
   - Loading skeletons (`components/ui/skeleton.tsx`) for each route segment.

3. **Setup State Management**
   - Zustand stores:
     - `auth-store.ts`: token, user info, login/logout actions, persist to localStorage.
     - `ui-store.ts`: sidebar open/closed, theme toggle.
   - React Query client provider with default options (staleTime, retry, refetchOnWindowFocus).

4. **Create API Client Layer**
   - `lib/api/client.ts`: base fetch wrapper that:
     - Prepends `GATEWAY_URL` to all requests.
     - Attaches JWT from auth-store as `Authorization: Bearer` header.
     - Handles 401 responses (attempt refresh, redirect to login on failure).
     - Parses `ApiResponse<T>` envelope and throws on `success: false`.
   - Per-domain API modules: `auth.ts`, `customers.ts`, `insurances.ts`, `estimations.ts`, `vehicles.ts`.

### Deliverables
- Working Next.js app with App Router and all layouts
- shadcn/ui primitives installed and themed
- Zustand stores for auth and UI state
- React Query provider configured
- API client layer with JWT handling, error handling, and per-domain modules
- BFF route handler stubs (`app/api/*`) ready for implementation
