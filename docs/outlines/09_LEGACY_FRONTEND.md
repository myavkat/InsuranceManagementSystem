# Legacy Frontend Outline (Vue 3)

## Overview

The legacy `frontend/` is a **Vue 3 + Vite + TypeScript + TailwindCSS 4** SPA. It is preserved as-is during migration and will be replaced by the Next.js frontend.

---

## Technology Stack

| Aspect | Detail |
|--------|--------|
| Framework | Vue 3 (Composition API) |
| Build | Vite |
| Language | TypeScript |
| Styling | TailwindCSS 4 (via `@import 'tailwindcss'` in `style.css`) |
| State Management | Pinia |
| Auth | JWT in `localStorage` (key: `jwt_token`) |
| Devtools | `vite-plugin-vue-devtools` active in dev |

---

## Current Implementation Status

### Mock Services

- **All services are mock** — no real API integration.
- Functions return hardcoded data with `await new Promise(r => setTimeout(r, N))` delays (300–500ms).
- `authService.ts` accepts any credentials (no real validation).

### Auth Flow

- JWT stored in `localStorage` under key `jwt_token`.
- The `useAuth` composable validates on import.
- `beforeEach` router guard redirects unauthenticated users to `/login`.

### View Completeness

| View | Status |
|------|--------|
| `LoginView` | Fully implemented |
| `ClientsView` | Fully implemented |
| `AddClientView` | Fully implemented |
| `ClientDetailView` | Fully implemented |
| Dashboard | Placeholder `<h1>` |
| Policies | Placeholder `<h1>` |
| Claims | Placeholder `<h1>` |
| All other views | Placeholder `<h1>` |

### Known Issues

- **Sidebar navigation** uses `<a href>` instead of `<router-link>` — causes full page reloads on navigation.
- **Tests** — `App.spec.ts` checks for `'You did it!'` text that does not exist; will fail as-is.
- **Pinia** — only a scaffold `counter` store exists; no feature stores.

---

## Import Alias

`@/` import alias maps to `src/`. Example: `import { useAuth } from '@/composables/useAuth'`

---

## Preservation Rule

See [`01_SYSTEM_ARCHITECTURE.md`](./01_SYSTEM_ARCHITECTURE.md) rule #6. Do **not** modify the legacy frontend unless explicitly directed for maintenance.
