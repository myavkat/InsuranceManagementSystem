"use client";

import { useParams, useRouter } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { getCustomer, deleteCustomer } from "@/lib/api/customers";
import { PageHeader } from "@/components/features/page-header";
import { ErrorAlert } from "@/components/features/error-alert";
import { ConfirmDialog } from "@/components/features/confirm-dialog";
import { Skeleton } from "@/components/ui/skeleton";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { ArrowLeft, Pencil, Trash2 } from "lucide-react";
import { useState } from "react";

export function CustomerDetail() {
  const params = useParams();
  const router = useRouter();
  const queryClient = useQueryClient();
  const id = params.id as string;
  const [deleteOpen, setDeleteOpen] = useState(false);

  const { data: customer, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["customer", id],
    queryFn: () => getCustomer(id),
  });

  const deleteMutation = useMutation({
    mutationFn: () => deleteCustomer(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["customers"] });
      router.push("/customers");
    },
  });

  if (isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-8 w-64" />
        <Card>
          <CardContent className="space-y-4 pt-6">
            {Array.from({ length: 8 }).map((_, i) => (
              <Skeleton key={i} className="h-5 w-full" />
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

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" onClick={() => router.push("/customers")}>
          <ArrowLeft className="size-4" />
        </Button>
        <PageHeader
          title={`${customer.firstName} ${customer.lastName}`}
          description="Customer details"
          action={
            <div className="flex gap-2">
              <Button variant="outline" onClick={() => router.push(`/customers/${id}/edit`)}>
                <Pencil className="size-4" />
                Edit
              </Button>
              <Button variant="destructive" onClick={() => setDeleteOpen(true)}>
                <Trash2 className="size-4" />
                Delete
              </Button>
            </div>
          }
        />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Personal Information</CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <DetailItem label="First Name" value={customer.firstName} />
            <DetailItem label="Last Name" value={customer.lastName} />
            <DetailItem label="National ID (TCKN)" value={customer.nationalId} />
            <DetailItem label="Email" value={customer.email} />
            <DetailItem label="Phone" value={customer.phone ?? "—"} />
            <DetailItem label="Birth Date" value={customer.birthDate ? new Date(customer.birthDate).toLocaleDateString() : "—"} />
            <DetailItem label="City" value={customer.cityName ?? "—"} />
            <DetailItem label="Profession" value={customer.professionName ?? "—"} />
            <DetailItem label="Address" value={customer.address ?? "—"} className="sm:col-span-2" />
          </dl>
        </CardContent>
      </Card>

      {/* Linked Vehicles — placeholder for now */}
      <Card>
        <CardHeader>
          <CardTitle>Linked Vehicles</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">
            Vehicle linkage will be available when vehicle pages are built.
          </p>
        </CardContent>
      </Card>

      {/* Estimation History — placeholder for now */}
      <Card>
        <CardHeader>
          <CardTitle>Estimation History</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">
            Estimation history will be available when estimation pages are built.
          </p>
        </CardContent>
      </Card>

      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="Delete Customer"
        description={`Are you sure you want to delete ${customer.firstName} ${customer.lastName}? This is a soft delete — the record will be hidden but historical data is preserved.`}
        confirmLabel="Delete"
        onConfirm={() => deleteMutation.mutate()}
        loading={deleteMutation.isPending}
      />
    </div>
  );
}

function DetailItem({ label, value, className }: { label: string; value: string; className?: string }) {
  return (
    <div className={className}>
      <dt className="text-sm font-medium text-muted-foreground">{label}</dt>
      <dd className="mt-0.5 text-sm">{value}</dd>
    </div>
  );
}
