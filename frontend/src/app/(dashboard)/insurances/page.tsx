import type { Metadata } from "next";
import { serverFetch } from "@/lib/api/server-fetch";
import type { InsuranceResponse } from "@/lib/api/insurances";
import type { PageResponse } from "@/lib/api/types";
import { InsuranceList } from "@/components/features/insurances/insurance-list";

export const metadata: Metadata = {
  title: "Insurance Products",
};

export default async function InsurancesPage() {
  let initialData: PageResponse<InsuranceResponse> | undefined;

  try {
    initialData = await serverFetch<PageResponse<InsuranceResponse>>(
      "/api/insurances?page=0&size=20",
      { cache: "no-store" },
    );
  } catch (e) {
    throw new Error(e instanceof Error ? e.message : "Failed to load insurance products");
  }

  return <InsuranceList initialData={initialData} />;
}
