import { z } from "zod";

export const realEstateSchema = z.object({
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

export type RealEstateFormData = z.infer<typeof realEstateSchema>;
