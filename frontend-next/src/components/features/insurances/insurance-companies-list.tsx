"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { createColumnHelper, type ColumnDef } from "@tanstack/react-table";
import { getInsuranceCompanies, type InsuranceCompanyResponse } from "@/lib/api/insurances";
import { PageHeader } from "@/components/features/page-header";
import { StatusBadge } from "@/components/features/status-badge";
import { ErrorAlert } from "@/components/features/error-alert";
import {
  DataTable,
  type DataTablePaginationState,
} from "@/components/features/data-table/data-table";
import { Button } from "@/components/ui/button";
import { ArrowLeft } from "lucide-react";
import { useRouter } from "next/navigation";

const columnHelper = createColumnHelper<InsuranceCompanyResponse>();

const columns: ColumnDef<InsuranceCompanyResponse, any>[] = [
  columnHelper.accessor("name", {
    header: "Name",
    cell: (info) => <span className="font-medium">{info.getValue()}</span>,
  }),
  columnHelper.accessor("rating", {
    header: "Rating",
    cell: (info) => info.getValue()?.toString() ?? "—",
  }),
  columnHelper.accessor("isActive", {
    header: "Status",
    cell: (info) => <StatusBadge status={info.getValue() ? "ACTIVE" : "INACTIVE"} />,
  }),
];

export function InsuranceCompaniesList() {
  const router = useRouter();
  const [pagination] = useState<DataTablePaginationState>({
    pageIndex: 0,
    pageSize: 100,
  });

  const { data: companies, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["insurance-companies"],
    queryFn: getInsuranceCompanies,
  });

  if (isError) {
    return (
      <ErrorAlert
        message={error instanceof Error ? error.message : "Failed to load insurance companies"}
        onRetry={() => refetch()}
      />
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" onClick={() => router.push("/insurances")}>
          <ArrowLeft className="size-4" />
        </Button>
        <PageHeader
          title="Insurance Companies"
          description="List of insurance companies"
        />
      </div>

      <DataTable
        columns={columns}
        data={companies ?? []}
        pageCount={1}
        totalElements={companies?.length ?? 0}
        pagination={pagination}
        sorting={[]}
        globalFilter=""
        onPaginationChange={() => {}}
        onSortingChange={() => {}}
        onGlobalFilterChange={() => {}}
        isLoading={isLoading}
        enableCsvExport
        csvFileName="insurance-companies.csv"
      />
    </div>
  );
}
