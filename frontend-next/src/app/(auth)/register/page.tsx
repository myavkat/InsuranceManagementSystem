"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { AlertCircle, Loader2 } from "lucide-react";

import { registerSchema, type RegisterFormData } from "@/lib/schemas/auth";
import { register } from "@/lib/api/auth";
import { ApiError } from "@/lib/api/types";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { FormField } from "@/components/features/form-field";

export default function RegisterPage() {
  const router = useRouter();
  const [formError, setFormError] = useState<string | null>(null);

  const form = useForm<RegisterFormData>({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      username: "",
      email: "",
      password: "",
      confirmPassword: "",
    },
  });

  const registerMutation = useMutation({
    mutationFn: (data: RegisterFormData) => {
      // Only send the fields the backend expects (no confirmPassword)
      return register({
        username: data.username,
        email: data.email,
        password: data.password,
      });
    },
    onSuccess: () => {
      router.push("/login?registered=true");
    },
    onError: (error) => {
      if (error instanceof ApiError) {
        setFormError(error.message);
      } else {
        setFormError("An unexpected error occurred. Please try again.");
      }
    },
  });

  const onSubmit = form.handleSubmit((data) => {
    setFormError(null);
    registerMutation.mutate(data);
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>Create Account</CardTitle>
        <CardDescription>
          Register a new account to get started
        </CardDescription>
      </CardHeader>
      <CardContent>
        {/* API-level error alert */}
        {formError && (
          <div
            className="mb-4 flex items-start gap-2 rounded-md border border-destructive/50 bg-destructive/10 px-4 py-3 text-sm text-destructive"
            role="alert"
          >
            <AlertCircle className="mt-0.5 size-4 shrink-0" />
            <span>{formError}</span>
          </div>
        )}

        <form onSubmit={onSubmit} className="space-y-4">
          <FormField
            label="Username"
            placeholder="Enter your username"
            error={form.formState.errors.username?.message}
            {...form.register("username")}
          />

          <FormField
            label="Email"
            type="email"
            placeholder="Enter your email"
            error={form.formState.errors.email?.message}
            {...form.register("email")}
          />

          <FormField
            label="Password"
            type="password"
            placeholder="Enter your password"
            error={form.formState.errors.password?.message}
            {...form.register("password")}
          />

          <FormField
            label="Confirm Password"
            type="password"
            placeholder="Confirm your password"
            error={form.formState.errors.confirmPassword?.message}
            {...form.register("confirmPassword")}
          />

          <Button
            type="submit"
            className="w-full"
            disabled={registerMutation.isPending}
          >
            {registerMutation.isPending && (
              <Loader2 className="mr-2 size-4 animate-spin" />
            )}
            {registerMutation.isPending
              ? "Creating account..."
              : "Create account"}
          </Button>
        </form>

        <p className="mt-4 text-center text-sm text-muted-foreground">
          Already have an account?{" "}
          <Link
            href="/login"
            className="font-medium text-primary hover:underline"
          >
            Sign in
          </Link>
        </p>
      </CardContent>
    </Card>
  );
}
