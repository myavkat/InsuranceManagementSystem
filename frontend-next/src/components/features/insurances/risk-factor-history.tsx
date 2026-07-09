"use client";

import { useQuery } from "@tanstack/react-query";
import { getRiskFactorHistory } from "@/lib/api/insurances";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Skeleton } from "@/components/ui/skeleton";
import { History } from "lucide-react";

interface Props {
  insuranceId: string;
}

export function RiskFactorHistory({ insuranceId }: Props) {
  const { data, isLoading } = useQuery({
    queryKey: ["risk-factor-history", insuranceId],
    queryFn: () => getRiskFactorHistory(insuranceId),
  });

  if (isLoading) return <Skeleton className="h-32 w-full" />;

  const entries = data?.content ?? [];

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <History className="size-4" />
          Change History
        </CardTitle>
      </CardHeader>
      <CardContent>
        {entries.length === 0 ? (
          <p className="text-sm text-muted-foreground text-center py-4">
            No changes recorded yet.
          </p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Factor</TableHead>
                <TableHead>Old Value</TableHead>
                <TableHead>New Value</TableHead>
                <TableHead>Changed At</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {entries.map((entry) => (
                <TableRow key={entry.id}>
                  <TableCell className="font-medium">{entry.factorName}</TableCell>
                  <TableCell>{entry.oldValue?.toFixed(2) ?? "—"}</TableCell>
                  <TableCell>{entry.newValue.toFixed(2)}</TableCell>
                  <TableCell className="text-muted-foreground">
                    {new Date(entry.changedAt).toLocaleString()}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  );
}
