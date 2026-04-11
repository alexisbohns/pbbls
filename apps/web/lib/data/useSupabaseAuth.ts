"use client"

import { useState, useEffect, useCallback } from "react"
import { createClient } from "@/lib/supabase/client"

// Singleton client — created once on first browser-side call.
let supabaseInstance: ReturnType<typeof createClient> | null = null

function getSupabase() {
  if (typeof window === "undefined") return null
  if (!supabaseInstance) supabaseInstance = createClient()
  return supabaseInstance
}
import type {
  Account,
  Profile,
  LoginInput,
  RegisterInput,
  UpdateProfileInput,
} from "@/lib/types"
import type { AuthContextValue } from "@/lib/data/auth-context"

/**
 * Manages Supabase auth state and exposes it in the shape expected by
 * AuthContext. Drop-in replacement for the old LocalProvider-based auth
 * logic in AuthProvider.
 *
 * During SSR/SSG the Supabase client is not created (no env vars available),
 * so all auth state is null/loading and actions are no-ops until the
 * component mounts in the browser.
 */
export function useSupabaseAuth(): AuthContextValue {

  const [user, setUser] = useState<Account | null>(null)
  const [profile, setProfile] = useState<Profile | null>(null)
  const [isLoading, setIsLoading] = useState(() => getSupabase() !== null)

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  /** Map a Supabase user to our Account type. */
  const toAccount = useCallback(
    (supabaseUser: { id: string; email?: string; created_at: string }): Account => ({
      id: supabaseUser.id,
      email: supabaseUser.email ?? "",
      created_at: supabaseUser.created_at,
    }),
    [],
  )

  /** Fetch the profile row for a given user ID. */
  const fetchProfile = useCallback(
    async (userId: string): Promise<Profile | null> => {
      const supabase = getSupabase()
      if (!supabase) return null

      const { data, error } = await supabase
        .from("profiles")
        .select("*")
        .eq("user_id", userId)
        .maybeSingle()

      if (error || !data) return null
      return data as Profile
    },
    [],
  )

  // -----------------------------------------------------------------------
  // Auth state listener
  // -----------------------------------------------------------------------

  useEffect(() => {
    const supabase = getSupabase()
    if (!supabase) return

    // Check for existing session on mount
    supabase.auth.getSession().then(async ({ data: { session } }) => {
      if (session?.user) {
        setUser(toAccount(session.user))
        const prof = await fetchProfile(session.user.id)
        setProfile(prof)
      }
      setIsLoading(false)
    })

    // Listen for auth state changes (login, logout, token refresh)
    const {
      data: { subscription },
    } = supabase.auth.onAuthStateChange(async (event, session) => {
      if (session?.user) {
        setUser(toAccount(session.user))
        const prof = await fetchProfile(session.user.id)
        setProfile(prof)
      } else {
        setUser(null)
        setProfile(null)
      }
    })

    return () => subscription.unsubscribe()
  }, [toAccount, fetchProfile])

  // -----------------------------------------------------------------------
  // Auth actions
  // -----------------------------------------------------------------------

  const login = useCallback(
    async (input: LoginInput) => {
      const supabase = getSupabase()
      if (!supabase) throw new Error("Supabase client not available")

      const { error } = await supabase.auth.signInWithPassword({
        email: input.email,
        password: input.password,
      })
      if (error) throw new Error(error.message)
    },
    [],
  )

  const register = useCallback(
    async (input: RegisterInput) => {
      const supabase = getSupabase()
      if (!supabase) throw new Error("Supabase client not available")

      const { error } = await supabase.auth.signUp({
        email: input.email,
        password: input.password,
        options: {
          data: {
            terms_accepted_at: input.terms_accepted ? new Date().toISOString() : null,
            privacy_accepted_at: input.privacy_accepted ? new Date().toISOString() : null,
          },
        },
      })
      if (error) throw new Error(error.message)
    },
    [],
  )

  const signInWithApple = useCallback(async () => {
    const supabase = getSupabase()
    if (!supabase) throw new Error("Supabase client not available")

    const { error } = await supabase.auth.signInWithOAuth({
      provider: "apple",
      options: {
        redirectTo: `${window.location.origin}/auth/callback`,
      },
    })
    if (error) throw new Error(error.message)
  }, [])

  const signInWithGoogle = useCallback(async () => {
    const supabase = getSupabase()
    if (!supabase) throw new Error("Supabase client not available")

    const { error } = await supabase.auth.signInWithOAuth({
      provider: "google",
      options: {
        redirectTo: `${window.location.origin}/auth/callback`,
      },
    })
    if (error) throw new Error(error.message)
  }, [])

  const logout = useCallback(async () => {
    const supabase = getSupabase()
    if (!supabase) throw new Error("Supabase client not available")

    const { error } = await supabase.auth.signOut()
    if (error) throw new Error(error.message)
  }, [])

  const updateProfile = useCallback(
    async (input: UpdateProfileInput): Promise<Profile> => {
      const supabase = getSupabase()
      if (!supabase) throw new Error("Supabase client not available")
      if (!user) throw new Error("Not authenticated")

      const { data, error } = await supabase
        .from("profiles")
        .update(input)
        .eq("user_id", user.id)
        .select()
        .single()

      if (error) throw new Error(error.message)

      const updated = data as Profile
      setProfile(updated)
      return updated
    },
    [user],
  )

  return {
    user,
    profile,
    isAuthenticated: user !== null,
    isLoading,
    login,
    register,
    signInWithApple,
    signInWithGoogle,
    logout,
    updateProfile,
  }
}
