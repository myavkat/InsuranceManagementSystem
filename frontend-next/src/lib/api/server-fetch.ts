import { headers } from "next/headers";

const GATEWAY_URL = process.env.NEXT_PUBLIC_GATEWAY_URL || "http://localhost:8080";

/**
 * Fetch data from the API Gateway directly.
 * For use in Server Components only.
 *
 * Next.js 16 guidance: Server Components should fetch data directly from its
 * source (the API Gateway), NOT via Route Handlers (BFF). Self-referencing
 * fetch calls to the Next.js dev server are unreliable during development.
 *
 * This utility reads the Authorization header from the incoming request
 * (set by middleware.ts from the auth_token cookie) and forwards it to
 * the API Gateway.
 *
 * @param path - API path (e.g., "/api/customers?page=0&size=20")
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
  const headerStore = await headers();
  const requestHeaders: Record<string, string> = {
    "Content-Type": "application/json",
  };

  // Read the Authorization header set by middleware.ts from the auth_token cookie.
  // This avoids any cookie encoding/decoding issues and keeps the token in one place.
  const authHeader = headerStore.get("authorization");
  if (authHeader) {
    requestHeaders["Authorization"] = authHeader;
  }

  const url = `${GATEWAY_URL}${path}`;
  const res = await fetch(url, {
    headers: requestHeaders,
    next: {
      revalidate: options.revalidate,
      tags: options.tags,
    },
    cache: options.cache,
  });

  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(
      body.message || `Server fetch failed: ${res.status} ${res.statusText} for ${path}`,
    );
  }

  const body = await res.json();

  if (!body.success) {
    throw new Error(body.message || `API error for ${path}`);
  }

  return body.data as T;
}
