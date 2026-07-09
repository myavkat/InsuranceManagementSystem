import { z } from "zod";

export const paymentSchema = z.object({
  cardNumber: z
    .string()
    .min(1, "Card number is required")
    .regex(/^\d{16}$/, "Card number must be exactly 16 digits"),
  cardHolder: z
    .string()
    .min(1, "Card holder name is required"),
  expiryMonth: z
    .string()
    .min(1, "Month is required")
    .regex(/^(0[1-9]|1[0-2])$/, "Must be a valid month (01-12)"),
  expiryYear: z
    .string()
    .min(1, "Year is required")
    .regex(/^\d{4}$/, "Must be a valid 4-digit year"),
  cvv: z
    .string()
    .min(1, "CVV is required")
    .regex(/^\d{3}$/, "CVV must be exactly 3 digits"),
});

export type PaymentFormData = z.infer<typeof paymentSchema>;
