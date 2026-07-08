import type { Metadata } from "next";
import { serverFetch } from "@/lib/api/server-fetch";
import type { CustomerResponse } from "@/lib/api/customers";
import type { PageResponse } from "@/lib/api/types";
import { CustomerList } from "@/components/features/customers/customer-list";

export const metadata: Metadata = {
  title: "Customers",
};

export default async function CustomersPage() {
  let initialData: PageResponse<CustomerResponse> | undefined;

  try {
    initialData = await serverFetch<PageResponse<CustomerResponse>>(
      "/api/customers?page=0&size=20",
      { cache: "no-store" },
    );
  } catch {
    // Let the error boundary handle it
    throw new Error("Failed to load customers");
  }

  return <CustomerList initialData={initialData} />;
}
