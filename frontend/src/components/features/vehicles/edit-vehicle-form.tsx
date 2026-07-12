"use client";

import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { getVehicle } from "@/lib/api/vehicles";
import { VehicleForm } from "./vehicle-form";
import { ErrorAlert } from "@/components/features/error-alert";
import { Skeleton } from "@/components/ui/skeleton";
import { Card, CardContent } from "@/components/ui/card";

export function EditVehicleForm() {
  const params = useParams();
  const id = params.id as string;

  const { data: vehicle, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["vehicle", id],
    queryFn: () => getVehicle(id),
  });

  if (isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-8 w-64" />
        <Card>
          <CardContent className="space-y-4 pt-6">
            {Array.from({ length: 8 }).map((_, i) => (
              <Skeleton key={i} className="h-10 w-full" />
            ))}
          </CardContent>
        </Card>
      </div>
    );
  }

  if (isError || !vehicle) {
    return (
      <ErrorAlert
        message={error instanceof Error ? error.message : "Failed to load vehicle"}
        onRetry={() => refetch()}
      />
    );
  }

  return <VehicleForm initialData={vehicle} />;
}
