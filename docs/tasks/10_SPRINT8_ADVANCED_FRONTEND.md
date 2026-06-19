# Task: Sprint 8 — Advanced Frontend Features

## Context Anchors
- Read Blueprint: @docs/outlines/05_NEXTJS_FRONTEND.md
- Read Blueprint: @docs/outlines/06_API_GATEWAY_AUTH.md
- Read Story: @docs/stories/01_AUTHENTICATION.md

## Objective
Add production-grade frontend features: SSR data fetching patterns, real-time notifications, advanced form handling, professional data tables, and full authentication.

### Subtasks

1. **Implement SSR Data Fetching**
   - Server Components use `fetch()` to BFF routes with `cache: 'no-store'` for dynamic data, `cache: 'force-cache'` for reference data.
   - Parallel data fetching patterns (multiple `fetch` calls at top level of Server Components).
   - Error boundaries: per-page error.tsx fallbacks.
   - Streaming with `loading.tsx` suspense boundaries for slow data dependencies.

2. **Add Real-time Notifications**
   - WebSocket connection from frontend to API Gateway (or dedicated push service).
   - Notification types: estimation status changes, validation failures, system alerts.
   - Zustand store for notification state with badge count in header.
   - Toast component (`sonner` or shadcn toast) for transient notifications.

3. **Implement Form Validation**
   - Zod schemas for every form: customer, vehicle, real estate, insurance, estimation, login, register.
   - React Hook Form integration with Zod resolvers.
   - Inline validation errors, disabled submit until valid, async validation (e.g., check national ID uniqueness).
   - Form state persistence (prevent accidental navigation away with unsaved changes).

4. **Build Advanced Data Tables**
   - TanStack Table (React Table) for all list pages.
   - Features: server-side pagination, column sorting, multi-column filtering, row selection, export to CSV.
   - Responsive: horizontal scroll on mobile, column visibility toggle.
   - Reusable `data-table.tsx` component wrapping TanStack Table with shadcn/ui styling.

5. **Implement Authentication**
   - Login page: username/email + password form, calls BFF `/api/auth/login` → forwards to Auth Service.
   - Registration page: username + email + password + confirm password.
   - Middleware (`middleware.ts`): check auth token on every request, redirect to `/login` if missing/expired.
   - Refresh token rotation: BFF route handler catches 401, attempts silent refresh with refresh token, retries original request.
   - Logout: clear tokens, redirect to `/login`.

### Deliverables
- SSR with streaming and error boundaries
- WebSocket real-time notification system
- Zod + React Hook Form validation on all forms
- TanStack Table with pagination, sorting, filtering, CSV export
- Complete auth flow (login, register, token refresh, middleware guard, logout)
- All advanced features hardened with proper error/loading/empty states
