import { NextRequest, NextResponse } from "next/server";

const GATEWAY_URL = process.env.NEXT_PUBLIC_GATEWAY_URL || "http://localhost:8080";

/**
 * Shared BFF proxy utility.
 *
 * Forwards an incoming Next.js route handler request to the API Gateway,
 * preserving the HTTP method, path segments, query string, body, and
 * relevant headers (Authorization, Content-Type).
 *
 * Returns a 502 JSON response when the Gateway is unreachable.
 */
export async function bffProxy(
  request: NextRequest,
  domain: string,
  pathSegments: string[],
): Promise<NextResponse> {
  // Build the target path: /api/{domain}[/{pathSegments...}]
  const pathSuffix = pathSegments.length > 0 ? `/${pathSegments.join("/")}` : "";
  const targetUrl = `${GATEWAY_URL}/api/${domain}${pathSuffix}`;

  // Preserve query string
  const searchParams = request.nextUrl.searchParams.toString();
  const fullUrl = searchParams ? `${targetUrl}?${searchParams}` : targetUrl;

  // Forward relevant headers
  const headers: Record<string, string> = {};
  const authHeader = request.headers.get("authorization");
  if (authHeader) headers["Authorization"] = authHeader;
  const contentType = request.headers.get("content-type");
  if (contentType) headers["Content-Type"] = contentType;

  // Read request body (for POST/PUT/PATCH)
  let body: string | undefined;
  if (["POST", "PUT", "PATCH"].includes(request.method)) {
    try {
      body = await request.text();
    } catch {
      body = undefined;
    }
  }

  try {
    const response = await fetch(fullUrl, {
      method: request.method,
      headers,
      body,
    });

    const data = await response.json();
    return NextResponse.json(data, { status: response.status });
  } catch {
    return NextResponse.json(
      {
        success: false,
        message: "Gateway unreachable. Please ensure the API Gateway is running.",
        data: null,
      },
      { status: 502 },
    );
  }
}
