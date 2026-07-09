"use client";

import { useParams, useRouter } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { processPayment } from "@/lib/api/estimations";
import { paymentSchema, type PaymentFormData } from "@/lib/schemas/payment";
import { PageHeader } from "@/components/features/page-header";
import { ErrorAlert } from "@/components/features/error-alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { ArrowLeft, CreditCard, Lock } from "lucide-react";

export function PaymentForm() {
  const params = useParams();
  const router = useRouter();
  const queryClient = useQueryClient();
  const estimationId = params.id as string;

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<PaymentFormData>({
    resolver: zodResolver(paymentSchema),
    defaultValues: {
      cardNumber: "",
      cardHolder: "",
      expiryMonth: "",
      expiryYear: "",
      cvv: "",
    },
  });

  const paymentMutation = useMutation({
    mutationFn: (data: PaymentFormData) => {
      // The payment is always successful — we don't actually send the card data.
      // We just call the backend to transition status to ACTIVE.
      return processPayment(estimationId);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["estimation", estimationId] });
      router.push(`/estimations/${estimationId}`);
    },
  });

  const onSubmit = (data: PaymentFormData) => {
    paymentMutation.mutate(data);
  };

  return (
    <div className="space-y-6 max-w-lg mx-auto">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" onClick={() => router.back()}>
          <ArrowLeft className="size-4" />
        </Button>
        <PageHeader
          title="Payment"
          description="Enter payment details to activate the policy"
        />
      </div>

      {paymentMutation.isError && (
        <ErrorAlert
          message={
            paymentMutation.error instanceof Error
              ? paymentMutation.error.message
              : "Payment failed"
          }
        />
      )}

      <form onSubmit={handleSubmit(onSubmit)}>
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <CreditCard className="size-5" />
              Card Details
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {/* Note: This is a dummy payment page — all payments succeed. */}

            {/* Card Number */}
            <div className="space-y-1.5">
              <label className="text-sm font-medium">Card Number</label>
              <Input
                {...register("cardNumber")}
                placeholder="1234567890123456"
                maxLength={16}
                inputMode="numeric"
              />
              {errors.cardNumber?.message && (
                <p className="text-sm text-destructive" role="alert">
                  {errors.cardNumber.message}
                </p>
              )}
            </div>

            {/* Card Holder */}
            <div className="space-y-1.5">
              <label className="text-sm font-medium">Card Holder</label>
              <Input
                {...register("cardHolder")}
                placeholder="AD SOYAD"
              />
              {errors.cardHolder?.message && (
                <p className="text-sm text-destructive" role="alert">
                  {errors.cardHolder.message}
                </p>
              )}
            </div>

            {/* Expiry Row */}
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <label className="text-sm font-medium">Expiry Month</label>
                <Input
                  {...register("expiryMonth")}
                  placeholder="MM"
                  maxLength={2}
                  inputMode="numeric"
                />
                {errors.expiryMonth?.message && (
                  <p className="text-sm text-destructive" role="alert">
                    {errors.expiryMonth.message}
                  </p>
                )}
              </div>
              <div className="space-y-1.5">
                <label className="text-sm font-medium">Expiry Year</label>
                <Input
                  {...register("expiryYear")}
                  placeholder="YYYY"
                  maxLength={4}
                  inputMode="numeric"
                />
                {errors.expiryYear?.message && (
                  <p className="text-sm text-destructive" role="alert">
                    {errors.expiryYear.message}
                  </p>
                )}
              </div>
            </div>

            {/* CVV */}
            <div className="space-y-1.5">
              <label className="text-sm font-medium">CVV</label>
              <Input
                {...register("cvv")}
                placeholder="123"
                maxLength={3}
                inputMode="numeric"
                type="password"
              />
              {errors.cvv?.message && (
                <p className="text-sm text-destructive" role="alert">
                  {errors.cvv.message}
                </p>
              )}
            </div>

            {/* Security Note */}
            <div className="flex items-center gap-2 text-xs text-muted-foreground pt-2">
              <Lock className="size-3" />
              This is a dummy payment page — all payments are accepted. No real payment is processed.
            </div>

            {/* Submit Button */}
            <Button
              type="submit"
              className="w-full"
              disabled={isSubmitting || paymentMutation.isPending}
            >
              <CreditCard className="size-4" />
              {isSubmitting || paymentMutation.isPending
                ? "Processing..."
                : "Pay & Activate Policy"}
            </Button>
          </CardContent>
        </Card>
      </form>
    </div>
  );
}
