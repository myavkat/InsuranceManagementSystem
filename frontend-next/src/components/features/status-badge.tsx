import { Badge } from "@/components/ui/badge";

type StatusVariant = "default" | "secondary" | "destructive" | "outline";

interface StatusBadgeProps {
  status: string;
}

const statusMap: Record<string, { label: string; variant: StatusVariant }> = {
  STARTED:           { label: "Started",           variant: "secondary" },
  WAITING_APPROVAL:  { label: "Waiting Approval",  variant: "secondary" },
  PAYMENT_WAITING:   { label: "Payment Waiting",   variant: "secondary" },
  ACTIVE:            { label: "Active",            variant: "default" },
  COMPLETED:         { label: "Completed",         variant: "default" },
  REJECTED:          { label: "Rejected",          variant: "destructive" },
  PENDING:           { label: "Pending",           variant: "secondary" },
  INACTIVE:          { label: "Inactive",          variant: "outline" },
};

export function StatusBadge({ status }: StatusBadgeProps) {
  const config = statusMap[status] ?? { label: status, variant: "outline" as StatusVariant };
  return <Badge variant={config.variant}>{config.label}</Badge>;
}
