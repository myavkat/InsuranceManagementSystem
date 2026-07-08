"use client";

import { useQuery } from "@tanstack/react-query";
import { getInsuranceCompanies } from "@/lib/api/insurances";
import { PageHeader } from "@/components/features/page-header";
import { StatusBadge } from "@/components/features/status-badge";
import { DataTableSkeleton } from "@/components/features/data-table-skeleton";
import { ErrorAlert } from "@/components/features/error-alert";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { ArrowLeft } from "lucide-react";
import { useRouter } from "next/navigation";

export function InsuranceCompaniesList() {
  const router = useRouter();

  const { data: companies, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["insurance-companies"],
    queryFn: getInsuranceCompanies,
  });

  if (isLoading) return <DataTableSkeleton columns={3} />;
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

      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Name</TableHead>
              <TableHead>Rating</TableHead>
              <TableHead>Status</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {companies && companies.length > 0 ? (
              companies.map((company) => (
                <TableRow key={company.id}>
                  <TableCell className="font-medium">{company.name}</TableCell>
                  <TableCell>{company.rating ?? "—"}</TableCell>
                  <TableCell>
                    <StatusBadge status={company.isActive ? "ACTIVE" : "INACTIVE"} />
                  </TableCell>
                </TableRow>
              ))
            ) : (
              <TableRow>
                <TableCell colSpan={3} className="text-center text-muted-foreground">
                  No insurance companies found
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
