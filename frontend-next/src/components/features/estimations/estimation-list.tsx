"use client";

import { useState, useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { createColumnHelper, type ColumnDef } from "@tanstack/react-table";
import { getEstimations, type EstimationResponse, type EstimationStatus } from "@/lib/api/estimations";
import { getCustomer } from "@/lib/api/customers";
import type { PageResponse } from "@/lib/api/types";
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
import { ClipboardList, Plus } from "lucide-react";

const statusOptions: { value: string; label: string }[] = [
  { value: "", label: "All statuses" },
  { value: "STARTED", label: "Started" },
  { value: "WAITING_APPROVAL", label: "Waiting Approval" },
  { value: "PAYMENT_WAITING", label: "Payment Waiting" },
  { value: "ACTIVE", label: "Active" },
  { value: "COMPLETED", label: "Completed" },
  { value: "REJECTED", label: "Rejected" },
];

const columnHelper = createColumnHelper<EstimationResponse>();

const columns: ColumnDef<EstimationResponse, any>[] = [
  columnHelper.accessor("customerName", {
    header: "Customer",
    cell: (info) => <span className="font-medium">{info.getValue() ?? "—"}</span>,
    enableSorting: true,
  }),
  columnHelper.accessor("insuranceName", {
    header: "Insurance",
    cell: (info) => info.getValue() ?? "—",
  }),
  columnHelper.accessor("insuranceTypeName", {
    header: "Insurance Type",
    cell: (info) => info.getValue() ?? "—",
  }),
  columnHelper.accessor("status", {
    header: "Status",
    cell: (info) => <StatusBadge status={info.getValue()} />,
    enableSorting: true,
  }),
  columnHelper.accessor("premium", {
    header: "Premium",
    cell: (info) => formatCurrency(info.getValue()),
  }),
  columnHelper.accessor("createdAt", {
    header: "Created",
    cell: (info) => formatDate(info.getValue()),
    enableSorting: true,
  }),
];

interface EstimationListProps {
  initialData?: PageResponse<EstimationResponse>;
}

export function EstimationList({ initialData }: EstimationListProps) {
  const router = useRouter();
  const [pagination, setPagination] = useState<DataTablePaginationState>({
    pageIndex: 0,
    pageSize: 20,
  });
  const [sorting, setSorting] = useState<DataTableSortingState[]>([]);
  const [customerSearch, setCustomerSearch] = useState("");
  const [status, setStatus] = useState("");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");

  const sortField = sorting[0]?.id;
  const sortDirection = sorting[0]?.desc ? "desc" : "asc";

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["estimations", pagination.pageIndex, pagination.pageSize, status, customerSearch, dateFrom, dateTo, sortField, sortDirection],
    queryFn: () => getEstimations({
      page: pagination.pageIndex,
      size: pagination.pageSize,
      status: (status || undefined) as EstimationStatus | undefined,
      customerId: customerSearch || undefined,
      dateFrom: dateFrom || undefined,
      dateTo: dateTo || undefined,
      sort: sortField,
      direction: sortDirection,
    }),
    initialData:
      pagination.pageIndex === 0 && !status && !customerSearch && !dateFrom && !dateTo && !sortField
        ? initialData
        : undefined,
    staleTime: 30_000, // SSR data is fresh for 30s — skip immediate refetch
  });

  const estimations = data?.content ?? [];

  // Collect unique customer IDs from the current page for name resolution
  const uniqueCustomerIds = useMemo(() => {
    const ids = new Set<string>();
    estimations.forEach((e) => {
      if (e.customerId) ids.add(e.customerId);
    });
    return Array.from(ids);
  }, [estimations]);

  // Fetch customer names for all unique customer IDs on this page
  const { data: customerNames } = useQuery({
    queryKey: ["customer-names", ...uniqueCustomerIds],
    queryFn: async () => {
      const results = await Promise.allSettled(
        uniqueCustomerIds.map((id) => getCustomer(id))
      );
      const nameMap: Record<string, string> = {};
      results.forEach((result) => {
        if (result.status === "fulfilled") {
          const customer = result.value;
          nameMap[customer.id] = `${customer.firstName} ${customer.lastName}`;
        }
      });
      return nameMap;
    },
    enabled: uniqueCustomerIds.length > 0,
    staleTime: 60_000,
  });

  // Enrich estimation rows with resolved customer and insurance type names
  const enrichedData = useMemo(() => {
    return estimations.map((estimation) => ({
      ...estimation,
      customerName: estimation.customerName ?? customerNames?.[estimation.customerId] ?? undefined,
      insuranceTypeName: estimation.insuranceTypeName ?? undefined,
    }));
  }, [estimations, customerNames]);

  if (isError) {
    return (
      <ErrorAlert
        message={error instanceof Error ? error.message : "Failed to load estimations"}
        onRetry={() => refetch()}
      />
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Offers"
        description="Manage insurance offers"
        action={
          <Button onClick={() => router.push("/estimations/new")}>
            <Plus className="size-4" />
            New Estimation
          </Button>
        }
      />

      {!isLoading && estimations.length === 0 && !status && !customerSearch && !dateFrom && !dateTo ? (
        <EmptyState
          icon={ClipboardList}
          title="No offers found"
          description="Get started by creating a new estimation."
          action={
            <Button onClick={() => router.push("/estimations/new")}>
              <Plus className="size-4" />
              New Estimation
            </Button>
          }
        />
      ) : (
        <DataTable
          columns={columns}
          data={enrichedData}
          pageCount={data?.totalPages ?? 1}
          totalElements={data?.totalElements ?? 0}
          pagination={pagination}
          sorting={sorting}
          globalFilter={customerSearch}
          onPaginationChange={setPagination}
          onSortingChange={setSorting}
          onGlobalFilterChange={(value) => {
            setCustomerSearch(value);
            setPagination({ pageIndex: 0, pageSize: pagination.pageSize });
          }}
          onRowClick={(estimation) => router.push(`/estimations/${estimation.id}`)}
          enableCsvExport
          csvFileName="estimations.csv"
          isLoading={isLoading}
          toolbar={
            <>
              <SearchBar
                placeholder="Search by customer name or ID..."
                onSearch={(value) => {
                  setCustomerSearch(value);
                  setPagination({ pageIndex: 0, pageSize: pagination.pageSize });
                }}
              />
              <Select
                value={status || undefined}
                onValueChange={(value) => {
                  setStatus(value ?? "");
                  setPagination({ pageIndex: 0, pageSize: pagination.pageSize });
                }}
              >
                <SelectTrigger className="w-[160px]">
                  <SelectValue placeholder="All statuses" />
                </SelectTrigger>
                <SelectContent>
                  {statusOptions.map((opt) => (
                    <SelectItem key={opt.value} value={opt.value}>{opt.label}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <input
                type="date"
                value={dateFrom}
                onChange={(e) => {
                  setDateFrom(e.target.value);
                  setPagination({ pageIndex: 0, pageSize: pagination.pageSize });
                }}
                className="flex h-8 w-[160px] rounded-lg border border-input bg-transparent px-2.5 py-1 text-sm transition-colors outline-none placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:pointer-events-none disabled:opacity-50"
                placeholder="From date"
              />
              <input
                type="date"
                value={dateTo}
                onChange={(e) => {
                  setDateTo(e.target.value);
                  setPagination({ pageIndex: 0, pageSize: pagination.pageSize });
                }}
                className="flex h-8 w-[160px] rounded-lg border border-input bg-transparent px-2.5 py-1 text-sm transition-colors outline-none placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:pointer-events-none disabled:opacity-50"
                placeholder="To date"
              />
            </>
          }
        />
      )}
    </div>
  );
}
