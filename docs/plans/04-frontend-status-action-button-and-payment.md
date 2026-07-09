# Plan 04: Frontend — Status-Driven Action Button and Payment Flow

**Status:** Completed
**Depends on:** Plan 02 (backend endpoints `accept-offer` and `process-payment` must exist), Plan 03 (status badge and types updated)
**Blocks:** Nothing

---

## Objective

1. Create a status-driven action button component that reads the current estimation status and renders the correct label and action — one component, not hardcoded per-screen.
2. Integrate the action button into the estimation detail page.
3. Create a dummy payment page at `/estimations/[id]/payment` that accepts card input, validates basic format, and on submit calls the `process-payment` endpoint.
4. Wire the full flow: Accept Offer → Start Payment → Payment Page → Success → Redirect back to detail.

---

## Files to Read First (before writing any code)

| # | File Path | Purpose |
|---|-----------|---------|
| 1 | `frontend-next/src/components/features/estimations/estimation-detail.tsx` | Where the action button will be integrated |
| 2 | `frontend-next/src/lib/api/estimations.ts` | API client — you will add new API functions here |
| 3 | `frontend-next/src/components/ui/button.tsx` | shadcn/ui Button component API |
| 4 | `frontend-next/src/components/ui/input.tsx` | shadcn/ui Input component API |
| 5 | `frontend-next/src/components/ui/card.tsx` | shadcn/ui Card component API |
| 6 | `frontend-next/src/components/features/status-badge.tsx` | Status badge for the payment page |
| 7 | `frontend-next/src/components/features/error-alert.tsx` | Error alert component for mutation errors |
| 8 | `frontend-next/src/lib/utils.ts` | `cn()` utility |
| 9 | `docs/outlines/05_NEXTJS_FRONTEND.md` | Component patterns, client boundaries |
| 10 | `AGENTS.md` | General rules (not SAGA-specific for frontend work) |

---

## Key Conventions

### React Hooks and Patterns
- Client Components must have `"use client"` directive at the top of the file.
- Use `useMutation` from `@tanstack/react-query` for API calls that change state (accept offer, process payment).
- Use `useRouter` from `next/navigation` for client-side navigation.
- Invalidate or update the estimation query cache after mutations so the detail page reflects the new status without a full page reload.

### Form Validation
- Use Zod schemas for form validation (consistent with the existing `estimation-form.tsx`).
- Use `react-hook-form` with `zodResolver` for form state management.

### API Client
- Use the existing `apiClient` from `@/lib/api/client` for all API calls. Import it: `import { apiClient } from "@/lib/api/client";`
- The estimation-specific functions go in `@/lib/api/estimations.ts`.
- All API calls return the typed response; errors are thrown as `ApiError`.

### Styling
- Use Tailwind CSS utility classes only — no inline styles, no CSS modules.
- Use shadcn/ui components for all UI primitives (Button, Input, Card, CardContent, CardHeader, CardTitle).
- Use `cn()` from `@/lib/utils` for conditional class merging.

---

## Steps

### Step 1: Add API Client Methods

**File:** `frontend-next/src/lib/api/estimations.ts`

Add two new API functions after the `createEstimation` function (at the end of the file, before the last line if any):

```typescript
export async function acceptOffer(id: string): Promise<EstimationResponse> {
  return apiClient<EstimationResponse>(`/api/estimations/${id}/accept-offer`, {
    method: "PUT",
  });
}

export async function processPayment(id: string): Promise<EstimationResponse> {
  return apiClient<EstimationResponse>(`/api/estimations/${id}/process-payment`, {
    method: "PUT",
  });
}
```

### Step 2: Create the Payment Page Schema

Create a new file: **`frontend-next/src/lib/schemas/payment.ts`**

```typescript
import { z } from "zod";

export const paymentSchema = z.object({
  cardNumber: z
    .string()
    .min(1, "Card number is required")
    .regex(/^\d{16}$/, "Card number must be exactly 16 digits"),
  cardHolder: z
    .string()
    .min(1, "Card holder name is required"),
  expiryMonth: z
    .string()
    .min(1, "Month is required")
    .regex(/^(0[1-9]|1[0-2])$/, "Must be a valid month (01-12)"),
  expiryYear: z
    .string()
    .min(1, "Year is required")
    .regex(/^\d{4}$/, "Must be a valid 4-digit year"),
  cvv: z
    .string()
    .min(1, "CVV is required")
    .regex(/^\d{3}$/, "CVV must be exactly 3 digits"),
});

export type PaymentFormData = z.infer<typeof paymentSchema>;
```

