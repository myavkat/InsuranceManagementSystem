import { z } from "zod";

export const estimationSchema = z.object({
  customerId: z.string().min(1, "Customer is required"),
  insuranceTypeId: z.string().min(1, "Insurance type is required"),
  vehicleId: z.string().optional(),
  realEstateId: z.string().optional(),
});

export type EstimationFormData = z.infer<typeof estimationSchema>;
