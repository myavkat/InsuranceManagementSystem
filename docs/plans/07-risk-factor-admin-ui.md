# Plan 07: Risk Factor Admin UI — Sliders on Insurance Detail Page

## Objective

Add a risk factor management section to the insurance detail page. Each risk factor is displayed as a slider (0.00–1.00), grouped by category (Vehicle factors, Real Estate factors, Customer factors). Changes are saved via the API and history is viewable.

## Dependencies

- [ ] Plan 06 (`06-risk-factor-api.md`) — API endpoints for GET/PUT risk factors and GET history must exist

## Files to Read First

- `frontend-next/src/components/features/insurances/insurance-detail.tsx` — detail page to enhance
- `frontend-next/src/lib/api/insurances.ts` — API client file (will add new functions)
- `frontend-next/src/components/ui/slider.tsx` — will be created (doesn't exist yet)
- `frontend-next/src/components/ui/card.tsx` — Card component used for sections
- `frontend-next/src/components/ui/button.tsx` — Button component
- `frontend-next/node_modules/@base-ui/react/slider/` — verify SliderRoot, SliderThumb, SliderTrack, SliderControl, SliderValue, SliderIndicator are available

## Technical Context

- **Frontend stack**: Next.js 16, React, TypeScript, Tailwind CSS, shadcn/ui (Base UI style)
- **Slider source**: `@base-ui/react` has full Slider primitives available — `Slider.Root`, `Slider.Thumb`, `Slider.Track`, `Slider.Control`, `Slider.Value`, `Slider.Indicator`, `Slider.Label`
- **Styling**: Use Tailwind classes for the slider track/thumb styling
- **State management**: Use React `useState` for local slider values before save, `useQuery` + `useMutation` from `@tanstack/react-query` for API interaction
- **Toast**: The project uses `sonner` for toast notifications (imported via providers)
- **API client pattern**: Functions follow the pattern in `insurances.ts` — use `apiClient<T>()` helper

## Steps

### Step 1: Create the Slider UI component

Create new file: `frontend-next/src/components/ui/slider.tsx`

This wraps `@base-ui/react` Slider primitives into a shadcn-style component:

```tsx
"use client";

import * as React from "react";
import { Slider } from "@base-ui/react/slider";
import { cn } from "@/lib/utils";

interface SliderProps {
  value: number;
  min?: number;
  max?: number;
  step?: number;
  onValueChange: (value: number) => void;
  disabled?: boolean;
  className?: string;
}

function BaseSlider({
  value,
  min = 0,
  max = 1,
  step = 0.01,
  onValueChange,
  disabled = false,
  className,
}: SliderProps) {
  return (
    <Slider.Root
      value={[value]}
      onValueChange={(newValue) => onValueChange(newValue[0])}
      min={min}
      max={max}
      step={step}
      disabled={disabled}
      className={cn(
        "relative flex w-full touch-none select-none items-center",
        className
      )}
    >
      <Slider.Control className="relative flex w-full items-center">
        <Slider.Track className="relative h-2 w-full grow rounded-full bg-secondary">
          <Slider.Indicator className="absolute h-full rounded-full bg-primary" />
        </Slider.Track>
        <Slider.Thumb className="block size-5 rounded-full border-2 border-primary bg-background ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50" />
      </Slider.Control>
    </Slider.Root>
  );
}

export { BaseSlider };
```

> Note: The component is named `BaseSlider` to avoid conflict with `@base-ui/react`'s `Slider` namespace.

### Step 2: Add API client functions for risk factors

Open `frontend-next/src/lib/api/insurances.ts`.

**Add** TypeScript types and API functions:

```typescript
// --- Risk Factor Types ---

export interface RiskFactorResponse {
  id: string;
  insuranceId: string;
  factorName: string;
  factorValue: number;
  createdAt: string;
  updatedAt: string;
}

export interface RiskFactorUpdateRequest {
  factorName: string;
  factorValue: number;
}

export interface RiskFactorHistoryResponse {
  id: string;
  riskFactorId: string;
  insuranceId: string;
  factorName: string;
  oldValue: number | null;
  newValue: number;
  changedAt: string;
}

// --- Risk Factor API Functions ---

export async function getRiskFactors(insuranceId: string): Promise<RiskFactorResponse[]> {
  return apiClient<RiskFactorResponse[]>(`/api/insurances/${insuranceId}/risk-factors`);
}

export async function updateRiskFactors(
  insuranceId: string,
  updates: RiskFactorUpdateRequest[]
): Promise<RiskFactorResponse[]> {
  return apiClient<RiskFactorResponse[]>(`/api/insurances/${insuranceId}/risk-factors`, {
    method: "PUT",
    body: JSON.stringify(updates),
  });
}

export async function getRiskFactorHistory(
  insuranceId: string,
  page = 0,
  size = 20
): Promise<PageResponse<RiskFactorHistoryResponse>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return apiClient<PageResponse<RiskFactorHistoryResponse>>(
    `/api/insurances/${insuranceId}/risk-factors/history?${params.toString()}`
  );
}
```

### Step 3: Create RiskFactorEditor component

Create new file: `frontend-next/src/components/features/insurances/risk-factor-editor.tsx`

This is a client component that:
1. Fetches risk factors via `useQuery`
2. Groups them by factor category (Vehicle, Real Estate, Customer/Shared)
3. Renders each factor as a labeled slider with current value display
4. Tracks local (unsaved) slider changes in a Map
5. Has a "Save Changes" button that calls `updateRiskFactors()` mutation
6. Shows toast on success/error via `sonner`

```tsx
"use client";

import { useState, useCallback } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  getRiskFactors,
  updateRiskFactors,
  type RiskFactorResponse,
  type RiskFactorUpdateRequest,
} from "@/lib/api/insurances";
import { BaseSlider } from "@/components/ui/slider";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Save } from "lucide-react";
import { toast } from "sonner";

// Factor name → display label mapping
const FACTOR_LABELS: Record<string, string> = {
  motorSize: "Motor Size",
  fuelType: "Fuel Type",
  carAge: "Car Age",
  brandRisk: "Brand Risk",
  buildingAge: "Building Age",
  constructionType: "Construction Type",
  luxuryClass: "Luxury Class",
  floorArea: "Floor Area",
  customerAge: "Customer Age",
  profession: "Profession",
  city: "City",
};

// Grouping
const VEHICLE_FACTORS = ["motorSize", "fuelType", "carAge", "brandRisk"];
const REAL_ESTATE_FACTORS = ["buildingAge", "constructionType", "luxuryClass", "floorArea"];
const SHARED_FACTORS = ["customerAge", "profession", "city"];

interface Props {
  insuranceId: string;
}

export function RiskFactorEditor({ insuranceId }: Props) {
  const queryClient = useQueryClient();
  const [localValues, setLocalValues] = useState<Record<string, number>>({});

  const { data: factors, isLoading } = useQuery({
    queryKey: ["risk-factors", insuranceId],
    queryFn: () => getRiskFactors(insuranceId),
  });

  const mutation = useMutation({
    mutationFn: (updates: RiskFactorUpdateRequest[]) =>
      updateRiskFactors(insuranceId, updates),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["risk-factors", insuranceId] });
      setLocalValues({});
      toast.success("Risk factors updated successfully");
    },
    onError: (error) => {
      toast.error(error instanceof Error ? error.message : "Failed to update risk factors");
    },
  });

  const handleSliderChange = useCallback((factorName: string, value: number) => {
    setLocalValues((prev) => ({ ...prev, [factorName]: value }));
  }, []);

  const hasChanges = Object.keys(localValues).length > 0;

  const handleSave = () => {
    const updates: RiskFactorUpdateRequest[] = Object.entries(localValues).map(
      ([factorName, factorValue]) => ({ factorName, factorValue })
    );
    mutation.mutate(updates);
  };

  if (isLoading) {
    return <Skeleton className="h-64 w-full" />;
  }

  if (!factors?.length) {
    return (
      <Card>
        <CardContent className="py-6 text-center text-sm text-muted-foreground">
          No risk factors configured for this insurance.
        </CardContent>
      </Card>
    );
  }

  const getDisplayValue = (factor: RiskFactorResponse): number => {
    if (factor.factorName in localValues) {
      return localValues[factor.factorName];
    }
    return factor.factorValue;
  };

  const renderFactorGroup = (title: string, factorNames: string[]) => {
    const groupFactors = factors.filter((f) => factorNames.includes(f.factorName));
    if (!groupFactors.length) return null;

    return (
      <div>
        <h4 className="text-sm font-medium text-muted-foreground mb-3">{title}</h4>
        <div className="space-y-4">
          {groupFactors.map((factor) => {
            const displayValue = getDisplayValue(factor);
            const isModified = factor.factorName in localValues;
            return (
              <div key={factor.id} className="space-y-1.5">
                <div className="flex items-center justify-between">
                  <label className="text-sm font-medium">
                    {FACTOR_LABELS[factor.factorName] ?? factor.factorName}
                  </label>
                  <span
                    className={`text-sm tabular-nums ${isModified ? "text-primary font-medium" : "text-muted-foreground"}`}
                  >
                    {displayValue.toFixed(2)}
                  </span>
                </div>
                <BaseSlider
                  value={displayValue}
                  onValueChange={(v) => handleSliderChange(factor.factorName, v)}
                />
              </div>
            );
          })}
        </div>
      </div>
    );
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center justify-between">
          Risk Factors
          <Button
            size="sm"
            onClick={handleSave}
            disabled={!hasChanges || mutation.isPending}
          >
            <Save className="size-4 mr-1" />
            {mutation.isPending ? "Saving..." : "Save Changes"}
          </Button>
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-6">
        {renderFactorGroup("Vehicle Factors", VEHICLE_FACTORS)}
        {renderFactorGroup("Real Estate Factors", REAL_ESTATE_FACTORS)}
        {renderFactorGroup("Customer Factors", SHARED_FACTORS)}
      </CardContent>
    </Card>
  );
}
```

### Step 4: Integrate RiskFactorEditor into InsuranceDetail

Open `frontend-next/src/components/features/insurances/insurance-detail.tsx`.

**Add** the import:
```typescript
import { RiskFactorEditor } from "./risk-factor-editor";
```

**Insert** the `RiskFactorEditor` component after the product information card (after the first `</Card>` around line 127):

```tsx
{/* Risk Factors — adjustable by admin */}
<RiskFactorEditor insuranceId={id} />
```

Place it between the "Product Information" card and the "Record Info" card.

### Step 5: Create RiskFactorHistory component (optional enhancement)

Create new file: `frontend-next/src/components/features/insurances/risk-factor-history.tsx`

This is a simple paginated table showing past changes:

```tsx
"use client";

import { useQuery } from "@tanstack/react-query";
import { getRiskFactorHistory } from "@/lib/api/insurances";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Skeleton } from "@/components/ui/skeleton";
import { History } from "lucide-react";

interface Props {
  insuranceId: string;
}

export function RiskFactorHistory({ insuranceId }: Props) {
  const { data, isLoading } = useQuery({
    queryKey: ["risk-factor-history", insuranceId],
    queryFn: () => getRiskFactorHistory(insuranceId),
  });

  if (isLoading) return <Skeleton className="h-32 w-full" />;

  const entries = data?.content ?? [];

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <History className="size-4" />
          Change History
        </CardTitle>
      </CardHeader>
      <CardContent>
        {entries.length === 0 ? (
          <p className="text-sm text-muted-foreground text-center py-4">
            No changes recorded yet.
          </p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Factor</TableHead>
                <TableHead>Old Value</TableHead>
                <TableHead>New Value</TableHead>
                <TableHead>Changed At</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {entries.map((entry) => (
                <TableRow key={entry.id}>
                  <TableCell className="font-medium">{entry.factorName}</TableCell>
                  <TableCell>{entry.oldValue?.toFixed(2) ?? "—"}</TableCell>
                  <TableCell>{entry.newValue.toFixed(2)}</TableCell>
                  <TableCell className="text-muted-foreground">
                    {new Date(entry.changedAt).toLocaleString()}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  );
}
```

Add this component to `insurance-detail.tsx` below the `RiskFactorEditor`.

### Step 6: Create `sonner` toast import if not already present

The `RiskFactorEditor` uses `toast.success()` and `toast.error()`. Verify that `sonner` is already set up in the providers. If not, install and configure.

## Acceptance Criteria

- [ ] Slider component (`BaseSlider`) exists in `components/ui/` and works with 0.00–1.00 range
- [ ] Insurance detail page shows a "Risk Factors" card with sliders grouped by category
- [ ] Sliders reflect current database values on load
- [ ] Moving a slider updates the displayed value in real-time (local state)
- [ ] Modified factors are visually highlighted (value text color changes)
- [ ] "Save Changes" button is enabled only when there are unsaved changes
- [ ] Saving calls `PUT /api/insurances/{id}/risk-factors` with modified factors only
- [ ] Toast notification appears on success/error
- [ ] History table below shows past changes with old/new values and timestamps
- [ ] Page does not crash if an insurance has no risk factors (empty state)
