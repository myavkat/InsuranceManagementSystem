"use client";

import { useParams, useRouter } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { getInsurance, deleteInsurance, updateInsurance, deactivateInsurance } from "@/lib/api/insurances";
import { PageHeader } from "@/components/features/page-header";
import { ErrorAlert } from "@/components/features/error-alert";
import { ConfirmDialog } from "@/components/features/confirm-dialog";
import { StatusBadge } from "@/components/features/status-badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { ArrowLeft, Pencil, Trash2, Ban } from "lucide-react";
import { useState } from "react";

export function InsuranceDetail() {
  const params = useParams();
  const router = useRouter();
  const queryClient = useQueryClient();
  const id = params.id as string;
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deactivateOpen, setDeactivateOpen] = useState(false);

  const { data: insurance, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["insurance", id],
    queryFn: () => getInsurance(id),
  });

  const deleteMutation = useMutation({
    mutationFn: () => deleteInsurance(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["insurances"] });
      router.push("/insurances");
    },
  });

  const deactivateMutation = useMutation({
    mutationFn: () => deactivateInsurance(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["insurance", id] });
      queryClient.invalidateQueries({ queryKey: ["insurances"] });
      setDeactivateOpen(false);
      refetch();
    },
    onError: (error) => {
      console.error("Failed to deactivate insurance:", error);
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

  if (isError || !insurance) {
    return (
      <ErrorAlert
        message={error instanceof Error ? error.message : "Failed to load insurance product"}
        onRetry={() => refetch()}
      />
    );
  }

  const formattedPremium = insurance.basePremium != null
    ? new Intl.NumberFormat("tr-TR", { style: "currency", currency: "TRY" }).format(insurance.basePremium)
    : "—";

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" onClick={() => router.push("/insurances")}>
          <ArrowLeft className="size-4" />
        </Button>
        <PageHeader
          title={insurance.name}
          description="Insurance product details"
          action={
            <div className="flex gap-2">
              <Button variant="outline" onClick={() => router.push(`/insurances/${id}/edit`)}>
                <Pencil className="size-4" />
                Edit
              </Button>
              {insurance.isActive && (
                <Button variant="outline" onClick={() => setDeactivateOpen(true)}>
                  <Ban className="size-4" />
                  Deactivate
                </Button>
              )}
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
          <CardTitle>Product Information</CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div className="sm:col-span-2">
              <dt className="text-sm font-medium text-muted-foreground">Status</dt>
              <dd className="mt-0.5">
                <StatusBadge status={insurance.isActive ? "ACTIVE" : "INACTIVE"} />
              </dd>
            </div>
            <DetailItem label="Name" value={insurance.name} />
            <DetailItem label="Description" value={insurance.description ?? "—"} />
            <DetailItem label="Insurance Type" value={insurance.typeName ?? "—"} />
            <DetailItem label="Base Premium" value={formattedPremium} />
          </dl>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Record Info</CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <DetailItem label="Created" value={new Date(insurance.createdAt).toLocaleString()} />
            <DetailItem label="Updated" value={insurance.updatedAt ? new Date(insurance.updatedAt).toLocaleString() : "—"} />
          </dl>
        </CardContent>
      </Card>

      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="Delete Insurance Product"
        description={`Are you sure you want to delete "${insurance.name}"? This action cannot be undone.`}
        confirmLabel="Delete"
        onConfirm={() => deleteMutation.mutate()}
        loading={deleteMutation.isPending}
      />

      <ConfirmDialog
        open={deactivateOpen}
        onOpenChange={setDeactivateOpen}
        title="Deactivate Insurance Product"
        description={`Are you sure you want to deactivate "${insurance.name}"? Deactivated products cannot be used for new estimations.`}
        confirmLabel="Deactivate"
        onConfirm={() => deactivateMutation.mutate()}
        loading={deactivateMutation.isPending}
      />
    </div>
  );
}

function DetailItem({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div>
      <dt className="text-sm font-medium text-muted-foreground">{label}</dt>
      <dd className="mt-0.5 text-sm">{value}</dd>
    </div>
  );
}
