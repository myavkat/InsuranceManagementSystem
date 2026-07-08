import { z } from "zod";

export const customerSchema = z.object({
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

export type CustomerFormData = z.infer<typeof customerSchema>;
