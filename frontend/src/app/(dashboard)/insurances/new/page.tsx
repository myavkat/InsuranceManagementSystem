import type { Metadata } from "next";
import { InsuranceForm } from "@/components/features/insurances/insurance-form";

export const metadata: Metadata = {
  title: "New Insurance Product",
};

export default function NewInsurancePage() {
  return <InsuranceForm />;
}
