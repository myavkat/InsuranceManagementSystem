"use client";

import { useQuery } from "@tanstack/react-query";
import { createColumnHelper, type ColumnDef } from "@tanstack/react-table";
import { getInsuranceTypes, type InsuranceTypeResponse } from "@/lib/api/insurances";
import { PageHeader } from "@/components/features/page-header";
import { ErrorAlert } from "@/components/features/error-alert";
import {
  DataTable,
  type DataTablePaginationState,
} from "@/components/features/data-table/data-table";
import { Button } from "@/components/ui/button";
import { ArrowLeft } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";

const columnHelper = createColumnHelper<InsuranceTypeResponse>();

const columns: ColumnDef<InsuranceTypeResponse, any>[] = [
  columnHelper.accessor("id", {
    header: "ID",
    cell: (info) => info.getValue(),
  }),
  columnHelper.accessor("name", {
    header: "Name",
    cell: (info) => <span className="font-medium">{info.getValue()}</span>,
    enableSorting: true,
  }),
];

export function InsuranceTypesList() {
  const router = useRouter();
  const [pagination] = useState<DataTablePaginationState>({
    pageIndex: 0,
    pageSize: 100,
  });

  const { data: types, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["insurance-types"],
    queryFn: getInsuranceTypes,
  });

  if (isError) {
    return (
      <ErrorAlert
        message={error instanceof Error ? error.message : "Failed to load insurance types"}
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
          title="Insurance Types"
          description="List of insurance types"
        />
      </div>

      <DataTable
        columns={columns}
        data={types ?? []}
        pageCount={1}
        totalElements={types?.length ?? 0}
        pagination={pagination}
        sorting={[]}
        globalFilter=""
        onPaginationChange={() => {}}
        onSortingChange={() => {}}
        onGlobalFilterChange={() => {}}
        isLoading={isLoading}
        csvFileName="insurance-types.csv"
      />
    </div>
  );
}
