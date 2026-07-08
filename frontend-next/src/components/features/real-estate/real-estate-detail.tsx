"use client";

import { useParams, useRouter } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { getRealEstate, deleteRealEstate } from "@/lib/api/realestate";
import { PageHeader } from "@/components/features/page-header";
import { ErrorAlert } from "@/components/features/error-alert";
import { ConfirmDialog } from "@/components/features/confirm-dialog";
import { Skeleton } from "@/components/ui/skeleton";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { ArrowLeft, Pencil, Trash2 } from "lucide-react";
import { useState } from "react";

export function RealEstateDetail() {
  const params = useParams();
  const router = useRouter();
  const queryClient = useQueryClient();
  const id = params.id as string;
  const [deleteOpen, setDeleteOpen] = useState(false);

  const { data: property, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["real-estate", id],
    queryFn: () => getRealEstate(id),
  });

  const deleteMutation = useMutation({
    mutationFn: () => deleteRealEstate(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["real-estate"] });
      router.push("/real-estate");
    },
  });

  if (isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-8 w-64" />
        <Card>
          <CardContent className="space-y-4 pt-6">
            {Array.from({ length: 10 }).map((_, i) => (
              <Skeleton key={i} className="h-5 w-full" />
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

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" onClick={() => router.push("/real-estate")}>
          <ArrowLeft className="size-4" />
        </Button>
        <PageHeader
          title={property.address}
          description="Property details"
          action={
            <div className="flex gap-2">
              <Button variant="outline" onClick={() => router.push(`/real-estate/${id}/edit`)}>
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
          <CardTitle>Property Information</CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <DetailItem label="Address" value={property.address} className="sm:col-span-2" />
            <DetailItem label="City" value={property.cityName ?? "—"} />
            <DetailItem label="District" value={property.district ?? "—"} />
            <DetailItem label="Square Meters" value={property.squareMeters ? `${property.squareMeters} m²` : "—"} />
            <DetailItem label="Construction Year" value={property.constructionYear?.toString() ?? "—"} />
            <DetailItem label="Construction Type" value={property.constructionTypeName ?? "—"} />
            <DetailItem label="Luxury Class" value={property.luxuryClassName ?? "—"} />
            <DetailItem label="Usage Type" value={property.usageTypeName ?? "—"} />
            <DetailItem label="Customer" value={property.customerName ?? "—"} />
          </dl>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Record Info</CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <DetailItem label="Created" value={new Date(property.createdAt).toLocaleString()} />
            <DetailItem label="Updated" value={property.updatedAt ? new Date(property.updatedAt).toLocaleString() : "—"} />
          </dl>
        </CardContent>
      </Card>

      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="Delete Property"
        description={`Are you sure you want to delete the property at "${property.address}"? This is a hard delete and cannot be undone.`}
        confirmLabel="Delete"
        onConfirm={() => deleteMutation.mutate()}
        loading={deleteMutation.isPending}
      />
    </div>
  );
}

function DetailItem({ label, value, className }: { label: string; value: React.ReactNode; className?: string }) {
  return (
    <div className={className}>
      <dt className="text-sm font-medium text-muted-foreground">{label}</dt>
      <dd className="mt-0.5 text-sm">{value}</dd>
    </div>
  );
}
