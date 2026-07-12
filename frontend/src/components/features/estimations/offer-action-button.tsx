"use client";

import { useRouter } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { acceptOffer } from "@/lib/api/estimations";
import type { EstimationStatus } from "@/lib/api/estimations";
import { Button } from "@/components/ui/button";
import { CheckCircle, CreditCard } from "lucide-react";

interface OfferActionButtonProps {
  estimationId: string;
  status: EstimationStatus;
}

export function OfferActionButton({ estimationId, status }: OfferActionButtonProps) {
  const router = useRouter();
  const queryClient = useQueryClient();

  const acceptMutation = useMutation({
    mutationFn: () => acceptOffer(estimationId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["estimation", estimationId] });
    },
  });

  // Determine button state from status
  switch (status) {
    case "WAITING_APPROVAL":
      // Show "Accept Offer" button
      return (
        <Button
          onClick={() => acceptMutation.mutate()}
          disabled={acceptMutation.isPending}
          variant="default"
        >
          <CheckCircle className="size-4" />
          {acceptMutation.isPending ? "Accepting..." : "Accept Offer"}
        </Button>
      );

    case "PAYMENT_WAITING":
      // Show "Start Payment" button — navigates to payment page
      return (
        <Button
          onClick={() => router.push(`/estimations/${estimationId}/payment`)}
          variant="default"
        >
          <CreditCard className="size-4" />
          Start Payment
        </Button>
      );

    case "ACTIVE":
    case "COMPLETED":
      // Terminal states — show disabled "Already Active/Completed" button
      return (
        <Button disabled variant="outline">
          <CheckCircle className="size-4" />
          {status === "ACTIVE" ? "Policy Active" : "Completed"}
        </Button>
      );

    case "REJECTED":
      // Rejected — show disabled "Rejected" indicator
      return (
        <Button disabled variant="outline" className="text-destructive">
          Offer Rejected
        </Button>
      );

    case "STARTED":
    default:
      // Still processing — show disabled "Processing" indicator
      return (
        <Button disabled variant="outline">
          Processing...
        </Button>
      );
  }
}
