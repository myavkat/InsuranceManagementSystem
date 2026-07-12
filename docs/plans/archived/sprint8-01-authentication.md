# Plan: Sprint 8 — Authentication Flow

**Plan ID:** `sprint8-01-authentication`
**Priority:** 1 (foundational — other plans depend on this)
**Prerequisite Plans:** None
**Blocks:** `sprint8-02-ssr-data-fetching`, `sprint8-05-notifications`

---

## Objective

Replace the stub login and register pages with functional forms. Add a Next.js middleware that redirects unauthenticated users to `/login`. Implement token refresh via BFF route handler. Implement logout with full cleanup. This plan covers subtask 5 from `docs/tasks/11_SPRINT8_ADVANCED_FRONTEND.md`.

---

## Files to Read First

Before starting, open these files to understand what already exists:

| File | Purpose |
|------|---------|
| `docs/outlines/05_NEXTJS_FRONTEND.md` | Architecture: BFF pattern, auth strategy, data flow |
| `docs/outlines/06_API_GATEWAY_AUTH.md` | Auth service endpoints, token format, security rules |
| `docs/stories/01_AUTHENTICATION.md` | User stories: register, login, token refresh, lockout |
| `frontend/src/lib/store/auth-store.ts` | Existing Zustand auth store (login/logout/setAccessToken) |
| `frontend/src/lib/api/client.ts` | Existing apiClient with 401 → refresh → retry logic |
| `frontend/src/lib/api/auth.ts` | Existing login/register/validateToken API functions |
| `frontend/src/lib/api/types.ts` | ApiResponse, ApiError, PageResponse types |
| `frontend/src/app/(auth)/login/page.tsx` | Current stub login page |
| `frontend/src/app/(auth)/register/page.tsx` | Current stub register page |
| `frontend/src/app/(auth)/layout.tsx` | Auth layout (centers content) |
| `frontend/src/components/layout/header.tsx` | Header with logout button |
| `frontend/src/components/ui/button.tsx` | Button component |
| `frontend/src/components/ui/input.tsx` | Input component |
| `frontend/src/components/ui/card.tsx` | Card component |
| `frontend/src/app/layout.tsx` | Root layout |
| `frontend/src/app/(dashboard)/layout.tsx` | Dashboard layout with sidebar/header |
| `frontend/package.json` | Dependencies (zod, react-hook-form, @hookform/resolvers already installed) |
| `frontend/components.json` | shadcn config: style "base-nova", tailwind v4, lucide icons |

---

## Technical Context

### Dependency Versions (already installed in package.json)
- **React**: 19.2.4 — use `use()` instead of `useContext()`, no `forwardRef` needed
- **Next.js**: 16.2.9 — App Router, middleware at `src/middleware.ts`
- **Zod**: 4.4.3 — use `.pipe()` for refinements (not `.refine()` which is Zod 3)
- **React Hook Form**: 7.80.0 — use with `@hookform/resolvers` 5.4.0
- **Zustand**: 5.0.14 — persist middleware for auth store
- **shadcn/ui**: "base-nova" style with Base UI React (`@base-ui/react`) primitives
- **Tailwind CSS**: v4 with `@tailwindcss/postcss`

### Auth Architecture (from outline)
- JWT stored in Zustand (persisted to localStorage)
- Access token: 15-minute expiry, passed as `Authorization: Bearer <token>`
- Refresh token: opaque, single-use, 7-day expiry, rotated on each refresh
- BFF `/api/auth/[...path]` proxies to Gateway → Auth Service
- After 5 failed logins: 15-minute account lockout

### Patterns to Follow
- All Client Components use `"use client"` directive at the top
- Use `useForm` from react-hook-form with `zodResolver` for form validation
- Use `useMutation` from `@tanstack/react-query` for form submission (not raw fetch)
- Use `useRouter` from `next/navigation` (not `next/router`)
- Use `useAuthStore()` with selector pattern for auth state
- Use `cn()` from `@/lib/utils` for conditional class names
- Use `lucide-react` for all icons
- Shadcn UI primitives from `@/components/ui/` — use `<Button>`, `<Input>`, `<Card>` etc.
- DO NOT use `forwardRef` — pass `ref` as a prop directly (React 19)
- DO NOT use `useContext()` — use `use()` instead (React 19)

