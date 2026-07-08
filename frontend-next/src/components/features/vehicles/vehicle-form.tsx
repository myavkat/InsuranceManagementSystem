"use client";

import { useRouter } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  createVehicle,
  updateVehicle,
  getBrands,
  getModelsByBrand,
  getEngines,
  getFuelTypes,
  getTypes,
  getPackages,
  type VehicleResponse,
  type VehicleRequest,
} from "@/lib/api/vehicles";
import { getCustomers } from "@/lib/api/customers";
import { FormField } from "@/components/features/form-field";
import { PageHeader } from "@/components/features/page-header";
import { ErrorAlert } from "@/components/features/error-alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Input } from "@/components/ui/input";
import { ArrowLeft, Save, Search } from "lucide-react";
import { useState, useCallback } from "react";
import { useUnsavedChanges } from "@/hooks/use-unsaved-changes";
import { vehicleSchema, type VehicleFormData } from "@/lib/schemas/vehicle";

interface VehicleFormProps {
  initialData?: VehicleResponse; // If provided, we're editing
}

export function VehicleForm({ initialData }: VehicleFormProps) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const isEdit = !!initialData;
  const [customerSearch, setCustomerSearch] = useState("");
  const [customerDropdownOpen, setCustomerDropdownOpen] = useState(false);

  // Reference data queries
  const { data: brands, isLoading: brandsLoading } = useQuery({
    queryKey: ["car-brands"],
    queryFn: getBrands,
  });

  const { data: engines } = useQuery({
    queryKey: ["car-engines"],
    queryFn: getEngines,
  });

  const { data: fuelTypes } = useQuery({
    queryKey: ["car-fuel-types"],
    queryFn: getFuelTypes,
  });

  const { data: types } = useQuery({
    queryKey: ["car-types"],
    queryFn: getTypes,
  });

  const { data: packages } = useQuery({
    queryKey: ["car-packages"],
    queryFn: getPackages,
  });

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<VehicleFormData>({
    resolver: zodResolver(vehicleSchema),
    defaultValues: initialData
      ? {
          plate: initialData.plate ?? "",
          chassisNumber: initialData.chassisNumber ?? "",
          licenseFirstDate: initialData.licenseFirstDate ?? "",
          carBrandId: initialData.carBrandId?.toString() ?? "",
          carModelId: initialData.carModelId?.toString() ?? "",
          carEngineId: initialData.carEngineId?.toString() ?? "",
          carFuelTypeId: initialData.carFuelTypeId?.toString() ?? "",
          carTypeId: initialData.carTypeId?.toString() ?? "",
          carPackageId: initialData.carPackageId?.toString() ?? "",
          customerId: initialData.customerId ?? "",
        }
      : {
          plate: "",
          chassisNumber: "",
          licenseFirstDate: "",
          carBrandId: "",
          carModelId: "",
          carEngineId: "",
          carFuelTypeId: "",
          carTypeId: "",
          carPackageId: "",
          customerId: "",
        },
  });

  const watchBrandId = watch("carBrandId");

  // Cascading: models depend on selected brand
  const { data: models, isLoading: modelsLoading } = useQuery({
    queryKey: ["car-models", watchBrandId],
    queryFn: () => getModelsByBrand(Number(watchBrandId)),
    enabled: !!watchBrandId && watchBrandId !== "",
  });

  // Customer search query
  const { data: customerData } = useQuery({
    queryKey: ["customers", "search", customerSearch],
    queryFn: () => getCustomers(0, 50, customerSearch || undefined),
    enabled: customerDropdownOpen,
  });

  const customers = customerData?.content ?? [];

  // Handle brand change — reset model selection only if brand actually changed
  const handleBrandChange = useCallback(
    (value: string | null) => {
      const newBrandId = value ?? "";
      const currentBrandId = watch("carBrandId");

      setValue("carBrandId", newBrandId);

      // Only reset model if brand actually changed
      if (newBrandId !== currentBrandId) {
        setValue("carModelId", "");
      }
    },
    [setValue, watch]
  );

  const mutation = useMutation({
    mutationFn: (data: VehicleFormData) => {
      const payload: VehicleRequest = {
        plate: data.plate || undefined,
        chassisNumber: data.chassisNumber || undefined,
        licenseFirstDate: data.licenseFirstDate || undefined,
        carBrandId: data.carBrandId ? Number(data.carBrandId) : undefined,
        carModelId: data.carModelId ? Number(data.carModelId) : undefined,
        carEngineId: data.carEngineId ? Number(data.carEngineId) : undefined,
        carFuelTypeId: data.carFuelTypeId ? Number(data.carFuelTypeId) : undefined,
        carTypeId: data.carTypeId ? Number(data.carTypeId) : undefined,
        carPackageId: data.carPackageId ? Number(data.carPackageId) : undefined,
        customerId: data.customerId,
      };
      if (isEdit && initialData) {
        return updateVehicle(initialData.id, payload);
      }
      return createVehicle(payload);
    },
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ["vehicles"] });
      queryClient.invalidateQueries({ queryKey: ["vehicle", result.id] });
      router.push(`/vehicles/${result.id}`);
    },
  });

  useUnsavedChanges(isDirty);

  const onSubmit = (data: VehicleFormData) => {
    mutation.mutate(data);
  };

  if (brandsLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-8 w-64" />
        <Card>
          <CardContent className="space-y-4 pt-6">
            {Array.from({ length: 8 }).map((_, i) => (
              <Skeleton key={i} className="h-10 w-full" />
            ))}
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" onClick={() => router.back()}>
          <ArrowLeft className="size-4" />
        </Button>
        <PageHeader
          title={isEdit ? "Edit Vehicle" : "New Vehicle"}
          description={isEdit ? "Update vehicle information" : "Create a new vehicle record"}
        />
      </div>

      {mutation.isError && (
        <ErrorAlert
          message={mutation.error instanceof Error ? mutation.error.message : "Failed to save vehicle"}
        />
      )}

      <form onSubmit={handleSubmit(onSubmit)}>
        <Card>
          <CardHeader>
            <CardTitle>Vehicle Information</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {/* Plate + Chassis Number row */}
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <FormField
                label="Plate"
                {...register("plate")}
                error={errors.plate?.message}
                placeholder="34 ABC 1234"
              />
              <FormField
                label="Chassis Number"
                {...register("chassisNumber")}
                error={errors.chassisNumber?.message}
                placeholder="17 characters alphanumeric"
                maxLength={17}
              />
            </div>

            {/* License First Date */}
            <FormField
              label="License First Date"
              type="date"
              {...register("licenseFirstDate")}
              error={errors.licenseFirstDate?.message}
            />

            {/* Customer dropdown (searchable) */}
            <div className="space-y-1.5">
              <label className="text-sm font-medium">Customer *</label>
              <Select
                value={watch("customerId") || undefined}
                onValueChange={(value) => {
                  setValue("customerId", value ?? "");
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
                      const c = customers.find((c) => c.id === value);
                      if (c) return `${c.firstName} ${c.lastName} (${c.nationalId})`;
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

            {/* Brand → Model (cascading) */}
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div className="space-y-1.5">
                <label className="text-sm font-medium">Brand</label>
                <Select
                  value={watch("carBrandId") || undefined}
                  onValueChange={handleBrandChange}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue>
                      {(value: any) => {
                        if (!value) return "Select a brand";
                        return brands?.find((b) => b.id.toString() === value)?.name ?? "";
                      }}
                    </SelectValue>
                  </SelectTrigger>
                  <SelectContent>
                    {brands?.map((brand) => (
                      <SelectItem key={brand.id} value={brand.id.toString()}>
                        {brand.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1.5">
                <label className="text-sm font-medium">Model</label>
                <Select
                  value={watch("carModelId") || undefined}
                  onValueChange={(value) => setValue("carModelId", value ?? "")}
                  disabled={!watchBrandId || watchBrandId === ""}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue>
                      {(value: any) => {
                        if (!value) {
                          if (!watchBrandId) return "Select a brand first";
                          if (modelsLoading) return "Loading models...";
                          return "Select a model";
                        }
                        return models?.find((m) => m.id.toString() === value)?.name ?? "";
                      }}
                    </SelectValue>
                  </SelectTrigger>
                  <SelectContent>
                    {models?.map((model) => (
                      <SelectItem key={model.id} value={model.id.toString()}>
                        {model.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            {/* Engine + Fuel Type row */}
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div className="space-y-1.5">
                <label className="text-sm font-medium">Engine</label>
                <Select
                  value={watch("carEngineId") || undefined}
                  onValueChange={(value) => setValue("carEngineId", value ?? "")}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue>
                      {(value: any) => {
                        if (!value) return "Select engine";
                        return engines?.find((e) => e.id.toString() === value)?.name ?? "";
                      }}
                    </SelectValue>
                  </SelectTrigger>
                  <SelectContent>
                    {engines?.map((engine) => (
                      <SelectItem key={engine.id} value={engine.id.toString()}>
                        {engine.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1.5">
                <label className="text-sm font-medium">Fuel Type</label>
                <Select
                  value={watch("carFuelTypeId") || undefined}
                  onValueChange={(value) => setValue("carFuelTypeId", value ?? "")}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue>
                      {(value: any) => {
                        if (!value) return "Select fuel type";
                        return fuelTypes?.find((f) => f.id.toString() === value)?.name ?? "";
                      }}
                    </SelectValue>
                  </SelectTrigger>
                  <SelectContent>
                    {fuelTypes?.map((fuel) => (
                      <SelectItem key={fuel.id} value={fuel.id.toString()}>
                        {fuel.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            {/* Type + Package row */}
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div className="space-y-1.5">
                <label className="text-sm font-medium">Type</label>
                <Select
                  value={watch("carTypeId") || undefined}
                  onValueChange={(value) => setValue("carTypeId", value ?? "")}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue>
                      {(value: any) => {
                        if (!value) return "Select type";
                        return types?.find((t) => t.id.toString() === value)?.name ?? "";
                      }}
                    </SelectValue>
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
                <label className="text-sm font-medium">Package</label>
                <Select
                  value={watch("carPackageId") || undefined}
                  onValueChange={(value) => setValue("carPackageId", value ?? "")}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue>
                      {(value: any) => {
                        if (!value) return "Select package";
                        return packages?.find((p) => p.id.toString() === value)?.name ?? "";
                      }}
                    </SelectValue>
                  </SelectTrigger>
                  <SelectContent>
                    {packages?.map((pkg) => (
                      <SelectItem key={pkg.id} value={pkg.id.toString()}>
                        {pkg.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            {/* Cancel + Save buttons */}
            <div className="flex justify-end gap-2 pt-4">
              <Button type="button" variant="outline" onClick={() => router.back()}>
                Cancel
              </Button>
              <Button type="submit" disabled={isSubmitting || mutation.isPending}>
                <Save className="size-4" />
                {isSubmitting || mutation.isPending ? "Saving..." : "Save"}
              </Button>
            </div>
          </CardContent>
        </Card>
      </form>
    </div>
  );
}
