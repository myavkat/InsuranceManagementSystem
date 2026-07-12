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
      ([factorName, factorValue]) => ({ factorName, factorValue }),
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
