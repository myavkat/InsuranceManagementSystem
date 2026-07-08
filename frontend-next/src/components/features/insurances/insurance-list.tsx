"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { getInsurances, getInsuranceTypes, getInsuranceCompanies } from "@/lib/api/insurances";
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
import { Shield, Plus } from "lucide-react";

export function InsuranceList() {
  const router = useRouter();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState("");
  const [typeId, setTypeId] = useState<string>("");
  const [companyId, setCompanyId] = useState<string>("");
  const pageSize = 20;

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["insurances", page, typeId, companyId, search],
    queryFn: () => getInsurances(
      page,
      pageSize,
      typeId ? Number(typeId) : undefined,
      companyId ? Number(companyId) : undefined,
      search || undefined
    ),
  });

  const { data: types } = useQuery({
    queryKey: ["insurance-types"],
    queryFn: getInsuranceTypes,
  });

  const { data: companies } = useQuery({
    queryKey: ["insurance-companies"],
    queryFn: getInsuranceCompanies,
  });

  if (isLoading) return <DataTableSkeleton columns={6} />;
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

      <div className="flex flex-wrap gap-3">
        <div className="flex-1 min-w-[200px]">
          <SearchBar
            placeholder="Search by name..."
            onSearch={(value) => {
              setSearch(value);
              setPage(0);
            }}
          />
        </div>
        <Select
          value={typeId || undefined}
          onValueChange={(value) => {
            setTypeId(value ?? "");
            setPage(0);
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
            setPage(0);
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
      </div>

      {insurances.length === 0 ? (
        <EmptyState
          icon={Shield}
          title="No insurance products found"
          description={search || typeId || companyId ? "Try adjusting your filters." : "Get started by creating a new insurance product."}
          action={
            !search && !typeId && !companyId && (
              <Button onClick={() => router.push("/insurances/new")}>
                <Plus className="size-4" />
                New Product
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
                  <TableHead>Name</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Company</TableHead>
                  <TableHead>Base Premium</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Created</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {insurances.map((insurance) => (
                  <TableRow
                    key={insurance.id}
                    className="cursor-pointer hover:bg-muted/50"
                    onClick={() => router.push(`/insurances/${insurance.id}`)}
                  >
                    <TableCell className="font-medium">{insurance.name}</TableCell>
                    <TableCell>{insurance.typeName ?? "—"}</TableCell>
                    <TableCell>{insurance.companyName ?? "—"}</TableCell>
                    <TableCell>
                      {insurance.basePremium != null
                        ? new Intl.NumberFormat("tr-TR", { style: "currency", currency: "TRY" }).format(insurance.basePremium)
                        : "—"}
                    </TableCell>
                    <TableCell>
                      <StatusBadge status={insurance.isActive ? "ACTIVE" : "INACTIVE"} />
                    </TableCell>
                    <TableCell>
                      {new Date(insurance.createdAt).toLocaleDateString()}
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
