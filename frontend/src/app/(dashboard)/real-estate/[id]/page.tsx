import type { Metadata } from "next";
import { RealEstateDetail } from "@/components/features/real-estate/real-estate-detail";

export const metadata: Metadata = {
  title: "Property Detail",
};

export default function RealEstateDetailPage() {
  return <RealEstateDetail />;
}
