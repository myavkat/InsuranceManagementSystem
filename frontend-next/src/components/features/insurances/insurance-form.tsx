"use client";

import { useRouter } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  createInsurance,
  updateInsurance,
  getInsuranceTypes,
  getInsuranceCompanies,
  type InsuranceResponse,
  type InsuranceRequest,
} from "@/lib/api/insurances";
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
import { ArrowLeft, Save } from "lucide-react";
import { useUnsavedChanges } from "@/hooks/use-unsaved-changes";
import { insuranceSchema, type InsuranceFormData } from "@/lib/schemas/insurance";

interface InsuranceFormProps {
  initialData?: InsuranceResponse;
}

export function InsuranceForm({ initialData }: InsuranceFormProps) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const isEdit = !!initialData;

  const { data: types } = useQuery({
    queryKey: ["insurance-types"],
    queryFn: getInsuranceTypes,
  });

  const { data: companies } = useQuery({
    queryKey: ["insurance-companies"],
    queryFn: getInsuranceCompanies,
  });

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<InsuranceFormData>({
    resolver: zodResolver(insuranceSchema),
    defaultValues: initialData
      ? {
          name: initialData.name ?? "",
          description: initialData.description ?? "",
          typeId: initialData.typeId?.toString() ?? "",
          companyId: initialData.companyId?.toString() ?? "",
          basePremium: initialData.basePremium?.toString() ?? "",
          isActive: initialData.isActive ?? true,
        }
      : {
          name: "",
          description: "",
          typeId: "",
          companyId: "",
          basePremium: "",
          isActive: true,
        },
  });

  const mutation = useMutation({
    mutationFn: (data: InsuranceFormData) => {
      const payload: InsuranceRequest = {
        name: data.name,
        description: data.description || undefined,
        typeId: Number(data.typeId),
        companyId: Number(data.companyId),
        basePremium: Number(data.basePremium),
        isActive: data.isActive,
      };
      if (isEdit && initialData) {
        return updateInsurance(initialData.id, payload);
      }
      return createInsurance(payload);
    },
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ["insurances"] });
      router.push(`/insurances/${result.id}`);
    },
  });

  useUnsavedChanges(isDirty);

  const onSubmit = (data: InsuranceFormData) => {
    mutation.mutate(data);
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" onClick={() => router.back()}>
          <ArrowLeft className="size-4" />
        </Button>
        <PageHeader
          title={isEdit ? "Edit Insurance Product" : "New Insurance Product"}
          description={isEdit ? "Update insurance product information" : "Create a new insurance product"}
        />
      </div>

      {mutation.isError && (
        <ErrorAlert
          message={mutation.error instanceof Error ? mutation.error.message : "Failed to save insurance product"}
        />
      )}

      <form onSubmit={handleSubmit(onSubmit)}>
        <Card>
          <CardHeader>
            <CardTitle>Product Information</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {/* Name (full width) */}
            <FormField
              label="Name *"
              {...register("name")}
              error={errors.name?.message}
              placeholder="Product name"
            />

            {/* Description (full width) */}
            <FormField
              label="Description"
              {...register("description")}
              error={errors.description?.message}
              placeholder="Product description"
            />

            {/* Type + Company row */}
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div className="space-y-1.5">
                <label className="text-sm font-medium">Insurance Type *</label>
                <Select
                  value={watch("typeId") || undefined}
                  onValueChange={(value) => setValue("typeId", value ?? "")}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder="Select type" />
                  </SelectTrigger>
                  <SelectContent>
                    {types?.map((type) => (
                      <SelectItem key={type.id} value={type.id.toString()}>
                        {type.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {errors.typeId?.message && (
                  <p className="text-sm text-destructive" role="alert">
                    {errors.typeId.message}
                  </p>
                )}
              </div>

              <div className="space-y-1.5">
                <label className="text-sm font-medium">Company *</label>
                <Select
                  value={watch("companyId") || undefined}
                  onValueChange={(value) => setValue("companyId", value ?? "")}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder="Select company" />
                  </SelectTrigger>
                  <SelectContent>
                    {companies?.map((company) => (
                      <SelectItem key={company.id} value={company.id.toString()}>
                        {company.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {errors.companyId?.message && (
                  <p className="text-sm text-destructive" role="alert">
                    {errors.companyId.message}
                  </p>
                )}
              </div>
            </div>

            {/* Base Premium */}
            <FormField
              label="Base Premium *"
              type="number"
              step="0.01"
              {...register("basePremium")}
              error={errors.basePremium?.message}
              placeholder="e.g., 2500.00"
            />

            {/* Active toggle */}
            <div className="flex items-center gap-2">
              <input
                type="checkbox"
                id="isActive"
                checked={watch("isActive")}
                onChange={(e) => setValue("isActive", e.target.checked)}
                className="size-4 rounded border-gray-300"
              />
              <label htmlFor="isActive" className="text-sm font-medium">
                Active
              </label>
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
