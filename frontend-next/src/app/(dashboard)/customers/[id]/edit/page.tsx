import type { Metadata } from "next";
import { EditCustomerForm } from "@/components/features/customers/edit-customer-form";

export const metadata: Metadata = {
  title: "Edit Customer",
};

export default function EditCustomerPage() {
  return <EditCustomerForm />;
}
