# Plan 03: Frontend — Terminology Renaming

**Status:** Completed
**Depends on:** Plan 01 (needs new status enum values for the status badge)
**Blocks:** Plan 04 (needs renamed pages/sidebar before adding payment flow)

---

## Objective

Rename "Estimation" terminology across all frontend UI text to match the new naming convention:
- The underlying record/entity → "Premium" (detail view labels)
- The action of creating a new one → "Estimation" (button labels, form title)
- Already-created records list → "Offers" (sidebar nav, list page title)

Update the `StatusBadge` component and status filter dropdowns to recognize the new status values.

---

## Files to Read First (before writing any code)

| # | File Path | Purpose |
|---|-----------|---------|
| 1 | `frontend-next/src/components/layout/sidebar.tsx` | Nav item label to change |
| 2 | `frontend-next/src/app/(dashboard)/estimations/page.tsx` | List page title |
| 3 | `frontend-next/src/app/(dashboard)/estimations/[id]/page.tsx` | Detail page title |
| 4 | `frontend-next/src/app/(dashboard)/estimations/new/page.tsx` | Create form page title |
| 5 | `frontend-next/src/components/features/estimations/estimation-list.tsx` | List component: page header, empty state, filter labels |
| 6 | `frontend-next/src/components/features/estimations/estimation-detail.tsx` | Detail component: page header, card labels, status labels |
| 7 | `frontend-next/src/components/features/estimations/estimation-form.tsx` | Form component: page header, step labels |
| 8 | `frontend-next/src/components/features/status-badge.tsx` | Status badge mapping |
| 9 | `frontend-next/src/lib/api/estimations.ts` | TypeScript `EstimationStatus` type |
| 10 | `frontend-next/src/app/(dashboard)/dashboard/page.tsx` | Dashboard card label |
| 11 | `frontend-next/src/components/features/customers/customer-detail.tsx` | "Estimation History" card |
| 12 | `frontend-next/src/components/features/insurances/insurance-detail.tsx` | Text about "estimations" |
| 13 | `frontend-next/src/app/layout.tsx` | Meta description |
| 14 | `frontend-next/src/lib/notification-types.ts` | Notification constant name |
| 15 | `frontend-next/src/hooks/use-websocket.ts` | WebSocket handler referencing estimations |
| 16 | `docs/outlines/05_NEXTJS_FRONTEND.md` | Frontend architecture conventions |

---

## Terminology Rules (apply consistently across ALL files)

| Context | Old Text | New Text |
|---------|---------|----------|
| Navigation sidebar | "Estimations" | "Offers" |
| List page title/heading | "Estimations" | "Offers" |
| List page description | "Manage insurance estimations" | "Manage insurance offers" |
| Empty state title | "No estimations found" | "No offers found" |
| Empty state description | "Get started by creating a new estimation." | "Get started by creating a new estimation." *(unchanged — describes the action)* |
| Create button | "New Estimation" | "New Estimation" *(unchanged — describes the action)* |
| Detail page title (metadata) | "Estimation Detail" | "Premium Detail" |
| Detail page heading | "Estimation #..." | "Premium #..." |
| Detail page description | "Estimation details" | "Premium details" |
| Form page title (metadata) | "New Estimation" | "New Estimation" *(unchanged)* |
| Form page heading | "New Estimation" | "New Estimation" *(unchanged)* |
| Form page description | "Create a new insurance estimation" | "Create a new insurance estimation" *(unchanged)* |
| "Estimated Premium" (calculated amount) | "Estimated Premium" | "Calculated Premium" |
| Status badge: STARTED | "Started" | "Started" *(unchanged)* |
| Status badge: COMPLETED | "Completed" | "Completed" *(keep for legacy data)* |
| Status badge: REJECTED | "Rejected" | "Rejected" *(unchanged)* |
| Status badge: WAITING_APPROVAL | *(new)* | "Waiting Approval" |
| Status badge: PAYMENT_WAITING | *(new)* | "Payment Waiting" |
| Status badge: ACTIVE | *(new)* | "Active" |
| Status filter dropdown | "STARTED", "COMPLETED", "REJECTED" | Add: "WAITING_APPROVAL", "PAYMENT_WAITING", "ACTIVE" |
| Dashboard card | "Pending Estimations" | "Pending Offers" |
| Customer detail | "Estimation History" | "Offer History" |
| Insurance detail | "Deactivated products cannot be used for new estimations." | "Deactivated products cannot be used for new estimations." *(unchanged — describes the action)* |
| Meta description | "Insurance premium estimation and policy management system" | "Insurance premium estimation and policy management system" *(unchanged)* |
| Notification constant | `ESTIMATION_STATUS: "estimation_status"` | *(keep as-is — this is a wire protocol constant, not UI text)* |
| API type names | `EstimationStatus`, `EstimationResponse`, etc. | *(keep as-is — these are code identifiers, not UI text)* |

