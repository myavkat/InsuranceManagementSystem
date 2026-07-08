import type { Metadata } from "next";
import { CustomerList } from "@/components/features/customers/customer-list";

export const metadata: Metadata = {
  title: "Customers",
};

export default function CustomersPage() {
  return <CustomerList />;
}
