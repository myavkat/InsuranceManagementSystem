import type { Metadata } from "next";
import { EditInsuranceForm } from "@/components/features/insurances/edit-insurance-form";

export const metadata: Metadata = {
  title: "Edit Insurance Product",
};

export default function EditInsurancePage() {
  return <EditInsuranceForm />;
}
