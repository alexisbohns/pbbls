"use client"

import { useState, useEffect, useCallback, useRef } from "react"
import { createClient } from "@/lib/supabase/client"
import { withTimeout } from "@/lib/utils/with-timeout"

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

  // Dev-only watchdog: warns if auth stays in loading state too long,
  // which indicates a hang (deadlock, network issue, missing callback).
  const isLoadingRef = useRef(true)
  useEffect(() => {
    isLoadingRef.current = isLoading
  }, [isLoading])
  useEffect(() => {
    if (process.env.NODE_ENV !== "development") return
    const id = setTimeout(() => {
      if (isLoadingRef.current) {
        console.warn(
          "[auth] still loading 5s after mount — possible deadlock or network hang. " +
          "Check: is onAuthStateChange firing? Is the Supabase URL reachable?",
        )
      }
    }, 5000)
    return () => clearTimeout(id)
  }, [])

  useEffect(() => {
    const supabase = getSupabase()
    if (!supabase) return

    let cancelled = false

    // onAuthStateChange is the primary driver of auth state.
    // It fires INITIAL_SESSION on mount (with current session from cookies)
    // and SIGNED_IN / SIGNED_OUT on auth changes.
    //
    // CRITICAL: This callback must be synchronous. The Supabase client holds
    // an internal lock during initialization and waits for async callbacks
    // to complete. If we await a database call here, the database call waits
    // for the lock → deadlock. Profile fetching is fire-and-forget.
    const {
      data: { subscription },
    } = supabase.auth.onAuthStateChange((event, session) => {
      if (cancelled) return

      if (event === "SIGNED_OUT" || !session?.user) {
        setUser(null)
        setProfile(null)
        setIsLoading(false)
        return
      }

      // Set auth state synchronously — unblocks rendering immediately
      setUser({
        id: session.user.id,
        email: session.user.email ?? "",
        created_at: session.user.created_at,
      })
      setIsLoading(false)

      // Profile fetch is fire-and-forget — must NOT block this callback
      // or it deadlocks the Supabase client's internal auth lock.
      withTimeout(
        supabase
          .from("profiles")
          .select("*")
          .eq("user_id", session.user.id)
          .maybeSingle(),
        10000,
        "profile fetch",
      ).then(({ data }) => {
        if (!cancelled) setProfile(data as Profile | null)
      }).catch((err) => {
        console.warn("[auth] profile fetch failed:", (err as Error).message)
      })
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

      const { data, error } = await withTimeout(
        supabase
          .from("profiles")
          .update(input)
          .eq("user_id", user.id)
          .select()
          .maybeSingle(),
        10000,
        "profile update",
      )
      if (error) throw new Error(error.message)

      let updated: Profile
      if (data) {
        updated = data as Profile
      } else {
        // Profile row does not exist — create it with the provided fields
        const { data: inserted, error: insertError } = await withTimeout(
          supabase
            .from("profiles")
            .insert({ user_id: user.id, display_name: "Pebbler", ...input })
            .select()
            .single(),
          10000,
          "profile insert",
        )
        if (insertError) throw new Error(insertError.message)
        updated = inserted as Profile
      }

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
