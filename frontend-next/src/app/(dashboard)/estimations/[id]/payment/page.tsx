import type { Metadata } from "next";
import { PaymentForm } from "@/components/features/estimations/payment-form";

export const metadata: Metadata = {
  title: "Payment",
};

export default function PaymentPage() {
  return <PaymentForm />;
}
