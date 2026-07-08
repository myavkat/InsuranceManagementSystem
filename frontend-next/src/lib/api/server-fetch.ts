import { cookies } from "next/headers";

const APP_URL = process.env.NEXT_PUBLIC_APP_URL || "http://localhost:3000";

/**
 * Fetch data from the Next.js BFF route handler.
 * For use in Server Components only.
 *
 * This utility:
 * 1. Reads the auth_token cookie (set by the auth flow)
 * 2. Passes it as a Bearer token to the BFF route handler
 * 3. Parses the ApiResponse envelope and returns the data payload
 *
 * @param path - BFF API path (e.g., "/api/customers?page=0&size=20")
 * @param options - Cache and revalidation options
 */
export async function serverFetch<T>(
  path: string,
  options: {
    cache?: RequestCache;
    revalidate?: number | false;
    tags?: string[];
  } = {},
): Promise<T> {
  const cookieStore = await cookies();
  const headers: Record<string, string> = {};

  // Forward all cookies (including auth_token) to the BFF route handler.
  // The BFF proxy reads the Authorization header, so we also derive
  // the Bearer token from the auth_token cookie below.
  headers["Cookie"] = cookieStore.toString();

  // Forward auth_token cookie as Authorization Bearer header.
  // The bffProxy utility reads the Authorization header to authenticate
  // proxied requests to the API Gateway.
  const authToken = cookieStore.get("auth_token")?.value;
  if (authToken) {
    headers["Authorization"] = `Bearer ${authToken}`;
  }

  const url = `${APP_URL}${path}`;
  const res = await fetch(url, {
    headers,
    next: {
      revalidate: options.revalidate,
      tags: options.tags,
    },
    cache: options.cache,
  });

  if (!res.ok) {
    throw new Error(
      `Server fetch failed: ${res.status} ${res.statusText} for ${path}`,
    );
  }

  const body = await res.json();

  if (!body.success) {
    throw new Error(body.message || `API error for ${path}`);
  }

  return body.data as T;
}
