import { NextResponse, type NextRequest } from "next/server";
import { updateSession } from "@/lib/supabase/middleware";
import {
  publicRoutes,
  authRoutes,
  shouldAllowRouteAccess,
} from "@/lib/middleware/route-guards";
import type { UserRole } from "@/types/api";

export async function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // Update session and get user
  const { user, supabaseResponse } = await updateSession(request);

  // Extract user role from user object
  const userRole = user?.user_metadata?.role as UserRole | undefined;

  // Check if the current route is public
  const isPublicRoute = publicRoutes.some((route) =>
    pathname.startsWith(route)
  );

  // Check if the current route is an auth route (login, etc.)
  const isAuthRoute = authRoutes.some((route) => pathname.startsWith(route));

  // If user is not authenticated and trying to access protected route
  if (!user && !isPublicRoute) {
    const loginUrl = new URL("/login", request.url);
    loginUrl.searchParams.set("redirect", pathname);
    return NextResponse.redirect(loginUrl);
  }

  // If user is authenticated and trying to access auth routes
  if (user && isAuthRoute) {
    // Redirect to appropriate default route based on role
    const defaultRoute = userRole === "EMPLOYEE" ? "/storage" : "/";
    return NextResponse.redirect(new URL(defaultRoute, request.url));
  }

  // If user is authenticated, check permission-based access
  if (user && !isPublicRoute) {
    const hasAccess = shouldAllowRouteAccess(pathname, userRole);

    if (!hasAccess) {
      // User lacks permission to access this route
      // Redirect to a safe default based on their role
      const defaultRoute = userRole === "EMPLOYEE" ? "/storage" : "/";
      const redirectUrl = new URL(defaultRoute, request.url);

      // Create response with redirect
      const response = NextResponse.redirect(redirectUrl);

      // Set a cookie to display error toast on the redirected page
      response.cookies.set("permission_denied", "true", {
        path: "/",
        maxAge: 10, // Cookie expires after 10 seconds
        httpOnly: false, // Allow JavaScript to read this cookie
        sameSite: "lax",
      });

      return response;
    }
  }

  return supabaseResponse;
}

export const config = {
  matcher: [
    /*
     * Match all request paths except for the ones starting with:
     * - _next/static (static files)
     * - _next/image (image optimization files)
     * - favicon.ico (favicon file)
     * - public folder
     * - api routes (handled separately)
     */
    "/((?!_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp)$|api).*)",
  ],
};
