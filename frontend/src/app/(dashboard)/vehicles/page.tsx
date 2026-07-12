import type { Metadata } from "next";
import { serverFetch } from "@/lib/api/server-fetch";
import type { VehicleResponse } from "@/lib/api/vehicles";
import type { PageResponse } from "@/lib/api/types";
import { VehicleList } from "@/components/features/vehicles/vehicle-list";

export const metadata: Metadata = {
  title: "Vehicles",
};

export default async function VehiclesPage() {
  let initialData: PageResponse<VehicleResponse> | undefined;

  try {
    initialData = await serverFetch<PageResponse<VehicleResponse>>(
      "/api/vehicles?page=0&size=20",
      { cache: "no-store" },
    );
  } catch (e) {
    throw new Error(e instanceof Error ? e.message : "Failed to load vehicles");
  }

  return <VehicleList initialData={initialData} />;
}
