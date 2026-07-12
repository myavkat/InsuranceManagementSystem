import { z } from "zod";

export const estimationSchema = z.object({
  customerId: z.string().min(1, "Customer is required"),
  insuranceId: z.string().min(1, "Insurance is required"),
  vehicleId: z.string().optional(),
  realEstateId: z.string().optional(),
});

export type EstimationFormData = z.infer<typeof estimationSchema>;
