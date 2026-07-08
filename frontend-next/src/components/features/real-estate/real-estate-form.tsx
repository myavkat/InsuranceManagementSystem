"use client";

import { useRouter } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import {
  createRealEstate,
  updateRealEstate,
  getConstructionTypes,
  getLuxuryClasses,
  getUsageTypes,
  type RealEstateResponse,
  type RealEstateRequest,
} from "@/lib/api/realestate";
import { getCustomers } from "@/lib/api/customers";
import { getCities } from "@/lib/api/reference-data";
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
import { useState } from "react";

const realEstateSchema = z.object({
  address: z.string().min(1, "Address is required"),
  cityId: z.string().optional(),
  district: z.string().optional(),
  squareMeters: z.string()
    .refine((v) => !v || Number(v) > 0, "Must be a positive number"),
  constructionYear: z.string()
    .refine((v) => !v || Number(v) <= new Date().getFullYear(), "Cannot be in the future"),
  constructionTypeId: z.string().optional(),
  luxuryClassId: z.string().optional(),
  usageTypeId: z.string().optional(),
  customerId: z.string().min(1, "Customer is required"),
});

type RealEstateFormData = z.infer<typeof realEstateSchema>;

interface RealEstateFormProps {
  initialData?: RealEstateResponse;
}

export function RealEstateForm({ initialData }: RealEstateFormProps) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const isEdit = !!initialData;
  const [customerSearch, setCustomerSearch] = useState("");
  const [customerDropdownOpen, setCustomerDropdownOpen] = useState(false);

  // Reference data queries
  const { data: constructionTypes } = useQuery({
    queryKey: ["construction-types"],
    queryFn: getConstructionTypes,
  });

  const { data: luxuryClasses } = useQuery({
    queryKey: ["luxury-classes"],
    queryFn: getLuxuryClasses,
  });

  const { data: usageTypes } = useQuery({
    queryKey: ["usage-types"],
    queryFn: getUsageTypes,
  });

  const { data: cities } = useQuery({
    queryKey: ["cities"],
    queryFn: getCities,
  });

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<RealEstateFormData>({
    resolver: zodResolver(realEstateSchema),
    defaultValues: initialData
      ? {
          address: initialData.address ?? "",
          cityId: initialData.cityId?.toString() ?? "",
          district: initialData.district ?? "",
          squareMeters: initialData.squareMeters?.toString() ?? "",
          constructionYear: initialData.constructionYear?.toString() ?? "",
          constructionTypeId: initialData.constructionTypeId?.toString() ?? "",
          luxuryClassId: initialData.luxuryClassId?.toString() ?? "",
          usageTypeId: initialData.usageTypeId?.toString() ?? "",
          customerId: initialData.customerId ?? "",
        }
      : {
          address: "",
          cityId: "",
          district: "",
          squareMeters: "",
          constructionYear: "",
          constructionTypeId: "",
          luxuryClassId: "",
          usageTypeId: "",
          customerId: "",
        },
  });

  // Customer search query
  const { data: customerData } = useQuery({
    queryKey: ["customers", "search", customerSearch],
    queryFn: () => getCustomers(0, 50, customerSearch || undefined),
    enabled: customerDropdownOpen,
  });

  const customers = customerData?.content ?? [];

  const mutation = useMutation({
    mutationFn: (data: RealEstateFormData) => {
      const payload: RealEstateRequest = {
        address: data.address,
        cityId: data.cityId ? Number(data.cityId) : undefined,
        district: data.district || undefined,
        squareMeters: data.squareMeters ? Number(data.squareMeters) : 0,
        constructionYear: data.constructionYear ? Number(data.constructionYear) : undefined,
        constructionTypeId: data.constructionTypeId ? Number(data.constructionTypeId) : undefined,
        luxuryClassId: data.luxuryClassId ? Number(data.luxuryClassId) : undefined,
        usageTypeId: data.usageTypeId ? Number(data.usageTypeId) : undefined,
        customerId: data.customerId,
      };
      if (isEdit && initialData) {
        return updateRealEstate(initialData.id, payload);
      }
      return createRealEstate(payload);
    },
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ["real-estate"] });
      router.push(`/real-estate/${result.id}`);
    },
  });

  const onSubmit = (data: RealEstateFormData) => {
    mutation.mutate(data);
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" onClick={() => router.back()}>
          <ArrowLeft className="size-4" />
        </Button>
        <PageHeader
          title={isEdit ? "Edit Property" : "New Property"}
          description={isEdit ? "Update property information" : "Create a new property record"}
        />
      </div>

      {mutation.isError && (
        <ErrorAlert
          message={mutation.error instanceof Error ? mutation.error.message : "Failed to save property"}
        />
      )}

      <form onSubmit={handleSubmit(onSubmit)}>
        <Card>
          <CardHeader>
            <CardTitle>Property Information</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {/* Address (full width) */}
            <FormField
              label="Address *"
              {...register("address")}
              error={errors.address?.message}
              placeholder="Full address"
            />

            {/* City + District row */}
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div className="space-y-1.5">
                <label className="text-sm font-medium">City</label>
                <Select
                  value={watch("cityId") || undefined}
                  onValueChange={(value) => setValue("cityId", value ?? "")}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder="Select a city" />
                  </SelectTrigger>
                  <SelectContent>
                    {cities?.map((city) => (
                      <SelectItem key={city.id} value={city.id.toString()}>
                        {city.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <FormField
                label="District"
                {...register("district")}
                error={errors.district?.message}
                placeholder="District"
              />
            </div>

            {/* Square Meters + Construction Year row */}
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <FormField
                label="Square Meters *"
                type="number"
                {...register("squareMeters")}
                error={errors.squareMeters?.message}
                placeholder="e.g., 120"
              />
              <FormField
                label="Construction Year"
                type="number"
                {...register("constructionYear")}
                error={errors.constructionYear?.message}
                placeholder="e.g., 2010"
              />
            </div>

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
              {errors.customerId?.message && (
                <p className="text-sm text-destructive" role="alert">
                  {errors.customerId.message}
                </p>
              )}
            </div>

            {/* Construction Type + Luxury Class + Usage Type row */}
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
              <div className="space-y-1.5">
                <label className="text-sm font-medium">Construction Type</label>
                <Select
                  value={watch("constructionTypeId") || undefined}
                  onValueChange={(value) => setValue("constructionTypeId", value ?? "")}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder="Select type" />
                  </SelectTrigger>
                  <SelectContent>
                    {constructionTypes?.map((type) => (
                      <SelectItem key={type.id} value={type.id.toString()}>
                        {type.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1.5">
                <label className="text-sm font-medium">Luxury Class</label>
                <Select
                  value={watch("luxuryClassId") || undefined}
                  onValueChange={(value) => setValue("luxuryClassId", value ?? "")}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder="Select class" />
                  </SelectTrigger>
                  <SelectContent>
                    {luxuryClasses?.map((cls) => (
                      <SelectItem key={cls.id} value={cls.id.toString()}>
                        {cls.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1.5">
                <label className="text-sm font-medium">Usage Type</label>
                <Select
                  value={watch("usageTypeId") || undefined}
                  onValueChange={(value) => setValue("usageTypeId", value ?? "")}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder="Select type" />
                  </SelectTrigger>
                  <SelectContent>
                    {usageTypes?.map((type) => (
                      <SelectItem key={type.id} value={type.id.toString()}>
                        {type.name}
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
