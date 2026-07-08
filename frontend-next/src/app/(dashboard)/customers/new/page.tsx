import type { Metadata } from "next";
import { CustomerForm } from "@/components/features/customers/customer-form";

export const metadata: Metadata = {
  title: "New Customer",
};

export default function NewCustomerPage() {
  return <CustomerForm />;
}
