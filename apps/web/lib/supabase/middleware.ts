import { createServerClient } from "@supabase/ssr"
import { NextResponse, type NextRequest } from "next/server"

/**
 * Refreshes the Supabase auth session on every request.
 *
 * This is essential for Supabase SSR: without it, the JWT stored in cookies
 * expires (default 1 h) and all authenticated requests start returning 401.
 *
 * The proxy achieves three things:
 * 1. Refreshes the auth token (via getClaims / getUser).
 * 2. Passes the refreshed token to Server Components through request cookies.
 * 3. Passes the refreshed token to the browser through response cookies.
 */
export async function updateSession(request: NextRequest) {
  let supabaseResponse = NextResponse.next({ request })

  const supabase = createServerClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY!,
    {
      cookies: {
        getAll() {
          return request.cookies.getAll()
        },
        setAll(cookiesToSet) {
          cookiesToSet.forEach(({ name, value }) =>
            request.cookies.set(name, value),
          )
          supabaseResponse = NextResponse.next({ request })
          cookiesToSet.forEach(({ name, value, options }) =>
            supabaseResponse.cookies.set(name, value, options),
          )
        },
      },
    },
  )

  // IMPORTANT: Do not add code between createServerClient and getUser().
  // A simple mistake could cause users to be randomly logged out.

  // getUser() sends a request to the Supabase Auth server every time to
  // revalidate the Auth token. Never trust getSession() here — it only
  // reads from cookies without validation.
  await supabase.auth.getUser()

  return supabaseResponse
}
