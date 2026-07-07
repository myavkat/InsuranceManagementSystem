# 07 — Sprint 6: API Client Layer

## Status: NOT STARTED

## Objective

Build the client-side API layer: TypeScript types for the API response envelope, a base fetch wrapper that attaches JWT tokens, handles 401 responses (attempts token refresh, redirects to login on failure), and per-domain API modules for each backend domain.

## Prerequisites

- **Plan 04 must be complete** (root layout, env vars)
- **Plan 05 must be complete** (auth-store exists with `useAuthStore` exporting `accessToken`, `refreshToken`, `expiresAt`, `login`, `logout`, `setAccessToken`)
- Read these files before starting:
  - `docs/outlines/05_NEXTJS_FRONTEND.md` — BFF pattern, data flow (Section 4)
  - `docs/outlines/06_API_GATEWAY_AUTH.md` — Gateway routes, JWT format, token refresh endpoint
  - `frontend-next/src/lib/store/auth-store.ts` — auth store API (you will import `useAuthStore`)
  - `frontend-next/AGENTS.md` — Next.js 16 docs reference
  - `frontend-next/.env.local` — `NEXT_PUBLIC_GATEWAY_URL` value

## Architecture Notes

- **The API client runs in the browser** (Client Components call these functions). Server Components call BFF route handlers directly (Plan 08), not this client.
- **Token refresh flow:** When a 401 is received, attempt `POST /api/auth/refresh` with the refresh token. If that succeeds, retry the original request. If refresh fails, clear auth state and redirect to `/login`.
- **ApiResponse<T> envelope:** The API Gateway wraps all responses in `{ success: boolean, data?: T, message?: string, errors?: Record<string, string[]> }`. The client unwraps this — callers receive `T` directly on success and a thrown `ApiError` on failure.
- **No axios.** Use the native `fetch` API. All dependencies are already in `package.json` — no new packages needed.

## Steps

### Step 1: Create TypeScript types for the API layer

**File to CREATE:** `frontend-next/src/lib/api/types.ts`

```ts
// Generic API response envelope from the API Gateway.
// All backend responses are wrapped in this structure.
export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  message?: string;
  errors?: Record<string, string[]>;
  timestamp?: string;
}

// Custom error class for API errors.
// Thrown when the API returns success: false or a non-2xx status.
export class ApiError extends Error {
  status: number;
  errors?: Record<string, string[]>;

  constructor(status: number, message: string, errors?: Record<string, string[]>) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.errors = errors;
  }
}

// Paginated response wrapper (used by list endpoints)
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number; // current page (0-indexed)
  size: number; // page size
}
```

**Why `ApiError` extends `Error`:** Callers can use `instanceof ApiError` to distinguish API errors from network failures. The `status` field lets callers branch on specific HTTP codes.

### Step 2: Create the base API client

**File to CREATE:** `frontend-next/src/lib/api/client.ts`

This is the core module. It's a plain TypeScript module (NOT a React component — no `"use client"` directive needed), but it imports from the Zustand store which IS client-side. This is intentional — the API client is only called from Client Components.

```ts
import { useAuthStore } from "@/lib/store/auth-store";
import { ApiError, type ApiResponse } from "./types";

const GATEWAY_URL = process.env.NEXT_PUBLIC_GATEWAY_URL || "http://localhost:8080";

// Track whether a token refresh is already in-flight.
// This prevents multiple concurrent 401s from each trying to refresh independently.
let refreshPromise: Promise<boolean> | null = null;

/**
 * Attempt to refresh the access token using the stored refresh token.
 * Returns true if refresh succeeded, false otherwise.
 * Only one refresh runs at a time — concurrent callers share the same promise.
 */
async function attemptTokenRefresh(): Promise<boolean> {
  const { refreshToken, setAccessToken, logout } = useAuthStore.getState();

  if (!refreshToken) {
    logout();
    return false;
  }

  try {
    const response = await fetch(`${GATEWAY_URL}/api/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });

    if (!response.ok) {
      logout();
      return false;
    }

    const body: ApiResponse<{
      accessToken: string;
      refreshToken: string;
      expiresIn: number;
    }> = await response.json();

    if (!body.success || !body.data) {
      logout();
      return false;
    }

    setAccessToken(body.data.accessToken, body.data.expiresIn);
    return true;
  } catch {
    logout();
    return false;
  }
}

