"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useQuery, useMutation } from "@tanstack/react-query";
import { createEstimation } from "@/lib/api/estimations";
import { getCustomers } from "@/lib/api/customers";
import { getInsuranceTypes, getInsuranceCompanies } from "@/lib/api/insurances";
import { PageHeader } from "@/components/features/page-header";
import { ErrorAlert } from "@/components/features/error-alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Input } from "@/components/ui/input";
import { ArrowLeft, Search, Send, ChevronLeft, ChevronRight } from "lucide-react";
import { cn } from "@/lib/utils";

const STEP_LABELS = [
  { number: 1, label: "Customer" },
  { number: 2, label: "Insurance" },
  { number: 3, label: "Link Assets" },
  { number: 4, label: "Review" },
];

export function EstimationForm() {
  const router = useRouter();
  const [step, setStep] = useState(1);

  // Step 1 state
  const [selectedCustomerId, setSelectedCustomerId] = useState<string>("");
  const [customerSearch, setCustomerSearch] = useState("");
  const [customerDropdownOpen, setCustomerDropdownOpen] = useState(false);

  // Step 2 state
  const [selectedTypeId, setSelectedTypeId] = useState<string>("");
  const [selectedCompanyId, setSelectedCompanyId] = useState<string>("");

  // Step 3 state
  const [selectedVehicleId, setSelectedVehicleId] = useState<string>("");
  const [selectedRealEstateId, setSelectedRealEstateId] = useState<string>("");
  const [vehicleSearch, setVehicleSearch] = useState("");
  const [vehicleDropdownOpen, setVehicleDropdownOpen] = useState(false);
  const [realEstateSearch, setRealEstateSearch] = useState("");
  const [realEstateDropdownOpen, setRealEstateDropdownOpen] = useState(false);

  // Customer search query
  const { data: customerData } = useQuery({
    queryKey: ["customers", "search", customerSearch],
    queryFn: () => getCustomers(0, 50, customerSearch || undefined),
    enabled: customerDropdownOpen,
  });

  // Reference data queries
  const { data: types } = useQuery({
    queryKey: ["insurance-types"],
    queryFn: getInsuranceTypes,
  });

  const { data: companies } = useQuery({
    queryKey: ["insurance-companies"],
    queryFn: getInsuranceCompanies,
  });

  const customers = customerData?.content ?? [];

  const selectedCustomer = customers.find((c) => c.id === selectedCustomerId);

  const mutation = useMutation({
    mutationFn: () => {
      return createEstimation({
        customerId: selectedCustomerId,
        insuranceTypeId: Number(selectedTypeId),
        companyId: selectedCompanyId ? Number(selectedCompanyId) : undefined,
        vehicleId: selectedVehicleId || undefined,
        realEstateId: selectedRealEstateId || undefined,
      });
    },
    onSuccess: (result) => {
      router.push(`/estimations/${result.id}`);
    },
  });

  const canProceedStep1 = selectedCustomerId !== "";
  const canProceedStep2 = selectedTypeId !== "";

  const handleNext = () => {
    if (step < 4) setStep(step + 1);
  };

  const handleBack = () => {
    if (step > 1) setStep(step - 1);
  };

  const handleSubmit = () => {
    mutation.mutate();
  };

  // Find selected names for review
  const selectedType = types?.find((t) => t.id.toString() === selectedTypeId);

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" onClick={() => router.back()}>
          <ArrowLeft className="size-4" />
        </Button>
        <PageHeader
          title="New Estimation"
          description="Create a new insurance estimation"
        />
      </div>

      {mutation.isError && (
        <ErrorAlert
          message={mutation.error instanceof Error ? mutation.error.message : "Failed to create estimation"}
        />
      )}

      {/* Step Indicator */}
      <div className="flex items-center justify-center gap-2">
        {STEP_LABELS.map((s) => (
          <div key={s.number} className="flex items-center gap-2">
            <div
              className={cn(
                "flex size-8 items-center justify-center rounded-full text-sm font-medium",
                step === s.number
                  ? "bg-primary text-primary-foreground"
                  : step > s.number
                    ? "bg-primary/20 text-primary"
                    : "bg-muted text-muted-foreground"
              )}
            >
              {step > s.number ? "✓" : s.number}
            </div>
            <span
              className={cn(
                "text-sm hidden sm:inline",
                step === s.number ? "font-medium text-foreground" : "text-muted-foreground"
              )}
            >
              {s.label}
            </span>
            {s.number < STEP_LABELS.length && (
              <ChevronRight className="size-4 text-muted-foreground hidden sm:block" />
            )}
          </div>
        ))}
      </div>

      <Card>
        <CardHeader>
          <CardTitle>
            {step === 1 && "Select Customer"}
            {step === 2 && "Select Insurance"}
            {step === 3 && "Link Assets (Optional)"}
            {step === 4 && "Review & Submit"}
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {/* Step 1: Select Customer */}
          {step === 1 && (
            <div className="space-y-4">
              <div className="space-y-1.5">
                <label className="text-sm font-medium">Customer *</label>
                <Select
                  value={selectedCustomerId || undefined}
                  onValueChange={(value) => {
                    setSelectedCustomerId(value ?? "");
                    setCustomerDropdownOpen(false);
                  }}
                  onOpenChange={(open) => {
                    setCustomerDropdownOpen(open);
                    if (open) setCustomerSearch("");
                  }}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder="Search and select a customer" />
                  </SelectTrigger>
                  <SelectContent>
                    <div
                      className="flex items-center gap-2 px-2 pb-2"
                      onPointerDown={(e) => e.stopPropagation()}
                    >
                      <Search className="size-4 text-muted-foreground shrink-0" />
                      <Input
                        placeholder="Search customers..."
                        value={customerSearch}
                        onChange={(e) => setCustomerSearch(e.target.value)}
                        className="h-8"
                      />
                    </div>
                    {customers.length === 0 ? (
                      <div className="px-2 py-4 text-center text-sm text-muted-foreground">
                        {customerSearch ? "No customers found" : "Type to search customers..."}
                      </div>
                    ) : (
                      customers.map((customer) => (
                        <SelectItem key={customer.id} value={customer.id}>
                          {customer.firstName} {customer.lastName} ({customer.nationalId})
                        </SelectItem>
                      ))
                    )}
                  </SelectContent>
                </Select>
              </div>

              {selectedCustomer && (
                <div className="rounded-lg bg-muted p-3 text-sm">
                  <p className="font-medium">
                    {selectedCustomer.firstName} {selectedCustomer.lastName}
                  </p>
                  <p className="text-muted-foreground">{selectedCustomer.email}</p>
                </div>
              )}
            </div>
          )}

          {/* Step 2: Select Insurance */}
          {step === 2 && (
            <div className="space-y-4">
              <div className="space-y-1.5">
                <label className="text-sm font-medium">Insurance Type *</label>
                <Select
                  value={selectedTypeId || undefined}
                  onValueChange={(value) => setSelectedTypeId(value ?? "")}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder="Select insurance type" />
                  </SelectTrigger>
                  <SelectContent>
                    {types?.map((type) => (
                      <SelectItem key={type.id} value={type.id.toString()}>
                        {type.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1.5">
                <label className="text-sm font-medium">Company</label>
                <Select
                  value={selectedCompanyId || undefined}
                  onValueChange={(value) => setSelectedCompanyId(value ?? "")}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder="Select company (optional)" />
                  </SelectTrigger>
                  <SelectContent>
                    {companies?.map((company) => (
                      <SelectItem key={company.id} value={company.id.toString()}>
                        {company.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              {selectedType && (
                <div className="rounded-lg bg-muted p-3 text-sm">
                  <p className="font-medium">{selectedType.name}</p>
                  {selectedCompanyId && (
                    <p className="text-muted-foreground">
                      Company: {companies?.find((c) => c.id.toString() === selectedCompanyId)?.name}
                    </p>
                  )}
                </div>
              )}
            </div>
          )}

          {/* Step 3: Optional Linkage */}
          {step === 3 && (
            <div className="space-y-6">
              <div className="space-y-1.5">
                <label className="text-sm font-medium">Link a Vehicle (optional)</label>
                <Select
                  value={selectedVehicleId || undefined}
                  onValueChange={(value) => {
                    setSelectedVehicleId(value ?? "");
                    setVehicleDropdownOpen(false);
                  }}
                  onOpenChange={(open) => {
                    setVehicleDropdownOpen(open);
                    if (open) setVehicleSearch("");
                  }}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder="Select a vehicle (optional)" />
                  </SelectTrigger>
                  <SelectContent>
                    <div
                      className="flex items-center gap-2 px-2 pb-2"
                      onPointerDown={(e) => e.stopPropagation()}
                    >
                      <Search className="size-4 text-muted-foreground shrink-0" />
                      <Input
                        placeholder="Search by plate..."
                        value={vehicleSearch}
                        onChange={(e) => setVehicleSearch(e.target.value)}
                        className="h-8"
                      />
                    </div>
                    <div className="px-2 py-4 text-center text-sm text-muted-foreground">
                      Type to search vehicles by plate
                    </div>
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1.5">
                <label className="text-sm font-medium">Link Real Estate (optional)</label>
                <Select
                  value={selectedRealEstateId || undefined}
                  onValueChange={(value) => {
                    setSelectedRealEstateId(value ?? "");
                    setRealEstateDropdownOpen(false);
                  }}
                  onOpenChange={(open) => {
                    setRealEstateDropdownOpen(open);
                    if (open) setRealEstateSearch("");
                  }}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder="Select a property (optional)" />
                  </SelectTrigger>
                  <SelectContent>
                    <div
                      className="flex items-center gap-2 px-2 pb-2"
                      onPointerDown={(e) => e.stopPropagation()}
                    >
                      <Search className="size-4 text-muted-foreground shrink-0" />
                      <Input
                        placeholder="Search by address..."
                        value={realEstateSearch}
                        onChange={(e) => setRealEstateSearch(e.target.value)}
                        className="h-8"
                      />
                    </div>
                    <div className="px-2 py-4 text-center text-sm text-muted-foreground">
                      Type to search properties by address
                    </div>
                  </SelectContent>
                </Select>
              </div>

              {/* Summary of selections so far */}
              <div className="rounded-lg bg-muted p-3 text-sm space-y-1">
                <p><span className="text-muted-foreground">Customer:</span> {selectedCustomer?.firstName} {selectedCustomer?.lastName}</p>
                <p><span className="text-muted-foreground">Insurance:</span> {selectedType?.name}</p>
                <p><span className="text-muted-foreground">Vehicle:</span> {selectedVehicleId ? "Selected" : "None"}</p>
                <p><span className="text-muted-foreground">Real Estate:</span> {selectedRealEstateId ? "Selected" : "None"}</p>
              </div>
            </div>
          )}

          {/* Step 4: Review & Submit */}
          {step === 4 && (
            <div className="space-y-4">
              <div className="rounded-lg border p-4 space-y-3">
                <div>
                  <p className="text-sm font-medium text-muted-foreground">Customer</p>
                  <p className="text-sm">{selectedCustomer?.firstName} {selectedCustomer?.lastName}</p>
                </div>
                <div>
                  <p className="text-sm font-medium text-muted-foreground">Insurance Type</p>
                  <p className="text-sm">{selectedType?.name ?? "—"}</p>
                </div>
                <div>
                  <p className="text-sm font-medium text-muted-foreground">Company</p>
                  <p className="text-sm">
                    {selectedCompanyId
                      ? companies?.find((c) => c.id.toString() === selectedCompanyId)?.name
                      : "None selected"}
                  </p>
                </div>
                <div>
                  <p className="text-sm font-medium text-muted-foreground">Vehicle</p>
                  <p className="text-sm">{selectedVehicleId ? "Linked" : "None"}</p>
                </div>
                <div>
                  <p className="text-sm font-medium text-muted-foreground">Real Estate</p>
                  <p className="text-sm">{selectedRealEstateId ? "Linked" : "None"}</p>
                </div>
              </div>

              {mutation.isError && (
                <ErrorAlert
                  message={mutation.error instanceof Error ? mutation.error.message : "Failed to create estimation"}
                />
              )}
            </div>
          )}

          {/* Navigation buttons */}
          <div className="flex justify-between pt-4">
            <div>
              {step > 1 && (
                <Button type="button" variant="outline" onClick={handleBack}>
                  <ChevronLeft className="size-4" />
                  Back
                </Button>
              )}
            </div>
            <div className="flex gap-2">
              {step < 4 ? (
                <Button
                  type="button"
                  onClick={handleNext}
                  disabled={(step === 1 && !canProceedStep1) || (step === 2 && !canProceedStep2)}
                >
                  Next
                  <ChevronRight className="size-4" />
                </Button>
              ) : (
                <Button
                  type="button"
                  onClick={handleSubmit}
                  disabled={mutation.isPending}
                >
                  <Send className="size-4" />
                  {mutation.isPending ? "Submitting..." : "Submit Estimation"}
                </Button>
              )}
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
