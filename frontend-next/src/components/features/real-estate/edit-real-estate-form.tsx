"use client";

import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { getRealEstate } from "@/lib/api/realestate";
import { RealEstateForm } from "./real-estate-form";
import { ErrorAlert } from "@/components/features/error-alert";
import { Skeleton } from "@/components/ui/skeleton";
import { Card, CardContent } from "@/components/ui/card";

export function EditRealEstateForm() {
  const params = useParams();
  const id = params.id as string;

  const { data: property, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["real-estate", id],
    queryFn: () => getRealEstate(id),
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

  if (isError || !property) {
    return (
      <ErrorAlert
        message={error instanceof Error ? error.message : "Failed to load property"}
        onRetry={() => refetch()}
      />
    );
  }

  return <RealEstateForm initialData={property} />;
}
