"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { createColumnHelper, type ColumnDef } from "@tanstack/react-table";
import { getInsurances, getInsuranceTypes, getInsuranceCompanies, type InsuranceResponse } from "@/lib/api/insurances";
import { Button } from "@/components/ui/button";
import { SearchBar } from "@/components/features/search-bar";
import { EmptyState } from "@/components/features/empty-state";
import { ErrorAlert } from "@/components/features/error-alert";
import { PageHeader } from "@/components/features/page-header";
import { StatusBadge } from "@/components/features/status-badge";
import {
  DataTable,
  type DataTablePaginationState,
  type DataTableSortingState,
} from "@/components/features/data-table/data-table";
import { formatCurrency, formatDate } from "@/components/features/data-table/column-helpers";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Shield, Plus } from "lucide-react";
import type { PageResponse } from "@/lib/api/types";

const columnHelper = createColumnHelper<InsuranceResponse>();

const columns: ColumnDef<InsuranceResponse, any>[] = [
  columnHelper.accessor("name", {
    header: "Name",
    cell: (info) => <span className="font-medium">{info.getValue()}</span>,
    enableSorting: true,
  }),
  columnHelper.accessor("typeName", {
    header: "Type",
    cell: (info) => info.getValue() ?? "—",
  }),
  columnHelper.accessor("companyName", {
    header: "Company",
    cell: (info) => info.getValue() ?? "—",
  }),
  columnHelper.accessor("basePremium", {
    header: "Base Premium",
    cell: (info) => formatCurrency(info.getValue()),
    enableSorting: true,
  }),
  columnHelper.accessor("isActive", {
    header: "Status",
    cell: (info) => <StatusBadge status={info.getValue() ? "ACTIVE" : "INACTIVE"} />,
    enableSorting: true,
  }),
  columnHelper.accessor("createdAt", {
    header: "Created",
    cell: (info) => formatDate(info.getValue()),
    enableSorting: true,
  }),
];

interface InsuranceListProps {
  initialData?: PageResponse<InsuranceResponse>;
}

export function InsuranceList({ initialData }: InsuranceListProps) {
  const router = useRouter();
  const [pagination, setPagination] = useState<DataTablePaginationState>({
    pageIndex: 0,
    pageSize: 20,
  });
  const [sorting, setSorting] = useState<DataTableSortingState[]>([]);
  const [search, setSearch] = useState("");
  const [typeId, setTypeId] = useState<string>("");
  const [companyId, setCompanyId] = useState<string>("");

  const sortField = sorting[0]?.id;
  const sortDirection = sorting[0]?.desc ? "desc" : "asc";

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["insurances", pagination.pageIndex, pagination.pageSize, search, typeId, companyId, sortField, sortDirection],
    queryFn: () =>
      getInsurances(
        pagination.pageIndex,
        pagination.pageSize,
        typeId ? Number(typeId) : undefined,
        companyId ? Number(companyId) : undefined,
        search || undefined,
        sortField,
        sortDirection,
      ),
    initialData:
      pagination.pageIndex === 0 && !search && !typeId && !companyId && !sortField ? initialData : undefined,
    staleTime: 30_000, // SSR data is fresh for 30s — skip immediate refetch
  });

  const { data: types } = useQuery({
    queryKey: ["insurance-types"],
    queryFn: getInsuranceTypes,
  });

  const { data: companies } = useQuery({
    queryKey: ["insurance-companies"],
    queryFn: getInsuranceCompanies,
  });

  if (isError) {
    return (
      <ErrorAlert
        message={error instanceof Error ? error.message : "Failed to load insurance products"}
        onRetry={() => refetch()}
      />
    );
  }

  const insurances = data?.content ?? [];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Insurance Products"
        description="Manage insurance products"
        action={
          <Button onClick={() => router.push("/insurances/new")}>
            <Plus className="size-4" />
            New Product
          </Button>
        }
      />

      {!isLoading && insurances.length === 0 && !search && !typeId && !companyId ? (
        <EmptyState
          icon={Shield}
          title="No insurance products found"
          description="Get started by creating a new insurance product."
          action={
            <Button onClick={() => router.push("/insurances/new")}>
              <Plus className="size-4" />
              New Product
            </Button>
          }
        />
      ) : (
        <DataTable
          columns={columns}
          data={insurances}
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
          onRowClick={(insurance) => router.push(`/insurances/${insurance.id}`)}
          enableCsvExport
          csvFileName="insurances.csv"
          isLoading={isLoading}
          toolbar={
            <>
              <SearchBar
                placeholder="Search by name..."
                onSearch={(value) => {
                  setSearch(value);
                  setPagination({ pageIndex: 0, pageSize: pagination.pageSize });
                }}
              />
              <Select
                value={typeId || undefined}
                onValueChange={(value) => {
                  setTypeId(value ?? "");
                  setPagination({ pageIndex: 0, pageSize: pagination.pageSize });
                }}
              >
                <SelectTrigger className="w-[180px]">
                  <SelectValue placeholder="All types" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">All types</SelectItem>
                  {types?.map((type) => (
                    <SelectItem key={type.id} value={type.id.toString()}>{type.name}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Select
                value={companyId || undefined}
                onValueChange={(value) => {
                  setCompanyId(value ?? "");
                  setPagination({ pageIndex: 0, pageSize: pagination.pageSize });
                }}
              >
                <SelectTrigger className="w-[180px]">
                  <SelectValue placeholder="All companies" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">All companies</SelectItem>
                  {companies?.map((company) => (
                    <SelectItem key={company.id} value={company.id.toString()}>{company.name}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </>
          }
        />
      )}
    </div>
  );
}