/**
 * Core fetch wrapper. Prepends GATEWAY_URL, attaches JWT Bearer token,
 * handles 401 → refresh → retry, parses ApiResponse<T> envelope.
 *
 * @param path - API path WITHOUT the gateway URL (e.g., "/api/customers")
 * @param options - Standard RequestInit options
 * @returns The unwrapped response data (T) on success
 * @throws ApiError on non-2xx or success: false responses
 */
export async function apiClient<T>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const url = `${GATEWAY_URL}${path}`;

  // Attach JWT if we have one
  const { accessToken } = useAuthStore.getState();
  const headers = new Headers(options.headers);

  if (accessToken) {
    headers.set("Authorization", `Bearer ${accessToken}`);
  }

  if (!headers.has("Content-Type") && !(options.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }

  let response = await fetch(url, {
    ...options,
    headers,
  });

  // Handle 401 — attempt token refresh and retry once
  if (response.status === 401) {
    // Deduplicate concurrent refresh attempts
    if (!refreshPromise) {
      refreshPromise = attemptTokenRefresh().finally(() => {
        refreshPromise = null;
      });
    }

    const refreshed = await refreshPromise;

    if (refreshed) {
      // Retry with new token
      const newToken = useAuthStore.getState().accessToken;
      if (newToken) {
        headers.set("Authorization", `Bearer ${newToken}`);
      }
      response = await fetch(url, {
        ...options,
        headers,
      });
    } else {
      // Refresh failed — redirect to login
      if (typeof window !== "undefined") {
        window.location.href = "/login";
      }
      throw new ApiError(401, "Session expired. Please sign in again.");
    }
  }

  // Parse JSON body
  let body: ApiResponse<T>;
  try {
    body = await response.json();
  } catch {
    throw new ApiError(
      response.status,
      `Request failed with status ${response.status}`
    );
  }

  // Check business-logic success flag
  if (!response.ok || !body.success) {
    throw new ApiError(
      response.status,
      body.message || `Request failed with status ${response.status}`,
      body.errors
    );
  }

  return body.data as T;
}
```

**Key design decisions:**
- `useAuthStore.getState()` is used instead of the React hook. This file is a plain module (not a React component), so hooks are disallowed. `getState()` is Zustand's escape hatch for reading state outside React.
- `refreshPromise` deduplication: if three requests all get 401 simultaneously, only one refresh call is made. The others wait for it and use the new token.
- After failed refresh: redirect to `/login` via `window.location.href`. This is a hard navigation (not client-side) that clears all state.
- `Content-Type: application/json` is set by default unless the body is `FormData` (file uploads).

### Step 3: Create per-domain API modules

Each module follows the same pattern: import `apiClient`, export typed functions for each endpoint.

**File to CREATE:** `frontend-next/src/lib/api/auth.ts`

```ts
import { apiClient } from "./client";
import type { PageResponse } from "./types";

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
```

**File to CREATE:** `frontend-next/src/lib/api/customers.ts`

```ts
import { apiClient } from "./client";
import type { PageResponse } from "./types";

export interface CustomerResponse {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  birthDate?: string;
  createdAt: string;
}

export interface CustomerRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  birthDate?: string;
}

export async function getCustomers(
  page = 0,
  size = 20,
  search?: string
): Promise<PageResponse<CustomerResponse>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (search) params.set("search", search);
  return apiClient<PageResponse<CustomerResponse>>(
    `/api/customers?${params.toString()}`
  );
}

export async function getCustomer(id: string): Promise<CustomerResponse> {
  return apiClient<CustomerResponse>(`/api/customers/${id}`);
}

