import type { Metadata } from "next";
import { VehicleDetail } from "@/components/features/vehicles/vehicle-detail";

export const metadata: Metadata = {
  title: "Vehicle Detail",
};

export default function VehicleDetailPage() {
  return <VehicleDetail />;
}
