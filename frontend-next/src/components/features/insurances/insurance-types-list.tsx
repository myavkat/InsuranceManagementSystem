"use client";

import { useQuery } from "@tanstack/react-query";
import { getInsuranceTypes } from "@/lib/api/insurances";
import { PageHeader } from "@/components/features/page-header";
import { DataTableSkeleton } from "@/components/features/data-table-skeleton";
import { ErrorAlert } from "@/components/features/error-alert";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { ArrowLeft } from "lucide-react";
import { useRouter } from "next/navigation";

export function InsuranceTypesList() {
  const router = useRouter();

  const { data: types, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["insurance-types"],
    queryFn: getInsuranceTypes,
  });

  if (isLoading) return <DataTableSkeleton columns={2} />;
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

      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>ID</TableHead>
              <TableHead>Name</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {types && types.length > 0 ? (
              types.map((type) => (
                <TableRow key={type.id}>
                  <TableCell>{type.id}</TableCell>
                  <TableCell className="font-medium">{type.name}</TableCell>
                </TableRow>
              ))
            ) : (
              <TableRow>
                <TableCell colSpan={2} className="text-center text-muted-foreground">
                  No insurance types found
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
