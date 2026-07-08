import { NextRequest, NextResponse } from "next/server";

// Paths that do NOT require authentication
const PUBLIC_PATHS = ["/login", "/register", "/api"];

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // Allow public paths through
  if (PUBLIC_PATHS.some((p) => pathname.startsWith(p))) {
    return NextResponse.next();
  }

  // Check for auth token in cookies.
  // The auth_token cookie is set client-side after successful login.
  // We cannot read localStorage in middleware (it runs on the server),
  // so this dual cookie + Zustand approach bridges the gap.
  const token = request.cookies.get("auth_token")?.value;

  if (!token) {
    const loginUrl = new URL("/login", request.url);
    loginUrl.searchParams.set("redirect", pathname);
    return NextResponse.redirect(loginUrl);
  }

  return NextResponse.next();
}

export const config = {
  matcher: [
    // Match all routes EXCEPT static assets and public pages
    "/((?!_next/static|_next/image|favicon.ico|login|register|api).*)",
  ],
};
