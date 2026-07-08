import type { Metadata } from "next";
import { VehicleForm } from "@/components/features/vehicles/vehicle-form";

export const metadata: Metadata = {
  title: "New Vehicle",
};

export default function NewVehiclePage() {
  return <VehicleForm />;
}
