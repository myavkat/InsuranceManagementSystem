"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { getCustomers } from "@/lib/api/customers";
import { Button } from "@/components/ui/button";
import { SearchBar } from "@/components/features/search-bar";
import { PaginationBar } from "@/components/features/pagination-bar";
import { DataTableSkeleton } from "@/components/features/data-table-skeleton";
import { EmptyState } from "@/components/features/empty-state";
import { ErrorAlert } from "@/components/features/error-alert";
import { PageHeader } from "@/components/features/page-header";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table";
import { Users, Plus } from "lucide-react";

export function CustomerList() {
  const router = useRouter();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState("");
  const pageSize = 20;

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["customers", page, search],
    queryFn: () => getCustomers(page, pageSize, search || undefined),
  });

  if (isLoading) return <DataTableSkeleton columns={6} />;
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

      <SearchBar
        placeholder="Search by name or national ID..."
        onSearch={(value) => {
          setSearch(value);
          setPage(0);
        }}
      />

      {customers.length === 0 ? (
        <EmptyState
          icon={Users}
          title="No customers found"
          description={search ? "Try adjusting your search." : "Get started by creating a new customer."}
          action={
            !search && (
              <Button onClick={() => router.push("/customers/new")}>
                <Plus className="size-4" />
                New Customer
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
                  <TableHead>National ID</TableHead>
                  <TableHead>Email</TableHead>
                  <TableHead>Phone</TableHead>
                  <TableHead>City</TableHead>
                  <TableHead>Created</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {customers.map((customer) => (
                  <TableRow
                    key={customer.id}
                    className="cursor-pointer hover:bg-muted/50"
                    onClick={() => router.push(`/customers/${customer.id}`)}
                  >
                    <TableCell className="font-medium">
                      {customer.firstName} {customer.lastName}
                    </TableCell>
                    <TableCell>{customer.nationalId}</TableCell>
                    <TableCell>{customer.email}</TableCell>
                    <TableCell>{customer.phone ?? "—"}</TableCell>
                    <TableCell>{customer.cityName ?? "—"}</TableCell>
                    <TableCell>
                      {new Date(customer.createdAt).toLocaleDateString()}
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
