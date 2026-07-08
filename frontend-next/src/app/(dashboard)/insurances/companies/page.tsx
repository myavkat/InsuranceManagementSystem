import type { Metadata } from "next";
import { InsuranceCompaniesList } from "@/components/features/insurances/insurance-companies-list";

export const metadata: Metadata = {
  title: "Insurance Companies",
};

export default function InsuranceCompaniesPage() {
  return <InsuranceCompaniesList />;
}
