"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useQuery, useMutation } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { createEstimation } from "@/lib/api/estimations";
import { getCustomers } from "@/lib/api/customers";
import { getInsurances, getInsuranceTypes } from "@/lib/api/insurances";
import { getVehicles } from "@/lib/api/vehicles";
import { getRealEstates } from "@/lib/api/realestate";
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
import { useUnsavedChanges } from "@/hooks/use-unsaved-changes";
import { estimationSchema, type EstimationFormData } from "@/lib/schemas/estimation";

const STEP_LABELS = [
  { number: 1, label: "Customer" },
  { number: 2, label: "Insurance" },
  { number: 3, label: "Link Assets" },
  { number: 4, label: "Review" },
];

export function EstimationForm() {
  const router = useRouter();
  const [step, setStep] = useState(1);

  // Step 3 search state (UI-only, not form values)
  const [customerSearch, setCustomerSearch] = useState("");
  const [customerDropdownOpen, setCustomerDropdownOpen] = useState(false);
  const [vehicleSearch, setVehicleSearch] = useState("");
  const [vehicleDropdownOpen, setVehicleDropdownOpen] = useState(false);
  const [realEstateSearch, setRealEstateSearch] = useState("");
  const [realEstateDropdownOpen, setRealEstateDropdownOpen] = useState(false);
  const [selectedInsuranceId, setSelectedInsuranceId] = useState<string>("");
  const [selectedInsuranceTypeId, setSelectedInsuranceTypeId] = useState<number | null>(null);

  const {
    handleSubmit,
    setValue,
    watch,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<EstimationFormData>({
    resolver: zodResolver(estimationSchema),
    defaultValues: {
      customerId: "",
      insuranceTypeId: "",
      vehicleId: "",
      realEstateId: "",
    },
  });

  const watchedCustomerId = watch("customerId");
  const watchedTypeId = watch("insuranceTypeId");
  const watchedVehicleId = watch("vehicleId");
  const watchedRealEstateId = watch("realEstateId");

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

  const { data: insurances } = useQuery({
    queryKey: ["insurances"],
    queryFn: () => getInsurances(0, 50),
  });

  // Step 3 asset queries (customer-filtered, enabled when dropdowns open)
  const { data: vehicleData } = useQuery({
    queryKey: ["vehicles", "customer", watchedCustomerId, vehicleSearch],
    queryFn: () => getVehicles(0, 20, vehicleSearch || undefined, undefined, undefined, watchedCustomerId),
    enabled: vehicleDropdownOpen && watchedCustomerId !== "",
  });

  const { data: realEstateData } = useQuery({
    queryKey: ["real-estate", "customer", watchedCustomerId, realEstateSearch],
    queryFn: () => getRealEstates(0, 20, realEstateSearch || undefined, undefined, undefined, watchedCustomerId),
    enabled: realEstateDropdownOpen && watchedCustomerId !== "",
  });

  const customers = customerData?.content ?? [];
  const selectedCustomer = customers.find((c) => c.id === watchedCustomerId);

  const mutation = useMutation({
    mutationFn: (data: EstimationFormData) => {
      return createEstimation({
        customerId: data.customerId,
        insuranceTypeId: Number(data.insuranceTypeId),
        vehicleId: data.vehicleId || undefined,
        realEstateId: data.realEstateId || undefined,
      });
    },
    onSuccess: (result) => {
      router.push(`/estimations/${result.id}`);
    },
  });

  useUnsavedChanges(isDirty);

  const canProceedStep1 = watchedCustomerId !== "";
  const canProceedStep2 = watchedTypeId !== "" && selectedInsuranceTypeId !== null;
  const canProceedStep3 =
    (selectedInsuranceTypeId === 1 && watchedVehicleId !== "") ||
    (selectedInsuranceTypeId === 2 && watchedRealEstateId !== "") ||
    (selectedInsuranceTypeId === 3 || selectedInsuranceTypeId === 4);

  const handleNext = () => {
    if (step < 4) setStep(step + 1);
  };

  const handleBack = () => {
    if (step > 1) setStep(step - 1);
  };

  const onSubmit = (data: EstimationFormData) => {
    mutation.mutate(data);
  };

  // Find selected names for review
  const selectedInsurance = insurances?.content?.find((i) => i.id === selectedInsuranceId);

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

      <form>
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
                    value={watchedCustomerId || undefined}
                    onValueChange={(value) => {
                      setValue("customerId", value ?? "", { shouldDirty: true });
                      setCustomerDropdownOpen(false);
                    }}
                    onOpenChange={(open) => {
                      setCustomerDropdownOpen(open);
                      if (open) setCustomerSearch("");
                    }}
                  >
                    <SelectTrigger className="w-full">
                      <SelectValue>
                        {(value: any) => {
                          if (!value) return "Search and select a customer";
                          const fromList = customers.find((c) => c.id === value);
                          if (fromList) return `${fromList.firstName} ${fromList.lastName} (${fromList.nationalId})`;
                          if (selectedCustomer) return `${selectedCustomer.firstName} ${selectedCustomer.lastName} (${selectedCustomer.nationalId})`;
                          return "";
                        }}
                      </SelectValue>
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
                          onKeyDown={(e) => e.stopPropagation()}
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
                  {errors.customerId?.message && (
                    <p className="text-sm text-destructive" role="alert">
                      {errors.customerId.message}
                    </p>
                  )}
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
                  <label className="text-sm font-medium">Insurance *</label>
                  <Select
                    value={selectedInsuranceId || undefined}
                    onValueChange={(value) => {
                      setSelectedInsuranceId(value ?? "");
                      const ins = insurances?.content?.find((i) => i.id === value);
                      const typeId = ins?.typeId ?? null;
                      setSelectedInsuranceTypeId(typeId);
                      setValue("insuranceTypeId", typeId?.toString() ?? "", { shouldDirty: true });
                    }}
                  >
                    <SelectTrigger className="w-full">
                      <SelectValue>
                        {(value: any) => {
                          if (!value) return "Select insurance";
                          return insurances?.content?.find((i) => i.id === value)?.name ?? "";
                        }}
                      </SelectValue>
                    </SelectTrigger>
                    <SelectContent>
                      {insurances?.content?.map((insurance) => (
                        <SelectItem key={insurance.id} value={insurance.id}>
                          {insurance.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  {errors.insuranceTypeId?.message && (
                    <p className="text-sm text-destructive" role="alert">
                      {errors.insuranceTypeId.message}
                    </p>
                  )}
                </div>

                {selectedInsurance && (
                  <div className="rounded-lg bg-muted p-3 text-sm">
                    <p className="font-medium">{selectedInsurance.name}</p>
                    <p className="text-muted-foreground">{selectedInsurance.description ?? ""}</p>
                  </div>
                )}
              </div>
            )}

            {/* Step 3: Optional Linkage */}
            {step === 3 && (
              <div className="space-y-6">
                {selectedInsuranceTypeId === 1 && (
                  <div className="space-y-1.5">
                    <label className="text-sm font-medium">Link a Vehicle *</label>
                    <Select
                      value={watchedVehicleId || undefined}
                      onValueChange={(value) => {
                        setValue("vehicleId", value ?? "", { shouldDirty: true });
                        setVehicleDropdownOpen(false);
                      }}
                      onOpenChange={(open) => {
                        setVehicleDropdownOpen(open);
                        if (open) setVehicleSearch("");
                      }}
                    >
                      <SelectTrigger className="w-full">
                        <SelectValue>
                          {(value: any) => {
                            if (!value) return "Select a vehicle";
                            const fromList = vehicleData?.content?.find((v) => v.id === value);
                            if (fromList) return `${fromList.plate} — ${fromList.carBrandName} ${fromList.carModelName}`;
                            return "";
                          }}
                        </SelectValue>
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
                            onKeyDown={(e) => e.stopPropagation()}
                            className="h-8"
                          />
                        </div>
                        {!vehicleData?.content?.length ? (
                          <div className="px-2 py-4 text-center text-sm text-muted-foreground">
                            {vehicleSearch ? "No vehicles found" : "Type to search your vehicles..."}
                          </div>
                        ) : (
                          vehicleData.content.map((v) => (
                            <SelectItem key={v.id} value={v.id}>
                              {v.plate} — {v.carBrandName} {v.carModelName}
                            </SelectItem>
                          ))
                        )}
                      </SelectContent>
                    </Select>
                  </div>
                )}

                {selectedInsuranceTypeId === 2 && (
                  <div className="space-y-1.5">
                    <label className="text-sm font-medium">Link Real Estate *</label>
                    <Select
                      value={watchedRealEstateId || undefined}
                      onValueChange={(value) => {
                        setValue("realEstateId", value ?? "", { shouldDirty: true });
                        setRealEstateDropdownOpen(false);
                      }}
                      onOpenChange={(open) => {
                        setRealEstateDropdownOpen(open);
                        if (open) setRealEstateSearch("");
                      }}
                    >
                      <SelectTrigger className="w-full">
                        <SelectValue>
                          {(value: any) => {
                            if (!value) return "Select a property";
                            const fromList = realEstateData?.content?.find((re) => re.id === value);
                            if (fromList) return `${fromList.address}${fromList.cityName ? `, ${fromList.cityName}` : ""}`;
                            return "";
                          }}
                        </SelectValue>
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
                            onKeyDown={(e) => e.stopPropagation()}
                            className="h-8"
                          />
                        </div>
                        {!realEstateData?.content?.length ? (
                          <div className="px-2 py-4 text-center text-sm text-muted-foreground">
                            {realEstateSearch ? "No properties found" : "Type to search your properties..."}
                          </div>
                        ) : (
                          realEstateData.content.map((re) => (
                            <SelectItem key={re.id} value={re.id}>
                              {re.address}{re.cityName ? `, ${re.cityName}` : ""}
                            </SelectItem>
                          ))
                        )}
                      </SelectContent>
                    </Select>
                  </div>
                )}

                {(selectedInsuranceTypeId === 3 || selectedInsuranceTypeId === 4) && (
                  <div className="rounded-lg bg-muted p-4 text-sm text-muted-foreground">
                    No asset linking is required for this insurance type. You can proceed to review.
                  </div>
                )}

                {/* Summary of selections so far */}
                <div className="rounded-lg bg-muted p-3 text-sm space-y-1">
                  <p><span className="text-muted-foreground">Customer:</span> {selectedCustomer?.firstName} {selectedCustomer?.lastName}</p>
                  <p><span className="text-muted-foreground">Insurance:</span> {selectedInsurance?.name}</p>
                  {selectedInsuranceTypeId === 1 && (
                    <p><span className="text-muted-foreground">Vehicle:</span> {watchedVehicleId ? "Selected" : "Not selected"}</p>
                  )}
                  {selectedInsuranceTypeId === 2 && (
                    <p><span className="text-muted-foreground">Real Estate:</span> {watchedRealEstateId ? "Selected" : "Not selected"}</p>
                  )}
                </div>

                {!canProceedStep3 && selectedInsuranceTypeId != null && (
                  <p className="text-sm text-muted-foreground">
                    {selectedInsuranceTypeId === 1
                      ? "Select a vehicle to continue."
                      : selectedInsuranceTypeId === 2
                        ? "Select a property to continue."
                        : ""}
                  </p>
                )}
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
                    <p className="text-sm font-medium text-muted-foreground">Insurance</p>
                    <p className="text-sm">{selectedInsurance?.name ?? "—"}</p>
                  </div>
                  {selectedInsuranceTypeId === 1 && (
                    <div>
                      <p className="text-sm font-medium text-muted-foreground">Vehicle</p>
                      <p className="text-sm">{watchedVehicleId ? "Linked" : "None"}</p>
                    </div>
                  )}
                  {selectedInsuranceTypeId === 2 && (
                    <div>
                      <p className="text-sm font-medium text-muted-foreground">Real Estate</p>
                      <p className="text-sm">{watchedRealEstateId ? "Linked" : "None"}</p>
                    </div>
                  )}
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
                    disabled={(step === 1 && !canProceedStep1) || (step === 2 && !canProceedStep2) || (step === 3 && !canProceedStep3)}
                  >
                    Next
                    <ChevronRight className="size-4" />
                  </Button>
                ) : (
                  <Button
                    type="button"
                    disabled={isSubmitting || mutation.isPending}
                    onClick={handleSubmit(onSubmit)}
                  >
                    <Send className="size-4" />
                    {isSubmitting || mutation.isPending ? "Submitting..." : "Submit Estimation"}
                  </Button>
                )}
              </div>
            </div>
          </CardContent>
        </Card>
      </form>
    </div>
  );
}
