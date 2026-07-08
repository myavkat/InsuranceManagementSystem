import type { Metadata } from "next";
import { serverFetch } from "@/lib/api/server-fetch";
import type { RealEstateResponse } from "@/lib/api/realestate";
import type { PageResponse } from "@/lib/api/types";
import { RealEstateList } from "@/components/features/real-estate/real-estate-list";

export const metadata: Metadata = {
  title: "Real Estate",
};

export default async function RealEstatePage() {
  let initialData: PageResponse<RealEstateResponse> | undefined;

  try {
    initialData = await serverFetch<PageResponse<RealEstateResponse>>(
      "/api/real-estate?page=0&size=20",
      { cache: "no-store" },
    );
  } catch {
    throw new Error("Failed to load real estate properties");
  }

  return <RealEstateList initialData={initialData} />;
}
