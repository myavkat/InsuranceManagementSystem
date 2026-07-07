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
