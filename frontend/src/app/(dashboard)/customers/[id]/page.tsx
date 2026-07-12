import type { Metadata } from "next";
import { CustomerDetail } from "@/components/features/customers/customer-detail";

export const metadata: Metadata = {
  title: "Customer Detail",
};

export default function CustomerDetailPage() {
  return <CustomerDetail />;
}
