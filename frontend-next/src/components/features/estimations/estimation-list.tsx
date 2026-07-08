"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { getEstimations, type EstimationStatus } from "@/lib/api/estimations";
import { Button } from "@/components/ui/button";
import { SearchBar } from "@/components/features/search-bar";
import { PaginationBar } from "@/components/features/pagination-bar";
import { DataTableSkeleton } from "@/components/features/data-table-skeleton";
import { EmptyState } from "@/components/features/empty-state";
import { ErrorAlert } from "@/components/features/error-alert";
import { PageHeader } from "@/components/features/page-header";
import { StatusBadge } from "@/components/features/status-badge";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table";
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/components/ui/select";
import { ClipboardList, Plus } from "lucide-react";

const statusOptions: { value: string; label: string }[] = [
  { value: "", label: "All statuses" },
  { value: "STARTED", label: "Started" },
  { value: "COMPLETED", label: "Completed" },
  { value: "REJECTED", label: "Rejected" },
];

export function EstimationList() {
  const router = useRouter();
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState("");
  const [customerSearch, setCustomerSearch] = useState("");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const pageSize = 20;

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["estimations", page, status, customerSearch, dateFrom, dateTo],
    queryFn: () => getEstimations({
      page,
      size: pageSize,
      status: (status || undefined) as EstimationStatus | undefined,
      customerId: customerSearch || undefined,
      dateFrom: dateFrom || undefined,
      dateTo: dateTo || undefined,
    }),
  });

  if (isLoading) return <DataTableSkeleton columns={6} />;
  if (isError) {
    return (
      <ErrorAlert
        message={error instanceof Error ? error.message : "Failed to load estimations"}
        onRetry={() => refetch()}
      />
    );
  }

  const estimations = data?.content ?? [];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Estimations"
        description="Manage insurance estimations"
        action={
          <Button onClick={() => router.push("/estimations/new")}>
            <Plus className="size-4" />
            New Estimation
          </Button>
        }
      />

      {/* Filter bar */}
      <div className="flex flex-wrap gap-3">
        <div className="flex-1 min-w-[200px]">
          <SearchBar
            placeholder="Search by customer name or ID..."
            onSearch={(value) => {
              setCustomerSearch(value);
              setPage(0);
            }}
          />
        </div>
        <Select
          value={status || undefined}
          onValueChange={(value) => {
            setStatus(value ?? "");
            setPage(0);
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
            setPage(0);
          }}
          className="flex h-10 w-[160px] rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
          placeholder="From date"
        />
        <input
          type="date"
          value={dateTo}
          onChange={(e) => {
            setDateTo(e.target.value);
            setPage(0);
          }}
          className="flex h-10 w-[160px] rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
          placeholder="To date"
        />
      </div>

      {estimations.length === 0 ? (
        <EmptyState
          icon={ClipboardList}
          title="No estimations found"
          description={status || customerSearch || dateFrom || dateTo ? "Try adjusting your filters." : "Get started by creating a new estimation."}
          action={
            !status && !customerSearch && !dateFrom && !dateTo && (
              <Button onClick={() => router.push("/estimations/new")}>
                <Plus className="size-4" />
                New Estimation
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
                  <TableHead>Customer</TableHead>
                  <TableHead>Insurance Type</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Premium</TableHead>
                  <TableHead>Created</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {estimations.map((estimation) => (
                  <TableRow
                    key={estimation.id}
                    className="cursor-pointer hover:bg-muted/50"
                    onClick={() => router.push(`/estimations/${estimation.id}`)}
                  >
                    <TableCell className="font-medium">
                      {estimation.customerName ?? "—"}
                    </TableCell>
                    <TableCell>{estimation.insuranceTypeName ?? "—"}</TableCell>
                    <TableCell>
                      <StatusBadge status={estimation.status} />
                    </TableCell>
                    <TableCell>
                      {estimation.premium != null
                        ? new Intl.NumberFormat("tr-TR", { style: "currency", currency: "TRY" }).format(estimation.premium)
                        : "—"}
                    </TableCell>
                    <TableCell>
                      {new Date(estimation.createdAt).toLocaleDateString()}
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
