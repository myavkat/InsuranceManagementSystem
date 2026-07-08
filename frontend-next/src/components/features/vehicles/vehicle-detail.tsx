"use client";

import { useParams, useRouter } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { getVehicle, deleteVehicle } from "@/lib/api/vehicles";
import { PageHeader } from "@/components/features/page-header";
import { ErrorAlert } from "@/components/features/error-alert";
import { ConfirmDialog } from "@/components/features/confirm-dialog";
import { Skeleton } from "@/components/ui/skeleton";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { ArrowLeft, Pencil, Trash2 } from "lucide-react";
import { useState } from "react";

export function VehicleDetail() {
  const params = useParams();
  const router = useRouter();
  const queryClient = useQueryClient();
  const id = params.id as string;
  const [deleteOpen, setDeleteOpen] = useState(false);

  const { data: vehicle, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["vehicle", id],
    queryFn: () => getVehicle(id),
  });

  const deleteMutation = useMutation({
    mutationFn: () => deleteVehicle(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["vehicles"] });
      router.push("/vehicles");
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

  if (isError || !vehicle) {
    return (
      <ErrorAlert
        message={error instanceof Error ? error.message : "Failed to load vehicle"}
        onRetry={() => refetch()}
      />
    );
  }

  const formattedPlate = vehicle.plate ?? "—";

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" onClick={() => router.push("/vehicles")}>
          <ArrowLeft className="size-4" />
        </Button>
        <PageHeader
          title={formattedPlate}
          description="Vehicle details"
          action={
            <div className="flex gap-2">
              <Button variant="outline" onClick={() => router.push(`/vehicles/${id}/edit`)}>
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
          <CardTitle>Vehicle Information</CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <DetailItem label="Plate" value={vehicle.plate ? <span className="font-mono">{vehicle.plate}</span> : "—"} />
            <DetailItem label="Chassis Number" value={vehicle.chassisNumber ? <span className="font-mono">{vehicle.chassisNumber}</span> : "—"} />
            <DetailItem label="License First Date" value={vehicle.licenseFirstDate ? new Date(vehicle.licenseFirstDate).toLocaleDateString() : "—"} />
            <DetailItem label="Customer" value={vehicle.customerName ?? "—"} />
            <DetailItem label="Brand" value={vehicle.carBrandName ?? "—"} />
            <DetailItem label="Model" value={vehicle.carModelName ?? "—"} />
            <DetailItem label="Engine" value={vehicle.carEngineName ?? "—"} />
            <DetailItem label="Fuel Type" value={vehicle.carFuelTypeName ?? "—"} />
            <DetailItem label="Type" value={vehicle.carTypeName ?? "—"} />
            <DetailItem label="Package" value={vehicle.carPackageName ?? "—"} />
          </dl>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Record Info</CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <DetailItem label="Created" value={new Date(vehicle.createdAt).toLocaleString()} />
            <DetailItem label="Updated" value={vehicle.updatedAt ? new Date(vehicle.updatedAt).toLocaleString() : "—"} />
          </dl>
        </CardContent>
      </Card>

      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="Delete Vehicle"
        description={`Are you sure you want to delete vehicle ${vehicle.plate ?? "with plate " + id}? This is a hard delete and cannot be undone.`}
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
