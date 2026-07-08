import type { Metadata } from "next";
import { serverFetch } from "@/lib/api/server-fetch";
import type { EstimationResponse } from "@/lib/api/estimations";
import type { PageResponse } from "@/lib/api/types";
import { EstimationList } from "@/components/features/estimations/estimation-list";

export const metadata: Metadata = {
  title: "Estimations",
};

export default async function EstimationsPage() {
  let initialData: PageResponse<EstimationResponse> | undefined;

  try {
    initialData = await serverFetch<PageResponse<EstimationResponse>>(
      "/api/estimations?page=0&size=20",
      { cache: "no-store" },
    );
  } catch {
    throw new Error("Failed to load estimations");
  }

  return <EstimationList initialData={initialData} />;
}
