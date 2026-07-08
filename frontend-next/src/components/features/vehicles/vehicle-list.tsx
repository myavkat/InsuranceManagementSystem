"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { createColumnHelper, type ColumnDef } from "@tanstack/react-table";
import { getVehicles, type VehicleResponse } from "@/lib/api/vehicles";
import { Button } from "@/components/ui/button";
import { SearchBar } from "@/components/features/search-bar";
import { EmptyState } from "@/components/features/empty-state";
import { ErrorAlert } from "@/components/features/error-alert";
import { PageHeader } from "@/components/features/page-header";
import {
  DataTable,
  type DataTablePaginationState,
  type DataTableSortingState,
} from "@/components/features/data-table/data-table";
import { formatDate } from "@/components/features/data-table/column-helpers";
import { Car, Plus } from "lucide-react";
import type { PageResponse } from "@/lib/api/types";

const columnHelper = createColumnHelper<VehicleResponse>();

const columns: ColumnDef<VehicleResponse, any>[] = [
  columnHelper.accessor("plate", {
    header: "Plate",
    cell: (info) => info.getValue() ?? "—",
    enableSorting: true,
  }),
  columnHelper.accessor("carBrandName", {
    header: "Brand / Model",
    cell: (info) => {
      const brand = info.getValue();
      const model = info.row.original.carModelName;
      return brand && model
        ? `${brand} / ${model}`
        : brand ?? model ?? "—";
    },
    enableSorting: true,
  }),
  columnHelper.accessor("customerName", {
    header: "Customer",
    cell: (info) => info.getValue() ?? "—",
  }),
  columnHelper.accessor("licenseFirstDate", {
    header: "License Date",
    cell: (info) => (info.getValue() ? formatDate(info.getValue()!) : "—"),
  }),
  columnHelper.accessor("chassisNumber", {
    header: "Chassis Number",
    cell: (info) => (
      <span className="font-mono text-xs">{info.getValue() ?? "—"}</span>
    ),
  }),
  columnHelper.accessor("createdAt", {
    header: "Created",
    cell: (info) => formatDate(info.getValue()),
    enableSorting: true,
  }),
];

interface VehicleListProps {
  initialData?: PageResponse<VehicleResponse>;
}

export function VehicleList({ initialData }: VehicleListProps) {
  const router = useRouter();
  const [pagination, setPagination] = useState<DataTablePaginationState>({
    pageIndex: 0,
    pageSize: 20,
  });
  const [sorting, setSorting] = useState<DataTableSortingState[]>([]);
  const [search, setSearch] = useState("");

  const sortField = sorting[0]?.id;
  const sortDirection = sorting[0]?.desc ? "desc" : "asc";

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["vehicles", pagination.pageIndex, pagination.pageSize, search, sortField, sortDirection],
    queryFn: () =>
      getVehicles(pagination.pageIndex, pagination.pageSize, search || undefined, sortField, sortDirection),
    initialData:
      pagination.pageIndex === 0 && !search && !sortField ? initialData : undefined,
    staleTime: 30_000, // SSR data is fresh for 30s — skip immediate refetch
  });

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

      {!isLoading && vehicles.length === 0 && !search ? (
        <EmptyState
          icon={Car}
          title="No vehicles found"
          description="Get started by creating a new vehicle."
          action={
            <Button onClick={() => router.push("/vehicles/new")}>
              <Plus className="size-4" />
              New Vehicle
            </Button>
          }
        />
      ) : (
        <DataTable
          columns={columns}
          data={vehicles}
          pageCount={data?.totalPages ?? 1}
          totalElements={data?.totalElements ?? 0}
          pagination={pagination}
          sorting={sorting}
          globalFilter={search}
          onPaginationChange={setPagination}
          onSortingChange={setSorting}
          onGlobalFilterChange={(value) => {
            setSearch(value);
            setPagination({ pageIndex: 0, pageSize: pagination.pageSize });
          }}
          onRowClick={(vehicle) => router.push(`/vehicles/${vehicle.id}`)}
          enableCsvExport
          csvFileName="vehicles.csv"
          isLoading={isLoading}
          toolbar={
            <SearchBar
              placeholder="Search by plate or brand..."
              onSearch={(value) => {
                setSearch(value);
                setPagination({ pageIndex: 0, pageSize: pagination.pageSize });
              }}
            />
          }
        />
      )}
    </div>
  );
}
