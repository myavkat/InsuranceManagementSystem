import type { Metadata } from "next";
import { EditRealEstateForm } from "@/components/features/real-estate/edit-real-estate-form";

export const metadata: Metadata = {
  title: "Edit Property",
};

export default function EditRealEstatePage() {
  return <EditRealEstateForm />;
}
