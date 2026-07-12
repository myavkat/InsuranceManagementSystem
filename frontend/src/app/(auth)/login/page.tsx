"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { AlertCircle, Loader2 } from "lucide-react";

import { loginSchema, type LoginFormData } from "@/lib/schemas/auth";
import { login, validateToken } from "@/lib/api/auth";
import { useAuthStore, type UserInfo } from "@/lib/store/auth-store";
import { ApiError } from "@/lib/api/types";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { FormField } from "@/components/features/form-field";

/**
 * Decode a JWT access token payload (base64url-encoded JSON body).
 * Returns null if the token is malformed.
 */
function decodeJwtPayload(token: string): Record<string, unknown> | null {
  try {
    const parts = token.split(".");
    if (parts.length !== 3) return null;
    // JWTs use base64url encoding (RFC 7519 §2, RFC 4648 §5).
    // Convert to standard base64 so atob() can decode it.
    const base64 = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    // atob requires padding to a multiple of 4
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), "=");
    return JSON.parse(atob(padded));
  } catch {
    return null;
  }
}

/**
 * Build a UserInfo object from an access token by decoding its claims.
 * Expected claims: sub (userId), username, roles (string array).
 */
function userFromToken(token: string): UserInfo {
  const claims = decodeJwtPayload(token);
  return {
    userId: (claims?.sub as string) ?? "",
    username: (claims?.username as string) ?? "",
    email: (claims?.email as string) ?? "",
    roles: Array.isArray(claims?.roles) ? (claims.roles as string[]) : [],
  };
}

function setAuthCookie(token: string, expiresIn: number): void {
  // URL-encode the JWT to avoid any issues with special characters (=, +, /, etc.)
  // in the cookie value. Decoded on read in serverFetch or middleware if needed.
  document.cookie = `auth_token=${encodeURIComponent(token)}; path=/; max-age=${expiresIn}; SameSite=Lax; Secure`;
}

function clearAuthCookie(): void {
  document.cookie = "auth_token=; path=/; max-age=0; SameSite=Lax; Secure";
}

export default function LoginPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const authStore = useAuthStore();
  const [formError, setFormError] = useState<string | null>(null);

  const redirectTo = searchParams.get("redirect") || "/dashboard";
  const justRegistered = searchParams.get("registered") === "true";

  const form = useForm<LoginFormData>({
    resolver: zodResolver(loginSchema),
    defaultValues: { username: "", password: "" },
  });

  const loginMutation = useMutation({
    mutationFn: async (data: LoginFormData) => {
      const response = await login(data);

      // Decode user info from the JWT access token
      const user: UserInfo = {
        ...userFromToken(response.accessToken),
        email: (() => {
          const claims = decodeJwtPayload(response.accessToken);
          return (claims?.email as string) ?? "";
        })(),
      };

      // Persist to Zustand (which syncs to localStorage via persist middleware)
      authStore.login(
        response.accessToken,
        response.refreshToken,
        response.expiresIn,
        user
      );

      // Set auth cookie so middleware.ts and Server Components can use it
      setAuthCookie(response.accessToken, response.expiresIn);

      return { redirectTo };
    },
    onSuccess: ({ redirectTo }) => {
      router.push(redirectTo);
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
    // Clear previous errors when user re-submits
    setFormError(null);
    loginMutation.mutate(data);
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>Sign In</CardTitle>
        <CardDescription>
          Enter your credentials to access the system
        </CardDescription>
      </CardHeader>
      <CardContent>
        {/* Success message after registration */}
        {justRegistered && (
          <div className="mb-4 rounded-md border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-800 dark:border-green-800 dark:bg-green-950 dark:text-green-200">
            Account created successfully! Please sign in.
          </div>
        )}

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
            label="Password"
            type="password"
            placeholder="Enter your password"
            error={form.formState.errors.password?.message}
            {...form.register("password")}
          />

          <Button
            type="submit"
            className="w-full"
            disabled={loginMutation.isPending}
          >
            {loginMutation.isPending && (
              <Loader2 className="mr-2 size-4 animate-spin" />
            )}
            {loginMutation.isPending ? "Signing in..." : "Sign in"}
          </Button>
        </form>

        <p className="mt-4 text-center text-sm text-muted-foreground">
          Don&apos;t have an account?{" "}
          <Link
            href="/register"
            className="font-medium text-primary hover:underline"
          >
            Register
          </Link>
        </p>
      </CardContent>
    </Card>
  );
}
