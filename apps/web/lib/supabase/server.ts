import { createServerClient } from "@supabase/ssr"
import { cookies } from "next/headers"

export async function createServerSupabaseClient() {
  const cookieStore = await cookies()
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL
  const key = process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY

  if (!url || !key) {
    throw new Error(
      "Missing NEXT_PUBLIC_SUPABASE_URL or NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY. " +
        "Copy apps/web/.env.local.example to apps/web/.env.local and fill in your values.",
    )
  }

  return createServerClient(url, key, {
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
          // In Server Components the cookie store is read-only, so the
          // set call throws. This is expected and safe — the Route Handler
          // (e.g. /auth/callback) will handle the write.
          // Log a warning so cookie failures are visible in all environments.
          console.warn("[supabase/server] setAll failed:", error)
        }
      },
    },
  })
}
