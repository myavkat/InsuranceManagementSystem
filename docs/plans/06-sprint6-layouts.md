# 06 — Sprint 6: Layout Components

## Status: NOT STARTED

## Objective

Create the two route group layouts (`(auth)` and `(dashboard)`), build the sidebar and header components, set up loading skeletons for each route segment, and implement responsive breakpoints with a collapsible sidebar.

## Prerequisites

- **Plan 04 must be complete** (root layout exists and builds)
- **Plan 05 must be complete** (Zustand stores and React Query provider exist — the dashboard layout imports `useUIStore` for sidebar state)
- Read these files before starting:
  - `docs/outlines/05_NEXTJS_FRONTEND.md` — Section 1 (App Router layout pattern), Section 3 (Server Components default)
  - `frontend-next/AGENTS.md` — Next.js 16 route groups and layouts docs
  - `frontend-next/src/lib/store/ui-store.ts` — `useUIStore` API (sidebarOpen, toggleSidebar, setSidebarOpen, theme)
  - `frontend-next/src/app/layout.tsx` — root layout structure (you'll nest route group layouts inside this)
  - `frontend-next/src/components/ui/skeleton.tsx` — Skeleton component API (for loading states)
  - `frontend-next/src/components/ui/button.tsx` — Button component API (for sidebar toggle, header actions)
  - `frontend-next/src/lib/utils.ts` — `cn()` helper

## Architecture Notes

- **Route groups** `(auth)` and `(dashboard)` are Next.js convention — the parenthesized folder name does NOT appear in the URL. `/login` maps to `app/(auth)/login/page.tsx`, and `/dashboard` maps to `app/(dashboard)/dashboard/page.tsx`.
- **Layouts cascade:** Root layout → route group layout → page. The `(auth)` layout provides a centered card wrapper; the `(dashboard)` layout provides sidebar + header + main content area.
- **Responsive strategy:** Mobile-first. Sidebar is a sliding overlay on small screens (`< lg`), and a persistent sidebar on large screens (`>= lg`).
- **Lucide icons** are already in `package.json` (`lucide-react`). Use them for all icons. Do NOT add another icon library.

## Steps

### Step 1: Create the auth route group layout

**File to CREATE:** `frontend-next/src/app/(auth)/layout.tsx`

This layout wraps login/register pages in a centered card. It should NOT include sidebar or header.

```tsx
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Authentication",
};

export default function AuthLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <main className="flex min-h-screen items-center justify-center bg-muted/50 px-4">
      <div className="w-full max-w-md">{children}</div>
    </main>
  );
}
```

**What this does:**
- Centers content vertically and horizontally on screen
- Constrains the card width to `max-w-md` (448px) — appropriate for login/register forms
- Uses `bg-muted/50` for a subtle background that distinguishes auth pages from dashboard
- Sets page metadata title to "Authentication" — will render as "Authentication | Insurance Management System" via the root layout's template

### Step 2: Create placeholder login and register pages

**File to CREATE:** `frontend-next/src/app/(auth)/login/page.tsx`

```tsx
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

export default function LoginPage() {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Sign In</CardTitle>
        <CardDescription>
          Enter your credentials to access the system
        </CardDescription>
      </CardHeader>
      <CardContent>
        <p className="text-sm text-muted-foreground">
          Login form will be implemented in a future sprint.
        </p>
      </CardContent>
    </Card>
  );
}
```

**File to CREATE:** `frontend-next/src/app/(auth)/register/page.tsx`

```tsx
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

export default function RegisterPage() {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Create Account</CardTitle>
        <CardDescription>
          Register a new account to get started
        </CardDescription>
      </CardHeader>
      <CardContent>
        <p className="text-sm text-muted-foreground">
          Registration form will be implemented in a future sprint.
        </p>
      </CardContent>
    </Card>
  );
}
```

These are placeholder pages — the actual form implementations come in a later sprint. They import from `@/components/ui/card` which already exists.

### Step 3: Build the sidebar component

**File to CREATE:** `frontend-next/src/components/layout/sidebar.tsx`

This is a Client Component (needs interactivity for collapse toggle and active link highlighting).

```tsx
"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";
import { useUIStore } from "@/lib/store/ui-store";
import { Button } from "@/components/ui/button";
import {
  LayoutDashboard,
  Users,
  Shield,
  Calculator,
  Car,
  PanelLeftClose,
  PanelLeft,
} from "lucide-react";

interface NavItem {
  href: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
}

const navItems: NavItem[] = [
  { href: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { href: "/customers", label: "Customers", icon: Users },
  { href: "/insurances", label: "Insurances", icon: Shield },
  { href: "/estimations", label: "Estimations", icon: Calculator },
  { href: "/vehicles", label: "Vehicles", icon: Car },
];

export function Sidebar() {
  const pathname = usePathname();
  const { sidebarOpen, toggleSidebar } = useUIStore();

  return (
    <>
      {/* Mobile overlay backdrop */}
      {sidebarOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/50 lg:hidden"
          onClick={toggleSidebar}
          aria-hidden="true"
        />
      )}

      {/* Sidebar panel */}
      <aside
        className={cn(
          "fixed top-0 left-0 z-50 flex h-full w-64 flex-col border-r bg-sidebar text-sidebar-foreground transition-transform duration-300",
          "lg:static lg:z-auto lg:translate-x-0",
          sidebarOpen ? "translate-x-0" : "-translate-x-full"
        )}
      >
        {/* Sidebar header with collapse button */}
        <div className="flex h-14 items-center justify-between border-b px-4">
          <Link href="/dashboard" className="text-lg font-semibold tracking-tight">
            IMS
          </Link>
          <Button
            variant="ghost"
            size="icon"
            onClick={toggleSidebar}
            aria-label="Toggle sidebar"
            className="hidden lg:inline-flex"
          >
            <PanelLeftClose className="h-5 w-5" />
          </Button>
        </div>

        {/* Navigation links */}
        <nav className="flex-1 space-y-1 p-3">
          {navItems.map((item) => {
            const isActive =
              pathname === item.href || pathname.startsWith(item.href + "/");
            return (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  "flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors",
                  isActive
                    ? "bg-sidebar-accent text-sidebar-accent-foreground"
                    : "text-sidebar-foreground hover:bg-sidebar-accent/50 hover:text-sidebar-accent-foreground"
                )}
              >
                <item.icon className="h-4 w-4" />
                {item.label}
              </Link>
            );
          })}
        </nav>
      </aside>

      {/* Mobile toggle button (visible when sidebar is closed) */}
      <Button
        variant="outline"
        size="icon"
        onClick={toggleSidebar}
        className={cn(
          "fixed top-3 left-3 z-50 lg:hidden",
          sidebarOpen && "hidden"
        )}
        aria-label="Open sidebar"
      >
        <PanelLeft className="h-5 w-5" />
      </Button>
    </>
  );
}
```

**Key design decisions:**
- Sidebar width: `w-64` (256px) — standard for admin dashboards.
- Mobile: slides in/out with CSS `translate-x` transition. Overlay backdrop closes it on tap.
- Desktop (`lg:`): static sidebar, no overlay. Collapse button hides it permanently.
- Active link detection: uses `usePathname()` to highlight the current route.
- `z-50` on sidebar to overlay above content on mobile. `z-40` for overlay backdrop.
- Icons from `lucide-react` — already a dependency.

### Step 4: Build the header component

**File to CREATE:** `frontend-next/src/components/layout/header.tsx`

This is a Client Component (needs user menu dropdown interactivity).

```tsx
"use client";

import { useUIStore } from "@/lib/store/ui-store";
import { useAuthStore } from "@/lib/store/auth-store";
import { Button } from "@/components/ui/button";
import {
  PanelLeft,
  Bell,
  LogOut,
  User,
  Sun,
  Moon,
  Monitor,
} from "lucide-react";

export function Header() {
  const { toggleSidebar, theme, setTheme } = useUIStore();
  const { user, logout, isAuthenticated } = useAuthStore();

  const cycleTheme = () => {
    const next: Record<string, "light" | "dark" | "system"> = {
      light: "dark",
      dark: "system",
      system: "light",
    };
    setTheme(next[theme]);
  };

  const ThemeIcon = theme === "dark" ? Moon : theme === "light" ? Sun : Monitor;

  return (
    <header className="sticky top-0 z-30 flex h-14 items-center gap-4 border-b bg-background px-4">
      {/* Mobile sidebar toggle */}
      <Button
        variant="ghost"
        size="icon"
        onClick={toggleSidebar}
        className="lg:hidden"
        aria-label="Toggle sidebar"
      >
        <PanelLeft className="h-5 w-5" />
      </Button>

      {/* Spacer */}
      <div className="flex-1" />

      {/* Theme toggle */}
      <Button
        variant="ghost"
        size="icon"
        onClick={cycleTheme}
        aria-label={`Current theme: ${theme}. Click to switch.`}
      >
        <ThemeIcon className="h-5 w-5" />
      </Button>

      {/* Notifications (placeholder) */}
      <Button variant="ghost" size="icon" aria-label="Notifications">
        <Bell className="h-5 w-5" />
      </Button>

      {/* User section */}
      {isAuthenticated && user ? (
        <div className="flex items-center gap-2">
          <div className="hidden text-sm sm:block">
            <p className="font-medium leading-none">{user.username}</p>
            <p className="text-xs text-muted-foreground">{user.email}</p>
          </div>
          <Button variant="ghost" size="icon" aria-label="User menu">
            <User className="h-5 w-5" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            onClick={logout}
            aria-label="Sign out"
          >
            <LogOut className="h-5 w-5" />
          </Button>
        </div>
      ) : (
        <div className="text-sm text-muted-foreground">Not signed in</div>
      )}
    </header>
  );
}
```

**Key design decisions:**
- `sticky top-0` — header stays visible when scrolling long pages.
- `z-30` — lower than sidebar overlay (`z-40`/`z-50`) so sidebar overlays above header on mobile.
- Theme cycling: light → dark → system → light. Uses the `useUIStore.theme` field.
- User info: hidden on small screens (`hidden sm:block`) to save space.
- Logout button calls `useAuthStore.logout()` directly — clears token and redirects (the redirect logic belongs in the API client layer, Plan 07).

### Step 5: Create the dashboard route group layout

**File to CREATE:** `frontend-next/src/app/(dashboard)/layout.tsx`

```tsx
import type { Metadata } from "next";
import { Sidebar } from "@/components/layout/sidebar";
import { Header } from "@/components/layout/header";

export const metadata: Metadata = {
  title: "Dashboard",
};

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar />
      <div className="flex flex-1 flex-col overflow-hidden">
        <Header />
        <main className="flex-1 overflow-y-auto p-6">{children}</main>
      </div>
    </div>
  );
}
```

**What this does:**
- Full-height layout (`h-screen`) with sidebar on the left, header + content on the right.
- Content area scrolls independently (`overflow-y-auto`) — header and sidebar stay fixed.
- Sidebar and Header are Client Components (declared `"use client"` in their own files). This layout file remains a Server Component — Next.js can render it on the server and stream it.
- Sets page metadata title to "Dashboard" — will render as "Dashboard | Insurance Management System" via the root layout template.

### Step 6: Create the dashboard index page

**File to CREATE:** `frontend-next/src/app/(dashboard)/dashboard/page.tsx`

```tsx
export default function DashboardPage() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Dashboard</h1>
        <p className="text-muted-foreground">
          Welcome to the Insurance Management System.
        </p>
      </div>
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        {/* Placeholder stat cards */}
        <DashboardCard title="Active Policies" value="—" />
        <DashboardCard title="Pending Estimations" value="—" />
        <DashboardCard title="Customers" value="—" />
      </div>
    </div>
  );
}

function DashboardCard({ title, value }: { title: string; value: string }) {
  return (
    <div className="rounded-lg border bg-card p-4 text-card-foreground shadow-sm">
      <p className="text-sm font-medium text-muted-foreground">{title}</p>
      <p className="mt-1 text-2xl font-bold">{value}</p>
    </div>
  );
}
```

### Step 7: Create loading skeletons

**File to CREATE:** `frontend-next/src/app/(dashboard)/dashboard/loading.tsx`

```tsx
import { Skeleton } from "@/components/ui/skeleton";

export default function DashboardLoading() {
  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-4 w-72" />
      </div>
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        <Skeleton className="h-28 rounded-lg" />
        <Skeleton className="h-28 rounded-lg" />
        <Skeleton className="h-28 rounded-lg" />
      </div>
    </div>
  );
}
```

**File to CREATE:** `frontend-next/src/app/(dashboard)/loading.tsx`

This is the loading state for the entire dashboard route group (shown when navigating between dashboard pages).

```tsx
import { Skeleton } from "@/components/ui/skeleton";

export default function DashboardGroupLoading() {
  return (
    <div className="flex h-screen overflow-hidden">
      {/* Sidebar skeleton */}
      <aside className="hidden w-64 flex-col gap-4 border-r bg-sidebar p-4 lg:flex">
        <Skeleton className="h-6 w-20" />
        <Skeleton className="h-8 w-full" />
        <Skeleton className="h-8 w-full" />
        <Skeleton className="h-8 w-full" />
        <Skeleton className="h-8 w-full" />
        <Skeleton className="h-8 w-full" />
      </aside>
      {/* Main area skeleton */}
      <div className="flex flex-1 flex-col">
        <Skeleton className="h-14 w-full rounded-none" />
        <div className="flex-1 p-6 space-y-6">
          <Skeleton className="h-8 w-48" />
          <Skeleton className="h-4 w-72" />
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            <Skeleton className="h-28 rounded-lg" />
            <Skeleton className="h-28 rounded-lg" />
            <Skeleton className="h-28 rounded-lg" />
          </div>
        </div>
      </div>
    </div>
  );
}
```

**File to CREATE:** `frontend-next/src/app/(auth)/loading.tsx`

```tsx
import { Skeleton } from "@/components/ui/skeleton";

export default function AuthLoading() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-muted/50 px-4">
      <div className="w-full max-w-md space-y-4">
        <Skeleton className="mx-auto h-8 w-48" />
        <Skeleton className="h-64 w-full rounded-lg" />
      </div>
    </main>
  );
}
```

**How loading.tsx works in Next.js 16:** When a page or layout is loading, Next.js automatically shows the nearest `loading.tsx` file. These are Server Components by default — they render instantly while data is being fetched.

### Step 8: Verify build

Run from repo root:

```bash
cd frontend-next && npm run build
```

Common issues:
- Import errors: verify all paths match. The `@/` alias maps to `frontend-next/src/`.
- If `lucide-react` icons are not found, the import name might differ — check `node_modules/lucide-react` for available exports.
- If the build fails because `page.tsx` uses `redirect("/dashboard")` from Plan 04, update it to the placeholder described in Plan 04 Step 6.

### Step 9: Verify lint

Run from repo root:

```bash
cd frontend-next && npm run lint
```

Fix any lint errors before marking this plan complete.

## Acceptance Criteria

- [ ] `frontend-next/src/app/(auth)/layout.tsx` exists — centered card layout
- [ ] `frontend-next/src/app/(auth)/login/page.tsx` exists — placeholder login page
- [ ] `frontend-next/src/app/(auth)/register/page.tsx` exists — placeholder register page
- [ ] `frontend-next/src/components/layout/sidebar.tsx` exists — nav links, collapse toggle, mobile overlay
- [ ] `frontend-next/src/components/layout/header.tsx` exists — user menu, theme toggle, notifications
- [ ] `frontend-next/src/app/(dashboard)/layout.tsx` exists — sidebar + header + content
- [ ] `frontend-next/src/app/(dashboard)/dashboard/page.tsx` exists — dashboard index with placeholder cards
- [ ] `frontend-next/src/app/(dashboard)/dashboard/loading.tsx` exists — dashboard page skeleton
- [ ] `frontend-next/src/app/(dashboard)/loading.tsx` exists — dashboard group skeleton
- [ ] `frontend-next/src/app/(auth)/loading.tsx` exists — auth loading skeleton
- [ ] `http://localhost:3000/login` renders the auth layout with a centered card
- [ ] `http://localhost:3000/dashboard` renders the dashboard layout with sidebar and header
- [ ] Sidebar collapses/expands on mobile (viewport < 1024px)
- [ ] Active nav link is highlighted in sidebar
- [ ] `npm run build` passes
- [ ] `npm run lint` passes
