<!-- BEGIN:nextjs-agent-rules -->
# This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` before writing any code. Heed deprecation notices.
<!-- END:nextjs-agent-rules -->

# Frontend AGENTS.md

## Overview
Next.js 16 App Router application with Server-Side Rendering (SSR) as the default rendering strategy, using Tailwind CSS and shadcn/ui.

## Workflow Commands
Building and Testing:
1. Format the code before committing:
   ```bash
   npm run lint
   ```
2. Run the build to compile and check for errors:
   ```bash
   npm run build
   ```
3. Start the production server (optional for local verification):
   ```bash
   npm run start
   ```

## Architectural Rules
- **Server-Side Data Fetching**: Server Components fetch data directly from the API Gateway via `serverFetch()` in `lib/api/server-fetch.ts`. The API Gateway URL is configured via `NEXT_PUBLIC_GATEWAY_URL`. Legacy BFF route handlers in `app/api/*` have been removed.
- **Server Components by Default**: All pages are Server Components unless interactivity is required (forms, real-time updates, client-side filtering). Client boundaries are explicit (`"use client"`).
- **Client State Management (Zustand)**: Zustand is used for lightweight client state (token, user info) persisted to localStorage. Server state is managed by React Query.
- **UI Components (shadcn/ui)**: All UI primitives are shadcn/ui (Base UI React + Tailwind CSS), configured with `style: "base-nova"`. No Bootstrap. No custom CSS components.
- **Authentication**: JWT stored in HTTP-only cookie (preferred) or localStorage. Auth state checked in root layout/middleware; unauthenticated users redirected to `/login`.
