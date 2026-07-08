import type { Metadata } from "next";
import { VehicleList } from "@/components/features/vehicles/vehicle-list";

export const metadata: Metadata = {
  title: "Vehicles",
};

export default function VehiclesPage() {
  return <VehicleList />;
}
