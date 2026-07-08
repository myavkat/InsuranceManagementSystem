import type { Metadata } from "next";
import { EstimationForm } from "@/components/features/estimations/estimation-form";

export const metadata: Metadata = {
  title: "New Estimation",
};

export default function NewEstimationPage() {
  return <EstimationForm />;
}
