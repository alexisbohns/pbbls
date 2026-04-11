"use client"

import { useState, useEffect, useCallback, useRef } from "react"
import { createClient } from "@/lib/supabase/client"
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
 */
export function useSupabaseAuth(): AuthContextValue {
  const supabase = useRef(createClient()).current

  const [user, setUser] = useState<Account | null>(null)
  const [profile, setProfile] = useState<Profile | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  /** Fetch the profile row for a given user ID. */
  const fetchProfile = useCallback(
    async (userId: string): Promise<Profile | null> => {
      const { data, error } = await supabase
        .from("profiles")
        .select("*")
        .eq("user_id", userId)
        .single()

      if (error || !data) return null
      return data as Profile
    },
    [supabase],
  )

  /** Map a Supabase user to our Account type. */
  const toAccount = useCallback(
    (supabaseUser: { id: string; email?: string; created_at: string }): Account => ({
      id: supabaseUser.id,
      email: supabaseUser.email ?? "",
      created_at: supabaseUser.created_at,
    }),
    [],
  )

  // -----------------------------------------------------------------------
  // Auth state listener
  // -----------------------------------------------------------------------

  useEffect(() => {
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
  }, [supabase, toAccount, fetchProfile])

  // -----------------------------------------------------------------------
  // Auth actions
  // -----------------------------------------------------------------------

  const login = useCallback(
    async (input: LoginInput) => {
      const { error } = await supabase.auth.signInWithPassword({
        email: input.email,
        password: input.password,
      })
      if (error) throw new Error(error.message)
    },
    [supabase],
  )

  const register = useCallback(
    async (input: RegisterInput) => {
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
    [supabase],
  )

  const signInWithApple = useCallback(async () => {
    const { error } = await supabase.auth.signInWithOAuth({
      provider: "apple",
      options: {
        redirectTo: `${window.location.origin}/auth/callback`,
      },
    })
    if (error) throw new Error(error.message)
  }, [supabase])

  const logout = useCallback(async () => {
    const { error } = await supabase.auth.signOut()
    if (error) throw new Error(error.message)
  }, [supabase])

  const updateProfile = useCallback(
    async (input: UpdateProfileInput): Promise<Profile> => {
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
    [supabase, user],
  )

  return {
    user,
    profile,
    isAuthenticated: user !== null,
    isLoading,
    login,
    register,
    signInWithApple,
    logout,
    updateProfile,
  }
}
