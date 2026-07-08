import type { Metadata } from "next";
import { InsuranceDetail } from "@/components/features/insurances/insurance-detail";

export const metadata: Metadata = {
  title: "Insurance Product Detail",
};

export default function InsuranceDetailPage() {
  return <InsuranceDetail />;
}
