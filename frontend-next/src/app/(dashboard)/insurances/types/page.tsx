import type { Metadata } from "next";
import { InsuranceTypesList } from "@/components/features/insurances/insurance-types-list";

export const metadata: Metadata = {
  title: "Insurance Types",
};

export default function InsuranceTypesPage() {
  return <InsuranceTypesList />;
}
