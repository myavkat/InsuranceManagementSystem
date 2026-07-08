import { NextRequest, NextResponse } from "next/server";

// Paths that do NOT require authentication — always allowed through
const PUBLIC_PATHS = ["/login", "/register"];

// API routes — allowed through for auth but still get the Authorization header
// set from the auth_token cookie so BFF handlers and Server Components can use it.
const API_PREFIX = "/api";

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const isApiRoute = pathname.startsWith(API_PREFIX);
  const isPublicPage = PUBLIC_PATHS.some((p) => pathname.startsWith(p));

  // Read auth_token cookie — middleware can access cookies directly.
  const rawToken = request.cookies.get("auth_token")?.value;

  // Build request headers with Authorization set from the cookie if available.
  const requestHeaders = new Headers(request.headers);
  if (rawToken) {
    requestHeaders.set("Authorization", `Bearer ${decodeURIComponent(rawToken)}`);
  }

  // API routes: never redirect (always return JSON, let Gateway handle 401).
  // Public pages: never redirect.
  if (isApiRoute || isPublicPage) {
    return NextResponse.next({
      request: { headers: requestHeaders },
    });
  }

  // Protected page routes: redirect to login if no auth_token.
  if (!rawToken) {
    const loginUrl = new URL("/login", request.url);
    loginUrl.searchParams.set("redirect", pathname);
    return NextResponse.redirect(loginUrl);
  }

  return NextResponse.next({
    request: { headers: requestHeaders },
  });
}

export const config = {
  matcher: [
    // Match all routes EXCEPT static assets
    "/((?!_next/static|_next/image|favicon.ico).*)",
  ],
};