Validation rules:
- Card number: exactly 16 digits
- Card holder: non-empty string
- Expiry month: 01-12
- Expiry year: 4 digits
- CVV: exactly 3 digits

All validations are format-only — the actual payment is always successful, so we never call a real payment gateway.

### Step 3: Create the Status-Driven Action Button Component

Create a new file: **`frontend-next/src/components/features/estimations/offer-action-button.tsx`**

This component reads the current `status` and renders the correct button label and action:

```typescript
"use client";

import { useRouter } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { acceptOffer } from "@/lib/api/estimations";
import type { EstimationStatus } from "@/lib/api/estimations";
import { Button } from "@/components/ui/button";
import { CheckCircle, CreditCard } from "lucide-react";

interface OfferActionButtonProps {
  estimationId: string;
  status: EstimationStatus;
}

export function OfferActionButton({ estimationId, status }: OfferActionButtonProps) {
  const router = useRouter();
  const queryClient = useQueryClient();

  const acceptMutation = useMutation({
    mutationFn: () => acceptOffer(estimationId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["estimation", estimationId] });
    },
  });

  // Determine button state from status
  switch (status) {
    case "WAITING_APPROVAL":
      // Show "Accept Offer" button
      return (
        <Button
          onClick={() => acceptMutation.mutate()}
          disabled={acceptMutation.isPending}
          variant="default"
        >
          <CheckCircle className="size-4" />
          {acceptMutation.isPending ? "Accepting..." : "Accept Offer"}
        </Button>
      );

    case "PAYMENT_WAITING":
      // Show "Start Payment" button — navigates to payment page
      return (
        <Button
          onClick={() => router.push(`/estimations/${estimationId}/payment`)}
          variant="default"
        >
          <CreditCard className="size-4" />
          Start Payment
        </Button>
      );

    case "ACTIVE":
    case "COMPLETED":
      // Terminal states — show disabled "Already Active/Completed" button
      return (
        <Button disabled variant="outline">
          <CheckCircle className="size-4" />
          {status === "ACTIVE" ? "Policy Active" : "Completed"}
        </Button>
      );

    case "REJECTED":
      // Rejected — show disabled "Rejected" indicator
      return (
        <Button disabled variant="outline" className="text-destructive">
          Offer Rejected
        </Button>
      );

    case "STARTED":
    default:
      // Still processing — show disabled "Processing" indicator
      return (
        <Button disabled variant="outline">
          Processing...
        </Button>
      );
  }
}
```

**Behavior specification:**
- `WAITING_APPROVAL`: Shows "Accept Offer" button (enabled). On click, calls `PUT /api/estimations/{id}/accept-offer`. On success, invalidates the estimation query cache so the detail page re-fetches and shows the new status.
- `PAYMENT_WAITING`: Shows "Start Payment" button (enabled). On click, navigates to `/estimations/{id}/payment`.
- `ACTIVE`: Shows "Policy Active" button (disabled, outline variant).
- `COMPLETED`: Shows "Completed" button (disabled, outline variant). Only appears for legacy data.
- `REJECTED`: Shows "Offer Rejected" button (disabled, outline variant with destructive text).
- `STARTED`: Shows "Processing..." button (disabled, outline variant). Estimation is still being calculated.

### Step 4: Create the Dummy Payment Page

Create a new directory and file: **`frontend-next/src/app/(dashboard)/estimations/[id]/payment/page.tsx`**

```typescript
import type { Metadata } from "next";
import { PaymentForm } from "@/components/features/estimations/payment-form";

export const metadata: Metadata = {
  title: "Payment",
};

export default function PaymentPage() {
  return <PaymentForm />;
}
```

### Step 5: Create the Payment Form Component

Create a new file: **`frontend-next/src/components/features/estimations/payment-form.tsx`**

