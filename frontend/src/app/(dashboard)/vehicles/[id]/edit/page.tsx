import type { Metadata } from "next";
import { EditVehicleForm } from "@/components/features/vehicles/edit-vehicle-form";

export const metadata: Metadata = {
  title: "Edit Vehicle",
};

export default function EditVehiclePage() {
  return <EditVehicleForm />;
}
