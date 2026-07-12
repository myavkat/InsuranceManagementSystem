import { z } from "zod";

export const vehicleSchema = z.object({
  plate: z.string()
    .regex(/^\d{2}\s?[A-Z]{1,3}\s?\d{2,4}(\s?[A-Z]{2})?$/, "Invalid Turkish plate format (e.g., 34 ABC 1234)"),
  chassisNumber: z.string()
    .min(17, "Chassis number must be 17 characters")
    .max(17, "Chassis number must be 17 characters")
    .optional()
    .or(z.literal("")),
  licenseFirstDate: z.string().optional(),
  carBrandId: z.string().optional(),
  carModelId: z.string().optional(),
  carEngineId: z.string().optional(),
  carFuelTypeId: z.string().optional(),
  carTypeId: z.string().optional(),
  carPackageId: z.string().optional(),
  customerId: z.string().min(1, "Customer is required"),
});

export type VehicleFormData = z.infer<typeof vehicleSchema>;