```typescript
"use client";

import { useParams, useRouter } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { processPayment } from "@/lib/api/estimations";
import { paymentSchema, type PaymentFormData } from "@/lib/schemas/payment";
import { PageHeader } from "@/components/features/page-header";
import { ErrorAlert } from "@/components/features/error-alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { ArrowLeft, CreditCard, Lock } from "lucide-react";

export function PaymentForm() {
  const params = useParams();
  const router = useRouter();
  const queryClient = useQueryClient();
  const estimationId = params.id as string;

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<PaymentFormData>({
    resolver: zodResolver(paymentSchema),
    defaultValues: {
      cardNumber: "",
      cardHolder: "",
      expiryMonth: "",
      expiryYear: "",
      cvv: "",
    },
  });

  const paymentMutation = useMutation({
    mutationFn: (data: PaymentFormData) => {
      // The payment is always successful — we don't actually send the card data.
      // We just call the backend to transition status to ACTIVE.
      return processPayment(estimationId);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["estimation", estimationId] });
      router.push(`/estimations/${estimationId}`);
    },
  });

  const onSubmit = (data: PaymentFormData) => {
    paymentMutation.mutate(data);
  };

  return (
    <div className="space-y-6 max-w-lg mx-auto">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" onClick={() => router.back()}>
          <ArrowLeft className="size-4" />
        </Button>
        <PageHeader
          title="Payment"
          description="Enter payment details to activate the policy"
        />
      </div>

      {paymentMutation.isError && (
        <ErrorAlert
          message={
            paymentMutation.error instanceof Error
              ? paymentMutation.error.message
              : "Payment failed"
          }
        />
      )}

      <form onSubmit={handleSubmit(onSubmit)}>
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <CreditCard className="size-5" />
              Card Details
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {/* Note: This is a dummy payment page — all payments succeed. */}

            {/* Card Number */}
            <div className="space-y-1.5">
              <label className="text-sm font-medium">Card Number</label>
              <Input
                {...register("cardNumber")}
                placeholder="1234567890123456"
                maxLength={16}
                inputMode="numeric"
              />
              {errors.cardNumber?.message && (
                <p className="text-sm text-destructive" role="alert">
                  {errors.cardNumber.message}
                </p>
              )}
            </div>

            {/* Card Holder */}
            <div className="space-y-1.5">
              <label className="text-sm font-medium">Card Holder</label>
              <Input
                {...register("cardHolder")}
                placeholder="AD SOYAD"
              />
              {errors.cardHolder?.message && (
                <p className="text-sm text-destructive" role="alert">
                  {errors.cardHolder.message}
                </p>
              )}
            </div>

            {/* Expiry Row */}
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <label className="text-sm font-medium">Expiry Month</label>
                <Input
                  {...register("expiryMonth")}
                  placeholder="MM"
                  maxLength={2}
                  inputMode="numeric"
                />
                {errors.expiryMonth?.message && (
                  <p className="text-sm text-destructive" role="alert">
                    {errors.expiryMonth.message}
                  </p>
                )}
              </div>
              <div className="space-y-1.5">
                <label className="text-sm font-medium">Expiry Year</label>
                <Input
                  {...register("expiryYear")}
                  placeholder="YYYY"
                  maxLength={4}
                  inputMode="numeric"
                />
                {errors.expiryYear?.message && (
                  <p className="text-sm text-destructive" role="alert">
                    {errors.expiryYear.message}
                  </p>
                )}
              </div>
            </div>

            {/* CVV */}
            <div className="space-y-1.5">
              <label className="text-sm font-medium">CVV</label>
              <Input
                {...register("cvv")}
                placeholder="123"
                maxLength={3}
                inputMode="numeric"
                type="password"
              />
              {errors.cvv?.message && (
                <p className="text-sm text-destructive" role="alert">
                  {errors.cvv.message}
                </p>
              )}
            </div>

            {/* Security Note */}
            <div className="flex items-center gap-2 text-xs text-muted-foreground pt-2">
              <Lock className="size-3" />
              This is a dummy payment page — all payments are accepted. No real payment is processed.
            </div>

            {/* Submit Button */}
            <Button
              type="submit"
              className="w-full"
              disabled={isSubmitting || paymentMutation.isPending}
            >
              <CreditCard className="size-4" />
              {isSubmitting || paymentMutation.isPending
                ? "Processing..."
                : "Pay & Activate Policy"}
            </Button>
          </CardContent>
        </Card>
      </form>
    </div>
  );
}
```

### Step 6: Integrate the Action Button into the Detail Page

**File:** `frontend-next/src/components/features/estimations/estimation-detail.tsx`

#### 6a: Import the new component

Add this import near the other component imports (after line 10):
```typescript
import { OfferActionButton } from "@/components/features/estimations/offer-action-button";
```

#### 6b: Add the action button to the Status Banner

In the status banner card (the Card around line 108), after the "Waiting for result..." polling indicator (line 119-122), add the action button. The action button should appear in place of (or after) the polling indicator.

Replace the polling indicator block:
```typescript
{isPolling && (
  <span className="text-xs text-muted-foreground animate-pulse">
    Waiting for result...
  </span>
)}
```

