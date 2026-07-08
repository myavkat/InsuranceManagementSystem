"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { getRealEstates, type RealEstateResponse } from "@/lib/api/realestate";
import type { PageResponse } from "@/lib/api/types";
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
import { Building2, Plus } from "lucide-react";

interface RealEstateListProps {
  initialData?: PageResponse<RealEstateResponse>;
}

export function RealEstateList({ initialData }: RealEstateListProps) {
  const router = useRouter();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState("");
  const pageSize = 20;

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["real-estate", page, search],
    queryFn: () => getRealEstates(page, pageSize, search || undefined),
    initialData: page === 0 && !search ? initialData : undefined,
  });

  if (isLoading) return <DataTableSkeleton columns={6} />;
  if (isError) {
    return (
      <ErrorAlert
        message={error instanceof Error ? error.message : "Failed to load properties"}
        onRetry={() => refetch()}
      />
    );
  }

  const properties = data?.content ?? [];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Real Estate"
        description="Manage property records"
        action={
          <Button onClick={() => router.push("/real-estate/new")}>
            <Plus className="size-4" />
            New Property
          </Button>
        }
      />

      <SearchBar
        placeholder="Search by address..."
        onSearch={(value) => {
          setSearch(value);
          setPage(0);
        }}
      />

      {properties.length === 0 ? (
        <EmptyState
          icon={Building2}
          title="No properties found"
          description={search ? "Try adjusting your search." : "Get started by creating a new property."}
          action={
            !search && (
              <Button onClick={() => router.push("/real-estate/new")}>
                <Plus className="size-4" />
                New Property
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
                  <TableHead>Address</TableHead>
                  <TableHead>City</TableHead>
                  <TableHead>Square Meters</TableHead>
                  <TableHead>Construction Year</TableHead>
                  <TableHead>Customer</TableHead>
                  <TableHead>Created</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {properties.map((property) => (
                  <TableRow
                    key={property.id}
                    className="cursor-pointer hover:bg-muted/50"
                    onClick={() => router.push(`/real-estate/${property.id}`)}
                  >
                    <TableCell className="font-medium">{property.address}</TableCell>
                    <TableCell>{property.cityName ?? "—"}</TableCell>
                    <TableCell>{property.squareMeters ? `${property.squareMeters} m²` : "—"}</TableCell>
                    <TableCell>{property.constructionYear ?? "—"}</TableCell>
                    <TableCell>{property.customerName ?? "—"}</TableCell>
                    <TableCell>
                      {new Date(property.createdAt).toLocaleDateString()}
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
