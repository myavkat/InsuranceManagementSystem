import type { Metadata } from "next";
import { RealEstateForm } from "@/components/features/real-estate/real-estate-form";

export const metadata: Metadata = {
  title: "New Property",
};

export default function NewRealEstatePage() {
  return <RealEstateForm />;
}
