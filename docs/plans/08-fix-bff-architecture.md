# Plan: Fix BFF Architecture Violation (server-fetch.ts)

**Source:** Sprint 8 code review — finding 13

---

## Objective

Resolve the architectural contradiction between `server-fetch.ts` (which calls the API Gateway directly) and the documented BFF (Backend-for-Frontend) pattern in `docs/outlines/05_NEXTJS_FRONTEND.md`.

The architecture outline says: *"Route handlers in `app/api/*` proxy requests to the API Gateway. Server Components fetch data through the BFF on the server (no direct Gateway calls from the browser)."*

But `server-fetch.ts` explicitly says: *"Server Components should fetch data directly from its source (the API Gateway), NOT via Route Handlers (BFF)."*

---

## Severity

🟡 **Low-Med** — Architecture deviation. The two sources of truth disagree. The BFF route handlers become dead code.

---

## Files to Read First

| File | Why |
|------|-----|
| `frontend-next/src/lib/api/server-fetch.ts` | The file implementing direct Gateway calls (64 lines) |
| `docs/outlines/05_NEXTJS_FRONTEND.md` | The documented BFF architecture |
| `frontend-next/src/lib/api/client.ts` | The client-side API client (also calls Gateway directly) |
| `frontend-next/src/app/api/**/[[...path]]/route.ts` | Existing BFF route handlers (search for them) |
| `frontend-next/AGENTS.md` | Instructs: "Read the relevant guide in node_modules/next/dist/docs/" |

---

## Steps

### Step 1 — Determine the resolution

There are two valid approaches. Pick ONE based on team decision:

**Option A: Re-align with the documented BFF pattern** — Change `server-fetch.ts` to call BFF route handlers (`/api/*`) instead of the Gateway directly. This restores the BFF as the single entry point. The BFF route handlers already exist and proxy to the Gateway.

**Option B: Update the architecture document** — Accept direct Gateway calls from Server Components (citing Next.js 16 guidance) and remove or deprecate the BFF route handlers. Update `docs/outlines/05_NEXTJS_FRONTEND.md` to reflect the new pattern.

This plan provides instructions for **Option B** (simpler, fewer changes), but notes what Option A would require.

---

### Option B: Accept direct Gateway calls, update docs

**Rationale**: Next.js Server Components run only on the server. Calling the Gateway directly from a Server Component is architecturally equivalent to calling it from a BFF route handler — both run server-side. The BFF pattern adds an unnecessary network hop.

#### Step 2 — Update the architecture document

Edit `docs/outlines/05_NEXTJS_FRONTEND.md`:

1. Find the section "1. BFF (Backend-for-Frontend) Pattern" (around line 47-50)
2. Update it to reflect the current implementation:

```markdown
### 1. Server-Side Data Fetching
- Server Components fetch data directly from the API Gateway via `serverFetch()` in `lib/api/server-fetch.ts`.
- The API Gateway URL is configured via `NEXT_PUBLIC_GATEWAY_URL` environment variable.
- Authorization headers are forwarded from the incoming request (set by middleware.ts from the auth_token cookie).
- Client Components use **React Query** via `apiClient()` in `lib/api/client.ts` which also calls the Gateway directly, with the JWT from the Zustand auth store.
- BFF route handlers in `app/api/*` are deprecated and will be removed in a future cleanup.
```

3. Update the "Data Flow" diagram (around line 76-102) to show Server Components connecting directly to the API Gateway (remove the BFF box from the server-rendering path).

#### Step 3 — Remove the dead BFF route handlers (optional cleanup)

If the BFF route handlers (`app/api/**/route.ts`) are no longer used by any code, remove them. First verify they have no callers:

```bash
# Search for any imports of the BFF handlers
grep -r "api/" frontend-next/src --include="*.ts" --include="*.tsx" | grep -v "node_modules"
```

If only `apiClient.ts` and `server-fetch.ts` reference `/api/` paths (as Gateway paths, not as local route handlers), the BFF route handlers can be safely deleted.

#### Step 4 — Add `.env.template` entry (per AGENTS.md rule)

Per the AGENTS.md environment variable rule: *"Every `${ENV_VAR:default}` placeholder referenced in any `application.yml` MUST have a corresponding entry in `.env.template`."* While this rule targets `application.yml`, the principle applies to all config. `server-fetch.ts` uses `NEXT_PUBLIC_GATEWAY_URL` with a default. Verify it exists in the repo's root `.env.template` (or `frontend-next/.env.local` example). If missing, add it:

```
# API Gateway URL (used by serverFetch and apiClient)
NEXT_PUBLIC_GATEWAY_URL=http://localhost:8080
```

---

### Option A: Re-align with BFF (alternative — more work)

If the team prefers to restore the BFF pattern:

1. Change `server-fetch.ts` to call `/api/*` paths (localhost) instead of `GATEWAY_URL` paths
2. Ensure BFF route handlers proxy to Gateway with proper error handling
3. Change `apiClient.ts` similarly for consistency

---

## Acceptance Criteria

- [ ] Architecture document (`05_NEXTJS_FRONTEND.md`) accurately describes the current data flow
- [ ] No contradiction between docs and implementation
- [ ] (If Option B): Dead BFF route handlers are identified and either removed or marked with a deprecation comment
- [ ] `NEXT_PUBLIC_GATEWAY_URL` is documented in `.env.template` or equivalent

---

## Dependencies

None — this plan is self-contained, but may be considered lower priority than bug fixes (plans 01-06).
