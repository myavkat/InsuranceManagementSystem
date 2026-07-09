import type { Metadata } from "next";
import { EstimationDetail } from "@/components/features/estimations/estimation-detail";

export const metadata: Metadata = {
  title: "Premium Detail",
};

export default function EstimationDetailPage() {
  return <EstimationDetail />;
}
