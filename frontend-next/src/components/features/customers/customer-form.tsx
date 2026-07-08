"use client";

import { useRouter } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { createCustomer, updateCustomer, type CustomerResponse, type CustomerRequest } from "@/lib/api/customers";
import { getCities, getProfessions } from "@/lib/api/reference-data";
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

const customerSchema = z.object({
  firstName: z.string().min(1, "First name is required"),
  lastName: z.string().min(1, "Last name is required"),
  nationalId: z.string()
    .min(11, "TCKN must be 11 digits")
    .max(11, "TCKN must be 11 digits")
    .regex(/^\d{11}$/, "TCKN must be exactly 11 digits"),
  email: z.string().email("Invalid email address"),
  phone: z.string().optional(),
  birthDate: z.string().optional(),
  address: z.string().optional(),
  cityId: z.string().optional(),
  professionId: z.string().optional(),
});

type CustomerFormData = z.infer<typeof customerSchema>;

interface CustomerFormProps {
  initialData?: CustomerResponse; // If provided, we're editing
}

export function CustomerForm({ initialData }: CustomerFormProps) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const isEdit = !!initialData;

  const { data: cities, isLoading: citiesLoading } = useQuery({
    queryKey: ["cities"],
    queryFn: getCities,
  });

  const { data: professions, isLoading: professionsLoading } = useQuery({
    queryKey: ["professions"],
    queryFn: getProfessions,
  });

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<CustomerFormData>({
    resolver: zodResolver(customerSchema),
    defaultValues: initialData
      ? {
          firstName: initialData.firstName,
          lastName: initialData.lastName,
          nationalId: initialData.nationalId,
          email: initialData.email,
          phone: initialData.phone ?? "",
          birthDate: initialData.birthDate ?? "",
          address: initialData.address ?? "",
          cityId: initialData.cityId?.toString() ?? "",
          professionId: initialData.professionId?.toString() ?? "",
        }
      : {
          firstName: "",
          lastName: "",
          nationalId: "",
          email: "",
          phone: "",
          birthDate: "",
          address: "",
          cityId: "",
          professionId: "",
        },
  });

  const mutation = useMutation({
    mutationFn: (data: CustomerFormData) => {
      const payload: CustomerRequest = {
        firstName: data.firstName,
        lastName: data.lastName,
        nationalId: data.nationalId,
        email: data.email,
        phone: data.phone || undefined,
        birthDate: data.birthDate || undefined,
        address: data.address || undefined,
        cityId: data.cityId ? Number(data.cityId) : undefined,
        professionId: data.professionId ? Number(data.professionId) : undefined,
      };
      if (isEdit && initialData) {
        return updateCustomer(initialData.id, payload);
      }
      return createCustomer(payload);
    },
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ["customers"] });
      router.push(`/customers/${result.id}`);
    },
  });

  const onSubmit = (data: CustomerFormData) => {
    mutation.mutate(data);
  };

  if (citiesLoading || professionsLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-8 w-64" />
        <Card>
          <CardContent className="space-y-4 pt-6">
            {Array.from({ length: 6 }).map((_, i) => (
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
          title={isEdit ? "Edit Customer" : "New Customer"}
          description={isEdit ? "Update customer information" : "Create a new customer record"}
        />
      </div>

      {mutation.isError && (
        <ErrorAlert
          message={mutation.error instanceof Error ? mutation.error.message : "Failed to save customer"}
        />
      )}

      <form onSubmit={handleSubmit(onSubmit)}>
        <Card>
          <CardHeader>
            <CardTitle>Customer Information</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <FormField
                label="First Name"
                {...register("firstName")}
                error={errors.firstName?.message}
                placeholder="John"
              />
              <FormField
                label="Last Name"
                {...register("lastName")}
                error={errors.lastName?.message}
                placeholder="Doe"
              />
            </div>

            <FormField
              label="National ID (TCKN)"
              {...register("nationalId")}
              error={errors.nationalId?.message}
              placeholder="12345678901"
              maxLength={11}
            />

            <FormField
              label="Email"
              type="email"
              {...register("email")}
              error={errors.email?.message}
              placeholder="john.doe@example.com"
            />

            <FormField
              label="Phone"
              {...register("phone")}
              error={errors.phone?.message}
              placeholder="+90 555 123 4567"
            />

            <FormField
              label="Birth Date"
              type="date"
              {...register("birthDate")}
              error={errors.birthDate?.message}
            />

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              {/* City dropdown */}
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

              {/* Profession dropdown */}
              <div className="space-y-1.5">
                <label className="text-sm font-medium">Profession</label>
                <Select
                  value={watch("professionId") || undefined}
                  onValueChange={(value) => setValue("professionId", value ?? "")}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder="Select a profession" />
                  </SelectTrigger>
                  <SelectContent>
                    {professions?.map((prof) => (
                      <SelectItem key={prof.id} value={prof.id.toString()}>
                        {prof.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            <FormField
              label="Address"
              {...register("address")}
              error={errors.address?.message}
              placeholder="Full address"
            />

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
