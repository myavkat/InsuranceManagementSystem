import { z } from "zod";

// --- Login Schema ---

export const loginSchema = z.object({
  username: z.string().min(3, "Username must be at least 3 characters"),
  password: z.string().min(1, "Password is required"),
});

export type LoginFormData = z.infer<typeof loginSchema>;

// --- Register Schema ---
// Uses Zod v4 .pipe() pattern for refinement validation

const registerBaseSchema = z.object({
  username: z.string().min(3, "Username must be at least 3 characters"),
  email: z.string().email("Invalid email address"),
  password: z.string().min(8, "Password must be at least 8 characters"),
  confirmPassword: z.string(),
});

const registerRefinementSchema = z
  .object({
    username: z.string(),
    email: z.string(),
    password: z.string(),
    confirmPassword: z.string(),
  })
  .refine(
    (data) => data.password === data.confirmPassword,
    { message: "Passwords do not match", path: ["confirmPassword"] }
  );

export const registerSchema = registerBaseSchema.pipe(registerRefinementSchema);

export type RegisterFormData = z.infer<typeof registerSchema>;
