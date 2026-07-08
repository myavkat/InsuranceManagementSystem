# Plan: Fix Middleware Auth Bypass (middleware.ts)

**Source:** Sprint 8 code review — finding 6

---

## Objective

Fix an auth bypass in the Next.js middleware where `PUBLIC_PATHS` uses `startsWith` instead of exact matching, allowing similarly-prefixed routes to skip authentication.

---

## Severity

🟠 **Medium** — Security gap. Routes like `/login-callback` or `/register-sso` would be unintentionally treated as public.

---

## Files to Read First

| File | Why |
|------|-----|
| `frontend-next/src/middleware.ts` | The file being fixed (49 lines) |
| `frontend-next/src/app/(auth)/login/page.tsx` | To confirm `/login` is the only login route |
| `frontend-next/src/app/(auth)/register/page.tsx` | To confirm `/register` is the only register route |

---

## Steps

### Finding — Fix: `startsWith` auth bypass (line 19 of actual file)

**Note**: The code review report cited line 4271 from the diff file. In the actual source file, the relevant line is approximately line 19:
```typescript
const isPublicPage = PUBLIC_PATHS.some((p) => pathname.startsWith(p));
```

**The bug**: `pathname.startsWith("/login")` matches ANY path that begins with `/login`, including `/login-callback`, `/login/oauth`, `/login-help`, etc. Same for `/register`. If a developer adds a new route that happens to start with these prefixes, it will be accidentally treated as public and skip authentication.

**The fix**: Change from `startsWith` prefix matching to exact path matching. This ensures only the exact `/login` and `/register` routes (and their sub-paths if needed) are treated as public.

**Option A — Exact match only (stricter)**:
```typescript
const isPublicPage = PUBLIC_PATHS.some((p) => pathname === p);
```

**Option B — Exact match OR direct sub-path (recommended)**: This allows `/login` and `/login/` (with trailing slash) and `/login/forgot-password` if a sub-route is needed later, but NOT `/login-callback`:
```typescript
const isPublicPage = PUBLIC_PATHS.some(
  (p) => pathname === p || pathname.startsWith(p + "/")
);
```

Choose **Option B** — it correctly handles:
- `/login` → public ✓
- `/login/` → public ✓
- `/login-callback` → NOT public ✓
- `/register` → public ✓
- `/register/` → public ✓
- `/register-sso` → NOT public ✓

**Exact change** — Find line 19 in `frontend-next/src/middleware.ts` (the line with `PUBLIC_PATHS.some`). Replace:
```typescript
const isPublicPage = PUBLIC_PATHS.some((p) => pathname.startsWith(p));
```
with:
```typescript
const isPublicPage = PUBLIC_PATHS.some(
  (p) => pathname === p || pathname.startsWith(p + "/")
);
```

---

## Acceptance Criteria

- [ ] `/login` → allowed through without redirect (existing behavior preserved)
- [ ] `/register` → allowed through without redirect (existing behavior preserved)
- [ ] `/login-callback` → redirected to `/login?redirect=/login-callback` (NEW: auth enforced)
- [ ] `/register-sso` → redirected to `/login?redirect=/register-sso` (NEW: auth enforced)
- [ ] `/dashboard` → redirected to `/login?redirect=/dashboard` (existing behavior preserved)
- [ ] API routes (`/api/*`) are still allowed through without redirect (existing behavior preserved)

---

## Dependencies

None — this plan is self-contained.
