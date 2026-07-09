"use client";

import { useParams, useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { getEstimation } from "@/lib/api/estimations";
import { getCustomer } from "@/lib/api/customers";
import { getVehicle } from "@/lib/api/vehicles";
import { PageHeader } from "@/components/features/page-header";
import { ErrorAlert } from "@/components/features/error-alert";
import { StatusBadge } from "@/components/features/status-badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { ArrowLeft, RefreshCw } from "lucide-react";

const INSURANCE_TYPE_NAMES: Record<number, string> = {
  1: "Vehicle",
  2: "Real Estate",
  3: "Health",
  4: "Life",
};

export function EstimationDetail() {
  const params = useParams();
  const router = useRouter();
  const id = params.id as string;

  const { data: estimation, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["estimation", id],
    queryFn: () => getEstimation(id),
    refetchInterval: (query) => {
      const data = query.state.data;
      return data?.status === "STARTED" ? 5000 : false;
    },
  });

  // Client-side enrichment fallback: resolve customer name/nationalId
  const customerId = estimation?.customerId;
  const { data: customer } = useQuery({
    queryKey: ["customer", customerId],
    queryFn: () => getCustomer(customerId!),
    enabled: !!customerId && !estimation?.customerName,
    staleTime: 60_000,
  });

  // Client-side enrichment fallback: resolve vehicle plate/chassisNumber
  const vehicleId = estimation?.vehicleId;
  const { data: vehicle } = useQuery({
    queryKey: ["vehicle", vehicleId],
    queryFn: () => getVehicle(vehicleId!),
    enabled: !!vehicleId && !estimation?.vehiclePlate,
    staleTime: 60_000,
  });

  // Resolved display values: prefer backend enrichment, fall back to client-side
  const resolvedCustomerName = estimation?.customerName
    ?? (customer ? `${customer.firstName} ${customer.lastName}` : null);
  const resolvedCustomerNationalId = estimation?.customerNationalId
    ?? customer?.nationalId
    ?? null;
  const resolvedVehiclePlate = estimation?.vehiclePlate ?? vehicle?.plate ?? null;
  const resolvedVehicleChassisNumber = estimation?.vehicleChassisNumber ?? vehicle?.chassisNumber ?? null;
  const resolvedInsuranceTypeName = estimation?.insuranceTypeName
    ?? (estimation?.insuranceTypeId ? INSURANCE_TYPE_NAMES[estimation.insuranceTypeId] : null);

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

  if (isError || !estimation) {
    return (
      <ErrorAlert
        message={error instanceof Error ? error.message : "Failed to load estimation"}
        onRetry={() => refetch()}
      />
    );
  }

  const formattedPremium = estimation.premium != null
    ? new Intl.NumberFormat("tr-TR", { style: "currency", currency: "TRY" }).format(estimation.premium)
    : null;

  const isPolling = estimation.status === "STARTED";

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" onClick={() => router.push("/estimations")}>
          <ArrowLeft className="size-4" />
        </Button>
        <PageHeader
          title={`Estimation #${estimation.id.slice(0, 8)}`}
          description="Estimation details"
          action={
            <Button variant="outline" size="sm" onClick={() => refetch()} disabled={isLoading}>
              <RefreshCw className={"size-4" + (isLoading ? " animate-spin" : "")} />
              {isPolling ? "Auto-refreshing..." : "Refresh"}
            </Button>
          }
        />
      </div>

      {/* Status Banner */}
      <Card className={
        estimation.status === "COMPLETED" ? "border-green-500" :
        estimation.status === "REJECTED" ? "border-destructive" : ""
      }>
        <CardContent className="pt-6">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <span className="text-sm font-medium text-muted-foreground">Status:</span>
              <StatusBadge status={estimation.status} />
            </div>
            {isPolling && (
              <span className="text-xs text-muted-foreground animate-pulse">
                Waiting for result...
              </span>
            )}
          </div>

          {estimation.status === "COMPLETED" && formattedPremium && (
            <div className="mt-4 rounded-lg bg-green-50 dark:bg-green-950/20 p-4">
              <p className="text-sm font-medium text-green-700 dark:text-green-400">Estimated Premium</p>
              <p className="text-2xl font-bold text-green-800 dark:text-green-300">{formattedPremium}</p>
            </div>
          )}

          {estimation.status === "REJECTED" && estimation.details && (
            <div className="mt-4 rounded-lg bg-destructive/10 p-4">
              <p className="text-sm font-medium text-destructive">Error Details</p>
              <p className="mt-1 text-sm">{estimation.details}</p>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Customer Info */}
      <Card>
        <CardHeader>
          <CardTitle>Customer Information</CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <DetailItem label="Customer Name" value={resolvedCustomerName ?? "—"} />
            <DetailItem label="Customer ID" value={resolvedCustomerNationalId ?? estimation.customerId ?? "—"} />
          </dl>
        </CardContent>
      </Card>

      {/* Insurance Info */}
      <Card>
        <CardHeader>
          <CardTitle>Insurance Information</CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <DetailItem label="Insurance Type" value={resolvedInsuranceTypeName ?? "—"} />
            <DetailItem label="Base Premium" value={formattedPremium ?? "Pending calculation..."} />
          </dl>
        </CardContent>
      </Card>

      {/* Linked Assets */}
      {(estimation.vehicleId || estimation.realEstateId) && (
        <Card>
          <CardHeader>
            <CardTitle>Linked Assets</CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              {estimation.vehicleId && (
                <DetailItem label="Vehicle" value={
                  resolvedVehiclePlate
                    ? resolvedVehicleChassisNumber
                      ? `${resolvedVehiclePlate} / Chassis: ${resolvedVehicleChassisNumber}`
                      : resolvedVehiclePlate
                    : estimation.vehicleId
                } />
              )}
              {estimation.realEstateId && (
                <DetailItem label="Real Estate" value={estimation.realEstateAddress ?? estimation.realEstateId} />
              )}
            </dl>
          </CardContent>
        </Card>
      )}

      {/* Timeline */}
      <Card>
        <CardHeader>
          <CardTitle>Timeline</CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <DetailItem label="Created" value={new Date(estimation.createdAt).toLocaleString()} />
            <DetailItem label="Updated" value={estimation.updatedAt ? new Date(estimation.updatedAt).toLocaleString() : "—"} />
          </dl>
        </CardContent>
      </Card>
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