### Existing Auth API Functions (in `frontend/src/lib/api/auth.ts`)
```typescript
login(credentials: LoginRequest): Promise<LoginResponse>
  // LoginRequest: { username: string; password: string }
  // LoginResponse: { accessToken: string; refreshToken: string; expiresIn: number; tokenType: string }

register(data: RegisterRequest): Promise<UserResponse>
  // RegisterRequest: { username: string; email: string; password: string }
  // UserResponse: { userId: string; username: string; email: string; roles: string[] }

validateToken(): Promise<{ valid: boolean; userId: string; roles: string[] }>
```

### Existing Auth Store (in `frontend/src/lib/store/auth-store.ts`)
```typescript
useAuthStore() returns:
  accessToken: string | null
  refreshToken: string | null
  expiresAt: number | null     // Unix timestamp in milliseconds
  user: UserInfo | null        // { userId, username, email, roles }
  isAuthenticated: boolean
  login(accessToken, refreshToken, expiresIn, user): void   // expiresIn is in SECONDS
  logout(): void
  setAccessToken(token, expiresIn): void                    // expiresIn is in SECONDS
```

---

## Steps

### Step 1: Create auth Zod schemas

- [x] Create file `frontend/src/lib/schemas/auth.ts`
- [x] Define `loginSchema` using Zod:
  ```
  z.object({
    username: z.string().min(3, "Username must be at least 3 characters"),
    password: z.string().min(1, "Password is required"),
  })
  ```
  Export the inferred type: `export type LoginFormData = z.infer<typeof loginSchema>;`
- [x] Define `registerSchema` using Zod:
  ```
  z.object({
    username: z.string().min(3, "Username must be at least 3 characters"),
    email: z.string().email("Invalid email address"),
    password: z.string().min(8, "Password must be at least 8 characters"),
    confirmPassword: z.string(),
  }).pipe(
    // Use Zod 4 .pipe() with a refinement check:
    // The object is valid if password === confirmPassword,
    // otherwise add an error to confirmPassword path
  )
  ```
  **CRITICAL — Zod 4 refinement syntax:** In Zod 4, use `.pipe()` with a transform or use `z.custom()`:
  ```typescript
  import { z } from "zod";
  
  // Base schema
  const baseSchema = z.object({
    username: z.string().min(3, "Username must be at least 3 characters"),
    email: z.string().email("Invalid email address"),
    password: z.string().min(8, "Password must be at least 8 characters"),
    confirmPassword: z.string(),
  });
  
  // Refinement via pipe — check password match
  export const registerSchema = baseSchema.pipe(
    z.object({
      password: z.string(),
      confirmPassword: z.string(),
    }).refine(
      (data) => data.password === data.confirmPassword,
      { message: "Passwords do not match", path: ["confirmPassword"] }
    )
  );
  ```
  Export the inferred type: `export type RegisterFormData = z.infer<typeof registerSchema>;`

### Step 2: Build the Login page

- [x] Open `frontend/src/app/(auth)/login/page.tsx` — replace full content
- [x] The page MUST be a Client Component (add `"use client"` at top)
- [x] Import:
  - `useForm` from `react-hook-form`
  - `zodResolver` from `@hookform/resolvers/zod`
  - `loginSchema` and `LoginFormData` from `@/lib/schemas/auth`
  - `useMutation` from `@tanstack/react-query`
  - `useRouter` from `next/navigation`
  - `login` API function from `@/lib/api/auth`
  - `useAuthStore` from `@/lib/store/auth-store`
  - `Button`, `Input` from `@/components/ui/`
  - `Card`, `CardContent`, `CardDescription`, `CardHeader`, `CardTitle` from `@/components/ui/card`
  - `FormField` from `@/components/features/form-field` (reuse existing)
  - `ErrorAlert` from `@/components/features/error-alert`
  - `AlertCircle`, `Loader2` from `lucide-react`
  - `Link` from `next/link`
  - `useState` from `react`
- [x] Set up `useForm<LoginFormData>` with `resolver: zodResolver(loginSchema)` and `defaultValues: { username: "", password: "" }`
- [x] Set up `useMutation` that calls the `login()` API function, then on success:
  1. Call `authStore.login(response.accessToken, response.refreshToken, response.expiresIn, user)` — NOTE: login() from the API returns `LoginResponse` which does NOT include user info. You must decode the JWT or call a separate endpoint. **Handle this**: after login, call `validateToken()` (or decode the JWT payload) to get userId, username, roles. The simplest approach: decode the access token on the client side using `JSON.parse(atob(token.split('.')[1]))` to extract `sub`, `username`, `roles` from the JWT claims.
  2. Redirect to `/dashboard` using `router.push("/dashboard")`
