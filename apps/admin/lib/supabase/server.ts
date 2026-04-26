import { createServerClient } from "@supabase/ssr"
import { cookies } from "next/headers"
import type { Database } from "@pbbls/supabase"

export async function createServerSupabaseClient() {
  const cookieStore = await cookies()
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL
  const key = process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY

  if (!url || !key) {
    throw new Error(
      "Missing NEXT_PUBLIC_SUPABASE_URL or NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY. " +
        "Copy apps/admin/.env.local.example to apps/admin/.env.local and fill in your values.",
    )
  }

  return createServerClient<Database>(url, key, {
    cookies: {
      getAll() {
        return cookieStore.getAll()
      },
      setAll(cookiesToSet) {
        try {
          for (const { name, value, options } of cookiesToSet) {
            cookieStore.set(name, value, options)
          }
        } catch (error) {
          // Server Component cookies are read-only; route handlers (auth/callback,
          // auth/signout) and server actions handle writes.
          console.warn("[supabase/server] setAll failed:", error)
        }
      },
    },
  })
}
