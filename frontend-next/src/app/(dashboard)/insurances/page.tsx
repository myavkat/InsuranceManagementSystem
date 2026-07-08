import type { Metadata } from "next";
import { InsuranceList } from "@/components/features/insurances/insurance-list";

export const metadata: Metadata = {
  title: "Insurance Products",
};

export default function InsurancesPage() {
  return <InsuranceList />;
}
