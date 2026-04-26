import { redirect } from "next/navigation"
import { createServerSupabaseClient } from "./server"

export type AdminUser = {
  id: string
  email: string
}

/**
 * Server-only. Redirects unauthenticated users to /login and
 * non-admin authed users to /403. Returns the admin user otherwise.
 *
 * Defense-in-depth: even if this gate is bypassed, RLS on the `logs`
 * table requires is_admin(auth.uid()) for any write or unpublished read.
 */
export async function requireAdmin(): Promise<AdminUser> {
  const supabase = await createServerSupabaseClient()

  const {
    data: { user },
    error: userError,
  } = await supabase.auth.getUser()

  if (userError) {
    console.error("[admin-guard] getUser failed:", userError.message)
  }

  if (!user) {
    redirect("/login")
  }

  const { data: isAdmin, error: rpcError } = await supabase.rpc("is_admin", {
    p_user_id: user.id,
  })

  if (rpcError) {
    console.error("[admin-guard] is_admin RPC failed:", rpcError.message)
    redirect("/403")
  }

  if (!isAdmin) {
    redirect("/403")
  }

  return { id: user.id, email: user.email ?? "" }
}
