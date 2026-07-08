"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { createColumnHelper, type ColumnDef } from "@tanstack/react-table";
import { getRealEstates, type RealEstateResponse } from "@/lib/api/realestate";
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
import { Building2, Plus } from "lucide-react";
import type { PageResponse } from "@/lib/api/types";

const columnHelper = createColumnHelper<RealEstateResponse>();

const columns: ColumnDef<RealEstateResponse, any>[] = [
  columnHelper.accessor("address", {
    header: "Address",
    cell: (info) => <span className="font-medium">{info.getValue()}</span>,
    enableSorting: true,
  }),
  columnHelper.accessor("cityName", {
    header: "City",
    cell: (info) => info.getValue() ?? "—",
  }),
  columnHelper.accessor("squareMeters", {
    header: "Square Meters",
    cell: (info) => `${info.getValue()} m²`,
    enableSorting: true,
  }),
  columnHelper.accessor("constructionYear", {
    header: "Construction Year",
    cell: (info) => info.getValue()?.toString() ?? "—",
  }),
  columnHelper.accessor("customerName", {
    header: "Customer",
    cell: (info) => info.getValue() ?? "—",
  }),
  columnHelper.accessor("createdAt", {
    header: "Created",
    cell: (info) => formatDate(info.getValue()),
    enableSorting: true,
  }),
];

interface RealEstateListProps {
  initialData?: PageResponse<RealEstateResponse>;
}

export function RealEstateList({ initialData }: RealEstateListProps) {
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
    queryKey: ["real-estate", pagination.pageIndex, pagination.pageSize, search, sortField, sortDirection],
    queryFn: () =>
      getRealEstates(pagination.pageIndex, pagination.pageSize, search || undefined, sortField, sortDirection),
    initialData:
      pagination.pageIndex === 0 && !search && !sortField ? initialData : undefined,
    staleTime: 30_000, // SSR data is fresh for 30s — skip immediate refetch
  });

  if (isError) {
    return (
      <ErrorAlert
        message={error instanceof Error ? error.message : "Failed to load properties"}
        onRetry={() => refetch()}
      />
    );
  }

  const properties = data?.content ?? [];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Real Estate"
        description="Manage property records"
        action={
          <Button onClick={() => router.push("/real-estate/new")}>
            <Plus className="size-4" />
            New Property
          </Button>
        }
      />

      {!isLoading && properties.length === 0 && !search ? (
        <EmptyState
          icon={Building2}
          title="No properties found"
          description="Get started by creating a new property."
          action={
            <Button onClick={() => router.push("/real-estate/new")}>
              <Plus className="size-4" />
              New Property
            </Button>
          }
        />
      ) : (
        <DataTable
          columns={columns}
          data={properties}
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
          onRowClick={(property) => router.push(`/real-estate/${property.id}`)}
          enableCsvExport
          csvFileName="real-estate.csv"
          isLoading={isLoading}
          toolbar={
            <SearchBar
              placeholder="Search by address..."
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
