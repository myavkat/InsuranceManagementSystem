# Task: Sprint 7 — Feature Page Migration

## Context Anchors
- Read Blueprint: @docs/outlines/05_NEXTJS_FRONTEND.md
- Read Blueprint: @docs/outlines/06_API_GATEWAY_AUTH.md
- Read Story: @docs/stories/02_CUSTOMER_MANAGEMENT.md
- Read Story: @docs/stories/03_INSURANCE_PRODUCTS.md
- Read Story: @docs/stories/04_ESTIMATION_SAGA.md
- Read Story: @docs/stories/05_VEHICLE_MANAGEMENT.md
- Read Story: @docs/stories/06_REAL_ESTATE_MANAGEMENT.md

## Objective
Build all feature pages in the Next.js frontend. Each page is a Server Component by default, with client components for interactive elements (forms, tables, search).

> **UI Library:** This project uses **shadcn/ui with Base UI React** (`@base-ui/react`), configured with `style: "base-nova"`. All interactive components (forms, dialogs, selects, tables) must use the `@base-ui/react`-based shadcn/ui wrappers from `src/components/ui/`. Do NOT use Radix UI primitives.

> **Prerequisite:** The API Gateway (`09_PHASE4_API_GATEWAY.md`) must be operational before starting this task. BFF route handlers in `app/api/*` proxy to the Gateway — without it, feature pages cannot fetch real data. If the Gateway is not yet built, mock its responses in BFF handlers during development.

### Subtasks

1. **Build Customer Management Pages**
   - Customer list page (`/customers`): server-rendered table with search bar, pagination.
   - Customer detail page (`/customers/[id]`): full customer info, linked vehicles, estimation history.
   - Customer create/edit page (`/customers/new`, `/customers/[id]/edit`): form with validation.
   - Feature components: `customer-table.tsx`, `customer-form.tsx`, `customer-detail.tsx`.

2. **Build Insurance Management Pages**
   - Insurance products list (`/insurances`): table with type/company/status filters.
   - Product detail (`/insurances/[id]`).
   - Product create/edit form.
   - Insurance types and companies management pages.

3. **Build Estimation Management Pages**
   - Estimation list (`/estimations`): table with status badge, customer name, date range filter.
   - Estimation detail (`/estimations/[id]`): full status, premium breakdown, customer info.
   - New estimation form: select customer → select insurance type/company → optionally link vehicle/real estate → submit.
   - Feature components: `estimation-form.tsx`, `estimation-status.tsx`.

4. **Build Vehicle Management Pages**
   - Vehicle list, detail, create, edit.
   - Cascading dropdowns (brand → model → engine/fuel/type/package).

5. **Build Real Estate Management Pages**
   - Real estate list, detail, create, edit.
   - Dropdowns for construction type, luxury class, usage type.

### Deliverables
- All feature pages functional with SSR
- BFF route handlers (`app/api/*`) for each domain implementing proxy to API Gateway
- Client components using React Query for data fetching, mutations, cache invalidation
- Zod validation schemas on all forms
- Loading states (skeletons), empty states, error states on every page
- Responsive design: mobile + tablet + desktop layouts
