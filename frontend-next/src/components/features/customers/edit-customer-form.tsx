"use client";

import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { getCustomer } from "@/lib/api/customers";
import { CustomerForm } from "./customer-form";
import { ErrorAlert } from "@/components/features/error-alert";
import { Skeleton } from "@/components/ui/skeleton";
import { Card, CardContent } from "@/components/ui/card";

export function EditCustomerForm() {
  const params = useParams();
  const id = params.id as string;

  const { data: customer, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["customer", id],
    queryFn: () => getCustomer(id),
  });

  if (isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-8 w-64" />
        <Card>
          <CardContent className="space-y-4 pt-6">
            {Array.from({ length: 6 }).map((_, i) => (
              <Skeleton key={i} className="h-10 w-full" />
            ))}
          </CardContent>
        </Card>
      </div>
    );
  }

  if (isError || !customer) {
    return (
      <ErrorAlert
        message={error instanceof Error ? error.message : "Failed to load customer"}
        onRetry={() => refetch()}
      />
    );
  }

  return <CustomerForm initialData={customer} />;
}
