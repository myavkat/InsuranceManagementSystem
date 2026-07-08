"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { createColumnHelper, type ColumnDef } from "@tanstack/react-table";
import { getCustomers, type CustomerResponse } from "@/lib/api/customers";
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
import { Users, Plus } from "lucide-react";
import type { PageResponse } from "@/lib/api/types";

const columnHelper = createColumnHelper<CustomerResponse>();

const columns: ColumnDef<CustomerResponse, any>[] = [
  columnHelper.accessor("firstName", {
    header: "First Name",
    cell: (info) => (
      <span className="font-medium">
        {info.getValue()} {info.row.original.lastName}
      </span>
    ),
    enableSorting: true,
  }),
  columnHelper.accessor("lastName", {
    header: "Last Name",
    enableSorting: true,
  }),
  columnHelper.accessor("nationalId", {
    header: "National ID",
    enableSorting: true,
  }),
  columnHelper.accessor("email", {
    header: "Email",
    enableSorting: true,
  }),
  columnHelper.accessor("phone", {
    header: "Phone",
    cell: (info) => info.getValue() ?? "—",
  }),
  columnHelper.accessor("cityName", {
    header: "City",
    cell: (info) => info.getValue() ?? "—",
  }),
  columnHelper.accessor("createdAt", {
    header: "Created",
    cell: (info) => formatDate(info.getValue()),
    enableSorting: true,
  }),
];

interface CustomerListProps {
  initialData?: PageResponse<CustomerResponse>;
}

export function CustomerList({ initialData }: CustomerListProps) {
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
    queryKey: ["customers", pagination.pageIndex, pagination.pageSize, search, sortField, sortDirection],
    queryFn: () =>
      getCustomers(pagination.pageIndex, pagination.pageSize, search || undefined, sortField, sortDirection),
    initialData:
      pagination.pageIndex === 0 && !search && !sortField ? initialData : undefined,
    staleTime: 30_000, // SSR data is fresh for 30s — skip immediate refetch
  });

  if (isError) {
    return (
      <ErrorAlert
        message={error instanceof Error ? error.message : "Failed to load customers"}
        onRetry={() => refetch()}
      />
    );
  }

  const customers = data?.content ?? [];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Customers"
        description="Manage customer records"
        action={
          <Button onClick={() => router.push("/customers/new")}>
            <Plus className="size-4" />
            New Customer
          </Button>
        }
      />

      {!isLoading && customers.length === 0 && !search ? (
        <EmptyState
          icon={Users}
          title="No customers found"
          description="Get started by creating a new customer."
          action={
            <Button onClick={() => router.push("/customers/new")}>
              <Plus className="size-4" />
              New Customer
            </Button>
          }
        />
      ) : (
        <DataTable
          columns={columns}
          data={customers}
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
          onRowClick={(customer) => router.push(`/customers/${customer.id}`)}
          enableCsvExport
          csvFileName="customers.csv"
          isLoading={isLoading}
          toolbar={
            <SearchBar
              placeholder="Search by name or national ID..."
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