export async function createCustomer(
  data: CustomerRequest
): Promise<CustomerResponse> {
  return apiClient<CustomerResponse>("/api/customers", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function updateCustomer(
  id: string,
  data: CustomerRequest
): Promise<CustomerResponse> {
  return apiClient<CustomerResponse>(`/api/customers/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
}

export async function deleteCustomer(id: string): Promise<void> {
  return apiClient<void>(`/api/customers/${id}`, { method: "DELETE" });
}
```

**File to CREATE:** `frontend-next/src/lib/api/insurances.ts`

```ts
import { apiClient } from "./client";
import type { PageResponse } from "./types";

export interface InsuranceResponse {
  id: string;
  name: string;
  description?: string;
  typeId: number;
  typeName?: string;
  basePremium: number;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export async function getInsurances(
  page = 0,
  size = 20,
  typeId?: number,
  search?: string
): Promise<PageResponse<InsuranceResponse>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (typeId != null) params.set("typeId", String(typeId));
  if (search) params.set("search", search);
  return apiClient<PageResponse<InsuranceResponse>>(
    `/api/insurances?${params.toString()}`
  );
}

export async function getInsurance(id: string): Promise<InsuranceResponse> {
  return apiClient<InsuranceResponse>(`/api/insurances/${id}`);
}
```

**File to CREATE:** `frontend-next/src/lib/api/estimations.ts`

```ts
import { apiClient } from "./client";
import type { PageResponse } from "./types";

export interface EstimationResponse {
  id: string;
  customerId: string;
  vehicleId?: string;
  realEstateId?: string;
  insuranceTypeId: number;
  premium?: number;
  status: string;
  createdAt: string;
}

export interface EstimationRequest {
  customerId: string;
  vehicleId?: string;
  realEstateId?: string;
  insuranceTypeId: number;
}

export async function getEstimations(
  page = 0,
  size = 20
): Promise<PageResponse<EstimationResponse>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return apiClient<PageResponse<EstimationResponse>>(
    `/api/estimations?${params.toString()}`
  );
}

export async function getEstimation(
  id: string
): Promise<EstimationResponse> {
  return apiClient<EstimationResponse>(`/api/estimations/${id}`);
}

export async function createEstimation(
  data: EstimationRequest
): Promise<EstimationResponse> {
  return apiClient<EstimationResponse>("/api/estimations", {
    method: "POST",
    body: JSON.stringify(data),
  });
}
```

**File to CREATE:** `frontend-next/src/lib/api/vehicles.ts`

```ts
import { apiClient } from "./client";
import type { PageResponse } from "./types";

export interface VehicleResponse {
  id: string;
  plate?: string;
  brand?: string;
  model?: string;
  year?: number;
  engineType?: string;
  fuelType?: string;
  customerId?: string;
}

export async function getVehicles(
  page = 0,
  size = 20
): Promise<PageResponse<VehicleResponse>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return apiClient<PageResponse<VehicleResponse>>(
    `/api/vehicles?${params.toString()}`
  );
}

export async function getVehicle(id: string): Promise<VehicleResponse> {
  return apiClient<VehicleResponse>(`/api/vehicles/${id}`);
}
```

### Step 4: Create an API barrel export

**File to CREATE:** `frontend-next/src/lib/api/index.ts`

```ts
export { apiClient } from "./client";
export { ApiError, type ApiResponse, type PageResponse } from "./types";

// Re-export all domain modules so consumers can import from a single path:
// import { authApi, customersApi } from "@/lib/api";
export * as authApi from "./auth";
export * as customersApi from "./customers";
export * as insurancesApi from "./insurances";
export * as estimationsApi from "./estimations";
export * as vehiclesApi from "./vehicles";
```

### Step 5: Verify build

Run from repo root:

```bash
cd frontend-next && npm run build
```

Common issues:
- If the build complains about `process.env.NEXT_PUBLIC_GATEWAY_URL`, ensure the Next.js types are loaded. This should be fine — Next.js automatically provides types for `process.env.NEXT_PUBLIC_*`.
- If the build warns about unused imports, remove them.

### Step 6: Verify lint

Run from repo root:

```bash
cd frontend-next && npm run lint
```

Fix any lint errors before marking this plan complete.

## Acceptance Criteria

- [ ] `frontend-next/src/lib/api/types.ts` exists with `ApiResponse<T>`, `ApiError`, and `PageResponse<T>`
- [ ] `frontend-next/src/lib/api/client.ts` exists with `apiClient<T>()` function
- [ ] `apiClient` prepends `GATEWAY_URL` to all requests
- [ ] `apiClient` attaches `Authorization: Bearer <token>` header from auth-store
- [ ] `apiClient` handles 401 by attempting `/api/auth/refresh` and retrying once
- [ ] `apiClient` deduplicates concurrent refresh attempts (single in-flight promise)
- [ ] `apiClient` redirects to `/login` on refresh failure
- [ ] `apiClient` parses `ApiResponse<T>` envelope and throws `ApiError` on failure
- [ ] `frontend-next/src/lib/api/auth.ts` exists with `login()`, `register()`, `validateToken()`
- [ ] `frontend-next/src/lib/api/customers.ts` exists with CRUD functions
- [ ] `frontend-next/src/lib/api/insurances.ts` exists with list/get functions
- [ ] `frontend-next/src/lib/api/estimations.ts` exists with list/get/create functions
- [ ] `frontend-next/src/lib/api/vehicles.ts` exists with list/get functions
- [ ] `frontend-next/src/lib/api/index.ts` exists with barrel re-exports
- [ ] `npm run build` passes
- [ ] `npm run lint` passes
