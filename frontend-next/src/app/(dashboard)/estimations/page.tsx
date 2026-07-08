import type { Metadata } from "next";
import { EstimationList } from "@/components/features/estimations/estimation-list";

export const metadata: Metadata = {
  title: "Estimations",
};

export default function EstimationsPage() {
  return <EstimationList />;
}