- [x] On mutation error, display the error: the `ApiError` message (server-rejected credentials) or a generic message
- [x] The form MUST have:
  - Username input with label "Username", placeholder "Enter your username"
  - Password input with type="password", label "Password", placeholder "Enter your password"
  - Submit button that shows "Signing in..." spinner when `mutation.isPending` and is disabled while pending
  - Link to `/register` below the form: "Don't have an account? Register"
- [x] Wrap the form in the same Card layout used by the current stub (Card → CardHeader → CardTitle "Sign In" + CardDescription → CardContent → form)
- [x] Add a state-level error alert above the form (not inside — use the existing `ErrorAlert` component) that shows when `mutation.isError` and clears when user starts typing (use `useEffect` or watch field changes)

### Step 3: Build the Register page

- [x] Open `frontend/src/app/(auth)/register/page.tsx` — replace full content
- [x] Client Component with `"use client"`
- [x] Import same patterns as login page, plus `registerSchema`, `RegisterFormData`, and the `register` API function
- [x] Set up `useForm<RegisterFormData>` with `resolver: zodResolver(registerSchema)` and default values for all 4 fields
- [x] Set up `useMutation` that calls `register()` API function, then on success:
  1. Show a success message (or redirect to `/login` with a query param like `?registered=true`)
  2. Use `router.push("/login?registered=true")` — the login page can optionally show a success toast
- [x] The form MUST have:
  - Username input
  - Email input with type="email"
  - Password input with type="password"
  - Confirm Password input with type="password"
  - Each field shows inline validation errors via `FormField` component
  - Submit button disabled while pending, shows "Creating account..." with spinner
  - Link to `/login`: "Already have an account? Sign in"
- [x] Display mutation errors via `ErrorAlert` — e.g., "Username already taken" from the API

### Step 4: Create middleware.ts for route protection

- [x] Create file `frontend/src/middleware.ts`
- [x] The middleware MUST check if the user has a valid auth token when accessing any route under `/(dashboard)/*`
- [x] Implementation pattern:
  ```typescript
  import { NextRequest, NextResponse } from "next/server";

  // Paths that do NOT require authentication
  const PUBLIC_PATHS = ["/login", "/register", "/api"];

  export function middleware(request: NextRequest) {
    const { pathname } = request.nextUrl;

    // Allow public paths through
    if (PUBLIC_PATHS.some((p) => pathname.startsWith(p))) {
      return NextResponse.next();
    }

    // Check for auth token in cookies (preferred) or Authorization header
    // Since we use Zustand + localStorage, the token is NOT in cookies by default.
    // The middleware runs on the server and CANNOT read localStorage.
    //
    // DECISION NEEDED — The outline says "JWT stored in HTTP-only cookie (preferred) or localStorage."
    // The current implementation uses localStorage via Zustand persist.
    //
    // For this plan, use a dual approach:
    // 1. Keep the Zustand localStorage persist for client-side API calls
    // 2. ALSO set a non-HTTP-only cookie `auth_token` when the user logs in
    //    (done in the login page success handler via document.cookie)
    // 3. The middleware reads this cookie to decide whether to redirect
    //
    // IMPLEMENT IT LIKE THIS:
    const token = request.cookies.get("auth_token")?.value;

    if (!token) {
      const loginUrl = new URL("/login", request.url);
      loginUrl.searchParams.set("redirect", pathname);
      return NextResponse.redirect(loginUrl);
    }

    // OPTIONAL but recommended: also check expiry
    // The auth_token cookie should store a minimal JWT or just "1" as a flag
    // Since we can't verify JWT in middleware without the public key,
    // just check presence for now. The actual JWT validation happens
    // at the API Gateway level.

    return NextResponse.next();
  }

  export const config = {
    matcher: [
      // Match all dashboard routes and any future protected routes
      "/((?!_next/static|_next/image|favicon.ico|login|register|api).*)",
    ],
  };
  ```