With:
```typescript
<div className="flex items-center gap-3">
  {isPolling && (
    <span className="text-xs text-muted-foreground animate-pulse">
      Waiting for result...
    </span>
  )}
  <OfferActionButton estimationId={estimation.id} status={estimation.status} />
</div>
```

This places the action button alongside the polling indicator. When polling stops (status is terminal), only the action button remains visible.

#### 6c: Show the offer action button below the status banner

After the closing `</Card>` of the status banner (around line 139), add a standalone action card that's only visible for actionable statuses:

Add after line 139 (after the `</Card>` of the status banner):

```typescript
{/* Action Card — only visible for actionable statuses */}
{["WAITING_APPROVAL", "PAYMENT_WAITING"].includes(estimation.status) && (
  <Card>
    <CardContent className="pt-6">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm font-medium">
            {estimation.status === "WAITING_APPROVAL"
              ? "This offer is waiting for approval"
              : "Payment is required to activate this policy"}
          </p>
          <p className="text-xs text-muted-foreground mt-1">
            {estimation.status === "WAITING_APPROVAL"
              ? "Accept the offer to proceed with payment."
              : "Click below to pay and activate the policy."}
          </p>
        </div>
        <OfferActionButton estimationId={estimation.id} status={estimation.status} />
      </div>
    </CardContent>
  </Card>
)}
```

#### 6d: Show timeline dates for ACTIVE policies

In the Timeline card (around line 194-204), add `startDate` and `endDate` fields when the status is ACTIVE. Add these DetailItems after the existing Created/Updated items:

```typescript
{estimation.startDate && (
  <DetailItem label="Start Date" value={new Date(estimation.startDate).toLocaleString()} />
)}
{estimation.endDate && (
  <DetailItem label="End Date" value={new Date(estimation.endDate).toLocaleString()} />
)}
```

But first, you need to update the `EstimationResponse` TypeScript interface to include these new fields.

### Step 7: Update TypeScript EstimationResponse Interface

**File:** `frontend-next/src/lib/api/estimations.ts`

Add two new optional fields to the `EstimationResponse` interface (after `details` and before `createdAt`):

```typescript
startDate?: string;
endDate?: string;
```

Add them around line 23, after the `details` field.

### Step 8: Verify TypeScript Compilation

Run TypeScript type checking to catch any errors:

```bash
cd frontend-next && npx tsc --noEmit
```

Fix any type errors before considering this plan complete.

### Step 9: Manual Verification Checklist

After implementation, verify the full flow:

- [x] Create a new estimation → starts in STARTED status, "Processing..." button shown (disabled)
- [x] (Simulate) estimation reaches WAITING_APPROVAL → "Accept Offer" button shown (enabled)
- [x] Click "Accept Offer" → estimation transitions to PAYMENT_WAITING, "Start Payment" button shown
- [x] Click "Start Payment" → navigates to `/estimations/{id}/payment`
- [x] Payment page validates: empty fields show errors, 15-digit card number shows error
- [x] Submit valid payment → shows "Processing..." on button, calls process-payment endpoint
- [x] On success → redirected to detail page, status shows ACTIVE, "Policy Active" button shown (disabled)
- [x] Detail page shows start_date and end_date for ACTIVE policies
- [x] For REJECTED estimations → "Offer Rejected" button shown (disabled)
- [x] For COMPLETED (legacy) estimations → "Completed" button shown (disabled)

---

## Acceptance Criteria

- [x] `offer-action-button.tsx` component created with status-driven button logic (all 6 statuses handled)
- [x] `payment-form.tsx` component created with Zod validation (5 fields)
- [x] `payment.ts` schema created with proper validation rules
- [x] `payment/page.tsx` route created at `/estimations/[id]/payment`
- [x] API client has `acceptOffer()` and `processPayment()` functions
- [x] Detail page integrates `OfferActionButton` in both the status banner and a standalone action card
- [x] Detail page shows `startDate` and `endDate` for ACTIVE policies
- [x] `EstimationResponse` TypeScript interface includes `startDate?` and `endDate?`
- [x] `"use client"` directive present on all client components
- [x] TypeScript compilation passes: `cd frontend-next && npx tsc --noEmit`
- [x] Accept Offer button only shown/active for `WAITING_APPROVAL`
- [x] Start Payment button only shown/active for `PAYMENT_WAITING`
- [x] All other statuses show disabled/informational buttons
- [x] Payment page validates all required fields with format rules
- [x] Payment submit always succeeds (dummy payment)
- [x] On payment success, redirected back to detail page with refreshed data
