"use client"

import { useState, useEffect, useCallback } from "react"
import { createClient } from "@/lib/supabase/client"

import type {
  Account,
  Profile,
  LoginInput,
  RegisterInput,
  UpdateProfileInput,
} from "@/lib/types"
import type { AuthContextValue } from "@/lib/data/auth-context"

let supabaseInstance: ReturnType<typeof createClient> | null = null

function getSupabase() {
  if (typeof window === "undefined") return null
  if (!supabaseInstance) supabaseInstance = createClient()
  return supabaseInstance
}

export function useSupabaseAuth(): AuthContextValue {
  const [user, setUser] = useState<Account | null>(null)
  const [profile, setProfile] = useState<Profile | null>(null)
  // Always start as true on both server and client to avoid hydration mismatch.
  // The client-side effect sets it to false after session check.
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    const supabase = getSupabase()
    if (!supabase) return

    let cancelled = false

    // Use getUser() instead of getSession() — it validates the token with
    // the Supabase Auth server and refreshes if expired. getSession() only
    // reads from cookies without validation, which can return stale/invalid
    // tokens that cause 401 on subsequent API calls.
    supabase.auth.getUser().then(async ({ data: { user: supabaseUser } }) => {
      if (cancelled) return
      if (supabaseUser) {
        setUser({
          id: supabaseUser.id,
          email: supabaseUser.email ?? "",
          created_at: supabaseUser.created_at,
        })
        const { data } = await supabase
          .from("profiles")
          .select("*")
          .eq("user_id", supabaseUser.id)
          .maybeSingle()
        if (!cancelled) setProfile(data as Profile | null)
      }
      if (!cancelled) setIsLoading(false)
    })

    const {
      data: { subscription },
    } = supabase.auth.onAuthStateChange(async (event, session) => {
      if (event !== "SIGNED_IN" && event !== "SIGNED_OUT") return
      if (cancelled) return

      if (event === "SIGNED_OUT" || !session?.user) {
        setUser(null)
        setProfile(null)
        setIsLoading(false)
        return
      }

      setUser({
        id: session.user.id,
        email: session.user.email ?? "",
        created_at: session.user.created_at,
      })
      const { data } = await supabase
        .from("profiles")
        .select("*")
        .eq("user_id", session.user.id)
        .maybeSingle()
      if (!cancelled) {
        setProfile(data as Profile | null)
        setIsLoading(false)
      }
    })

    return () => {
      cancelled = true
      subscription.unsubscribe()
    }
  }, [])

  const login = useCallback(async (input: LoginInput) => {
    const supabase = getSupabase()
    if (!supabase) throw new Error("Supabase client not available")
    const { error } = await supabase.auth.signInWithPassword({
      email: input.email,
      password: input.password,
    })
    if (error) throw new Error(error.message)
  }, [])

  const register = useCallback(async (input: RegisterInput) => {
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
  }, [])

  const signInWithApple = useCallback(async () => {
    const supabase = getSupabase()
    if (!supabase) throw new Error("Supabase client not available")
    const { error } = await supabase.auth.signInWithOAuth({
      provider: "apple",
      options: { redirectTo: `${window.location.origin}/auth/callback` },
    })
    if (error) throw new Error(error.message)
  }, [])

  const signInWithGoogle = useCallback(async () => {
    const supabase = getSupabase()
    if (!supabase) throw new Error("Supabase client not available")
    const { error } = await supabase.auth.signInWithOAuth({
      provider: "google",
      options: { redirectTo: `${window.location.origin}/auth/callback` },
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