- [x] IMPORTANT: The middleware matcher must NOT match `/login`, `/register`, `/api/*`, or static assets (`/_next/*`)
- [x] Test: navigating to `/customers` while not logged in should redirect to `/login?redirect=%2Fcustomers`

### Step 5: Set auth cookie on login (dual cookie + Zustand)

- [x] In the login page's `onSuccess` handler, after calling `authStore.login(...)`, also set a cookie:
  ```typescript
  document.cookie = `auth_token=1; path=/; max-age=${expiresIn}; SameSite=Lax`;
  ```
  Where `expiresIn` comes from the login response (in seconds, so use directly as max-age seconds).
- [x] On logout, clear this cookie:
  ```typescript
  document.cookie = "auth_token=; path=/; max-age=0; SameSite=Lax";
  ```

### Step 6: Enhance logout to clear the cookie

- [x] The header component (`frontend/src/components/layout/header.tsx`) already calls `useAuthStore().logout()` on the logout button click
- [x] Update the logout handler in the header to ALSO clear the auth cookie AND redirect to `/login`:
  ```typescript
  const handleLogout = () => {
    logout();
    document.cookie = "auth_token=; path=/; max-age=0; SameSite=Lax";
    window.location.href = "/login";
  };
  ```
- [x] Replace the current `onClick={logout}` on the LogOut button with `onClick={handleLogout}`

### Step 7: Handle redirect after login

- [x] In the login page, after successful login, check for the `redirect` query parameter:
  ```typescript
  const searchParams = useSearchParams();
  const redirectTo = searchParams.get("redirect") || "/dashboard";
  router.push(redirectTo);
  ```
- [x] The `useSearchParams` import is from `next/navigation` (Next.js 16 App Router)

### Step 8: Update the auth layout to redirect authenticated users

- [x] Open `frontend/src/app/(auth)/layout.tsx`
- [x] This is currently a Server Component. Make it a Client Component (add `"use client"`)
- [x] Import `useAuthStore` and `useRouter` and `useEffect`
- [x] Add a `useEffect` that checks if `isAuthenticated` is true — if so, redirect to `/dashboard`
- [x] This prevents authenticated users from seeing the login/register pages

### Step 9: Verify and test

- [x] Verify the login form renders with all fields
- [x] Verify validation errors show inline (try submitting empty form)
- [x] Verify the register form renders with password confirmation validation
- [x] Verify the middleware redirects unauthenticated users to `/login`
- [x] Verify logout clears the cookie and redirects to `/login`
- [x] Verify the `auth_token` cookie is set on login and cleared on logout
- [x] Run `npx tsc --noEmit` from `frontend/` to check for TypeScript errors

---

## Acceptance Criteria

1. Login page has a functional form with Zod validation (username ≥ 3 chars + password required)
2. Register page has a functional form with Zod validation (all 4 fields, password match check)
3. Successful login stores tokens in Zustand + sets `auth_token` cookie, redirects to dashboard
4. Successful registration redirects to login page with `?registered=true`
5. `middleware.ts` redirects unauthenticated requests to `/login` with the intended path in `?redirect=`
6. Logout clears Zustand state, clears the auth cookie, redirects to `/login`
7. Authenticated users visiting `/login` or `/register` are redirected to `/dashboard`
8. All forms show loading states during submission and display API errors
9. No TypeScript errors
10. Backend auth endpoints are assumed available — stub calls will fail gracefully with visible error messages

---

## Common Mistakes to Avoid

- **DO NOT** use `next/router` — use `next/navigation` (App Router)
- **DO NOT** use `forwardRef` — React 19 accepts `ref` as a prop directly
- **DO NOT** use `useContext()` — use `use()` from React 19
- **DO NOT** call `authStore.login()` without user info — decode the JWT payload to get `sub`, `username`, `roles`
- **DO NOT** forget to set `document.cookie` on login AND clear it on logout — the middleware depends on it
- **DO NOT** read `localStorage` in middleware — it runs on the server
- **DO NOT** use Zod 3 `.refine()` on the schema directly — Zod 4 uses `.pipe()`
- **DO NOT** redirect to a hardcoded path on login — check for `?redirect=` query param
- **DO NOT** remove the `"use client"` directive from client components
- **DO NOT** import from `@radix-ui/*` — use `@base-ui/react` or `@/components/ui/*`