---

## Steps

### Step 1: Update the TypeScript EstimationStatus Type

**File:** `frontend-next/src/lib/api/estimations.ts`

Add the new status values to the `EstimationStatus` type. Currently (line 4):

```typescript
export type EstimationStatus = "STARTED" | "COMPLETED" | "REJECTED";
```

Change to:

```typescript
export type EstimationStatus = "STARTED" | "WAITING_APPROVAL" | "PAYMENT_WAITING" | "ACTIVE" | "COMPLETED" | "REJECTED";
```

Keep `COMPLETED` — legacy data may still have this status.

### Step 2: Update the StatusBadge Component

**File:** `frontend-next/src/components/features/status-badge.tsx`

Add the new status mappings to the `statusMap` object. Currently (lines 9-16):

```typescript
const statusMap: Record<string, { label: string; variant: StatusVariant }> = {
  STARTED:    { label: "Started",    variant: "secondary" },
  COMPLETED:  { label: "Completed",  variant: "default" },
  REJECTED:   { label: "Rejected",   variant: "destructive" },
  PENDING:    { label: "Pending",    variant: "secondary" },
  ACTIVE:     { label: "Active",     variant: "default" },
  INACTIVE:   { label: "Inactive",   variant: "outline" },
};
```

Change to:

```typescript
const statusMap: Record<string, { label: string; variant: StatusVariant }> = {
  STARTED:           { label: "Started",           variant: "secondary" },
  WAITING_APPROVAL:  { label: "Waiting Approval",  variant: "secondary" },
  PAYMENT_WAITING:   { label: "Payment Waiting",   variant: "secondary" },
  ACTIVE:            { label: "Active",            variant: "default" },
  COMPLETED:         { label: "Completed",         variant: "default" },
  REJECTED:          { label: "Rejected",          variant: "destructive" },
  PENDING:           { label: "Pending",           variant: "secondary" },
  INACTIVE:          { label: "Inactive",          variant: "outline" },
};
```

Note: `ACTIVE` was already in the map but had `variant: "default"` — keep it as `default` since it's a terminal positive state.

### Step 3: Update the Sidebar Navigation

**File:** `frontend-next/src/components/layout/sidebar.tsx`

Change the "Estimations" nav item (line 30):
```typescript
{ href: "/estimations", label: "Estimations", icon: Calculator },
```
To:
```typescript
{ href: "/estimations", label: "Offers", icon: Calculator },
```

**Important:** Do NOT change the `href` value `/estimations` — the URL routes must stay the same. Only change the display label.

### Step 4: Update the Estimation List Page

**File:** `frontend-next/src/app/(dashboard)/estimations/page.tsx`

Change the metadata title (line 8):
```typescript
title: "Estimations",
```
To:
```typescript
title: "Offers",
```

**File:** `frontend-next/src/components/features/estimations/estimation-list.tsx`

Make these changes:

