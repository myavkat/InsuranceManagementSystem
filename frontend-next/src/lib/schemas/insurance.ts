import { z } from "zod";

export const insuranceSchema = z.object({
  name: z.string().min(1, "Name is required"),
  description: z.string().optional(),
  typeId: z.string().min(1, "Insurance type is required"),
  basePremium: z.string()
    .refine((v) => Number(v) > 0, "Must be a positive number"),
  isActive: z.boolean(),
});

export type InsuranceFormData = z.infer<typeof insuranceSchema>;
