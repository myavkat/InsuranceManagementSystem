"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { getVehicles } from "@/lib/api/vehicles";
import { Button } from "@/components/ui/button";
import { SearchBar } from "@/components/features/search-bar";
import { PaginationBar } from "@/components/features/pagination-bar";
import { DataTableSkeleton } from "@/components/features/data-table-skeleton";
import { EmptyState } from "@/components/features/empty-state";
import { ErrorAlert } from "@/components/features/error-alert";
import { PageHeader } from "@/components/features/page-header";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table";
import { Car, Plus } from "lucide-react";

export function VehicleList() {
  const router = useRouter();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState("");
  const pageSize = 20;

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["vehicles", page, search],
    queryFn: () => getVehicles(page, pageSize, search || undefined),
  });

  if (isLoading) return <DataTableSkeleton columns={6} />;
  if (isError) {
    return (
      <ErrorAlert
        message={error instanceof Error ? error.message : "Failed to load vehicles"}
        onRetry={() => refetch()}
      />
    );
  }

  const vehicles = data?.content ?? [];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Vehicles"
        description="Manage vehicle records"
        action={
          <Button onClick={() => router.push("/vehicles/new")}>
            <Plus className="size-4" />
            New Vehicle
          </Button>
        }
      />

      <SearchBar
        placeholder="Search by plate or brand..."
        onSearch={(value) => {
          setSearch(value);
          setPage(0);
        }}
      />

      {vehicles.length === 0 ? (
        <EmptyState
          icon={Car}
          title="No vehicles found"
          description={search ? "Try adjusting your search." : "Get started by creating a new vehicle."}
          action={
            !search && (
              <Button onClick={() => router.push("/vehicles/new")}>
                <Plus className="size-4" />
                New Vehicle
              </Button>
            )
          }
        />
      ) : (
        <>
          <div className="rounded-md border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Plate</TableHead>
                  <TableHead>Brand / Model</TableHead>
                  <TableHead>Customer</TableHead>
                  <TableHead>License Date</TableHead>
                  <TableHead>Chassis Number</TableHead>
                  <TableHead>Created</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {vehicles.map((vehicle) => (
                  <TableRow
                    key={vehicle.id}
                    className="cursor-pointer hover:bg-muted/50"
                    onClick={() => router.push(`/vehicles/${vehicle.id}`)}
                  >
                    <TableCell className="font-medium">{vehicle.plate ?? "—"}</TableCell>
                    <TableCell>
                      {vehicle.carBrandName && vehicle.carModelName
                        ? `${vehicle.carBrandName} / ${vehicle.carModelName}`
                        : vehicle.carBrandName ?? vehicle.carModelName ?? "—"}
                    </TableCell>
                    <TableCell>{vehicle.customerName ?? "—"}</TableCell>
                    <TableCell>
                      {vehicle.licenseFirstDate
                        ? new Date(vehicle.licenseFirstDate).toLocaleDateString()
                        : "—"}
                    </TableCell>
                    <TableCell className="font-mono text-xs">{vehicle.chassisNumber ?? "—"}</TableCell>
                    <TableCell>
                      {new Date(vehicle.createdAt).toLocaleDateString()}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <PaginationBar
            currentPage={page}
            totalPages={data?.totalPages ?? 1}
            totalElements={data?.totalElements ?? 0}
            pageSize={pageSize}
            onPageChange={setPage}
          />
        </>
      )}
    </div>
  );
}