**4a.** Page header title (line 160):
```typescript
title="Estimations"
```
To:
```typescript
title="Offers"
```

**4b.** Page header description (line 161):
```typescript
description="Manage insurance estimations"
```
To:
```typescript
description="Manage insurance offers"
```

**4c.** Empty state title (line 173):
```typescript
title="No estimations found"
```
To:
```typescript
title="No offers found"
```

**4d.** Empty state description (line 174) — keep "Get started by creating a new estimation." unchanged (describes the action).

**4e.** Status filter dropdown options (lines 31-36) — update to include new statuses:
```typescript
const statusOptions: { value: string; label: string }[] = [
  { value: "", label: "All statuses" },
  { value: "STARTED", label: "Started" },
  { value: "WAITING_APPROVAL", label: "Waiting Approval" },
  { value: "PAYMENT_WAITING", label: "Payment Waiting" },
  { value: "ACTIVE", label: "Active" },
  { value: "COMPLETED", label: "Completed" },
  { value: "REJECTED", label: "Rejected" },
];
```

**4f.** Column header for premium (line 60): keep "Premium" — it's already correct.

### Step 5: Update the Estimation Detail Page

**File:** `frontend-next/src/app/(dashboard)/estimations/[id]/page.tsx`

Change metadata title (line 5):
```typescript
title: "Estimation Detail",
```
To:
```typescript
title: "Premium Detail",
```

**File:** `frontend-next/src/components/features/estimations/estimation-detail.tsx`

Make these changes:

