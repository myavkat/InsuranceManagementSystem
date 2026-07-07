import { apiClient } from "./client";

// --- Types ---

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  tokenType: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export interface UserResponse {
  userId: string;
  username: string;
  email: string;
  roles: string[];
}

// --- API Functions ---

export async function login(credentials: LoginRequest): Promise<LoginResponse> {
  return apiClient<LoginResponse>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify(credentials),
  });
}

export async function register(data: RegisterRequest): Promise<UserResponse> {
  return apiClient<UserResponse>("/api/auth/register", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function validateToken(): Promise<{
  valid: boolean;
  userId: string;
  roles: string[];
}> {
  return apiClient("/api/auth/validate", { method: "POST" });
}
