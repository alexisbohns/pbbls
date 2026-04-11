"use client"

import { AuthContext } from "@/lib/data/auth-context"
import { useSupabaseAuth } from "@/lib/data/useSupabaseAuth"

/**
 * Client-only wrapper that manages auth state via Supabase and exposes it
 * to the React tree via AuthContext.
 *
 * Uses the useSupabaseAuth hook which handles session rehydration,
 * auth state changes, and profile fetching.
 */
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const auth = useSupabaseAuth()

  return (
    <AuthContext.Provider value={auth}>{children}</AuthContext.Provider>
  )
}