**5a.** Page header title (line 96):
```typescript
title={`Estimation #${estimation.id.slice(0, 8)}`}
```
To:
```typescript
title={`Premium #${estimation.id.slice(0, 8)}`}
```

**5b.** Page header description (line 97):
```typescript
description="Estimation details"
```
To:
```typescript
description="Premium details"
```

**5c.** Status banner — the "Estimated Premium" label (line 127):
```typescript
<p className="text-sm font-medium text-green-700 dark:text-green-400">Estimated Premium</p>
```
To:
```typescript
<p className="text-sm font-medium text-green-700 dark:text-green-400">Calculated Premium</p>
```

**5d.** "Insurance Information" card — "Base Premium" label (line 163):
```typescript
<DetailItem label="Base Premium" value={formattedPremium ?? "Pending calculation..."} />
```
To:
```typescript
<DetailItem label="Premium" value={formattedPremium ?? "Pending calculation..."} />
```

**5e.** The polling condition (line 26 and 87): Change the `refetchInterval` to also poll for the new intermediate statuses. Currently it polls when `status === "STARTED"`. Change to poll when status is STARTED, WAITING_APPROVAL, or PAYMENT_WAITING:

At line 24-27:
```typescript
refetchInterval: (query) => {
  const data = query.state.data;
  return data?.status === "STARTED" ? 5000 : false;
},
```
Change to:
```typescript
refetchInterval: (query) => {
  const data = query.state.data;
  if (!data?.status) return false;
  return ["STARTED", "WAITING_APPROVAL", "PAYMENT_WAITING"].includes(data.status) ? 5000 : false;
},
```

And the `isPolling` check at line 87:
```typescript
const isPolling = estimation.status === "STARTED";
```
Change to:
```typescript
const isPolling = ["STARTED", "WAITING_APPROVAL", "PAYMENT_WAITING"].includes(estimation.status);
```

**5f.** The status banner border colors (lines 108-111): Currently it highlights green for COMPLETED and red for REJECTED. Update to handle all statuses:
```typescript
<Card className={
  estimation.status === "ACTIVE" || estimation.status === "COMPLETED" ? "border-green-500" :
  estimation.status === "REJECTED" ? "border-destructive" :
  estimation.status === "WAITING_APPROVAL" ? "border-yellow-500" :
  estimation.status === "PAYMENT_WAITING" ? "border-blue-500" : ""
}>
```

Use these Tailwind border color classes:
- `border-green-500` for ACTIVE, COMPLETED (positive terminal states)
- `border-destructive` for REJECTED (failure)
- `border-yellow-500` for WAITING_APPROVAL (needs action)
- `border-blue-500` for PAYMENT_WAITING (payment pending)
- Default (no border color) for STARTED

**5g.** Show premium info not just for COMPLETED but also for WAITING_APPROVAL and PAYMENT_WAITING and ACTIVE (lines 125-130). Change:
```typescript
{estimation.status === "COMPLETED" && formattedPremium && (
  <div className="mt-4 rounded-lg bg-green-50 dark:bg-green-950/20 p-4">
    <p className="text-sm font-medium text-green-700 dark:text-green-400">Calculated Premium</p>
    <p className="text-2xl font-bold text-green-800 dark:text-green-300">{formattedPremium}</p>
  </div>
)}
```
To:
```typescript
{["WAITING_APPROVAL", "PAYMENT_WAITING", "ACTIVE", "COMPLETED"].includes(estimation.status) && formattedPremium && (
  <div className="mt-4 rounded-lg bg-green-50 dark:bg-green-950/20 p-4">
    <p className="text-sm font-medium text-green-700 dark:text-green-400">Calculated Premium</p>
    <p className="text-2xl font-bold text-green-800 dark:text-green-300">{formattedPremium}</p>
  </div>
)}
```

### Step 6: Update the Dashboard Page

**File:** `frontend-next/src/app/(dashboard)/dashboard/page.tsx`

Find the card label "Pending Estimations" (around line 13) and change it to:
```
"Pending Offers"
```

### Step 7: Update the Customer Detail Page

**File:** `frontend-next/src/components/features/customers/customer-detail.tsx`

Find the placeholder card with the text "Estimation History" (around line 135-143) and change the card title to:
```
"Offer History"
```

### Step 8: No Change Needed — Verify These Files

These files do NOT need changes but should be verified:
- `frontend-next/src/app/(dashboard)/estimations/new/page.tsx` — "New Estimation" stays (describes the action)
- `frontend-next/src/components/features/estimations/estimation-form.tsx` — "New Estimation" heading stays
- `frontend-next/src/components/features/insurances/insurance-detail.tsx` — "new estimations" stays (describes the action)
- `frontend-next/src/app/layout.tsx` — meta description stays unchanged
- `frontend-next/src/lib/notification-types.ts` — `ESTIMATION_STATUS` is a wire protocol constant, not UI text
- `frontend-next/src/hooks/use-websocket.ts` — references to `/estimations/` URL paths stay unchanged

### Step 9: Verify — Run a Visual Check

After all changes, verify:
- [x] Sidebar shows "Offers" with Calculator icon, links to `/estimations`
- [x] List page shows title "Offers" with description "Manage insurance offers"
- [x] Empty state shows "No offers found"
- [x] "New Estimation" button is present and links to `/estimations/new`
- [x] Detail page shows "Premium #..." heading
- [x] Status badge supports all 6 statuses
- [x] Status filter dropdown includes all 6 statuses

---

## Acceptance Criteria

- [x] Sidebar nav item reads "Offers"
- [x] List page title reads "Offers", description "Manage insurance offers"
- [x] Empty state reads "No offers found"
- [x] "New Estimation" button text is unchanged (describes the action)
- [x] Detail page heading reads "Premium #..."
- [x] "Estimated Premium" changed to "Calculated Premium"
- [x] Status badge shows all new statuses with appropriate colors
- [x] Status filter dropdown includes all status values
- [x] Dashboard card reads "Pending Offers"
- [x] Customer detail card reads "Offer History"
- [x] Polling works for STARTED, WAITING_APPROVAL, and PAYMENT_WAITING statuses
- [x] No URL routes changed (still `/estimations`, `/estimations/new`, `/estimations/[id]`)
- [x] TypeScript compilation passes: `cd frontend-next && npx tsc --noEmit`
