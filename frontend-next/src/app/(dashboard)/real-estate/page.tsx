import type { Metadata } from "next";
import { RealEstateList } from "@/components/features/real-estate/real-estate-list";

export const metadata: Metadata = {
  title: "Real Estate",
};

export default function RealEstatePage() {
  return <RealEstateList />;
}
