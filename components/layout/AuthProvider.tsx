"use client"

import { useState, useEffect, useCallback } from "react"
import { AuthContext, type AuthContextValue } from "@/lib/data/auth-context"
import { useDataProvider } from "@/lib/data/provider-context"
import type {
  Account,
  Profile,
  LoginInput,
  RegisterInput,
  UpdateProfileInput,
} from "@/lib/types"

/**
 * Client-only wrapper that manages auth state (user, profile, session) and
 * exposes it to the React tree via AuthContext.
 *
 * Must be rendered inside <DataProvider> — it reads the provider instance
 * from DataContext to delegate all auth operations.
 *
 * On mount it rehydrates the session from localStorage (via the provider's
 * getSession / getAccount / getProfile methods) so returning users are
 * automatically logged in.
 */
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const { provider, setStore } = useDataProvider()

  const [user, setUser] = useState<Account | null>(null)
  const [profile, setProfile] = useState<Profile | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  // -------------------------------------------------------------------------
  // Session rehydration — runs once on mount.
  // -------------------------------------------------------------------------
  useEffect(() => {
    async function rehydrate() {
      const session = provider.getSession()
      if (session) {
        const [account, prof] = await Promise.all([
          provider.getAccount(),
          provider.getProfile(),
        ])
        setUser(account ?? null)
        setProfile(prof ?? null)
      }
      setIsLoading(false)
    }
    rehydrate()
  }, [provider])

  // -------------------------------------------------------------------------
  // Auth actions
  // -------------------------------------------------------------------------

  const login = useCallback(
    async (input: LoginInput) => {
      await provider.login(input)
      setStore(provider.reloadStore())
      const [account, prof] = await Promise.all([
        provider.getAccount(),
        provider.getProfile(),
      ])
      setUser(account ?? null)
      setProfile(prof ?? null)
    },
    [provider, setStore],
  )

  const register = useCallback(
    async (input: RegisterInput) => {
      await provider.register(input)
      setStore(provider.reloadStore())
      const [account, prof] = await Promise.all([
        provider.getAccount(),
        provider.getProfile(),
      ])
      setUser(account ?? null)
      setProfile(prof ?? null)
    },
    [provider, setStore],
  )

  const logout = useCallback(async () => {
    await provider.logout()
    setStore(provider.reloadStore())
    setUser(null)
    setProfile(null)
  }, [provider, setStore])

  const updateProfileFn = useCallback(
    async (input: UpdateProfileInput): Promise<Profile> => {
      const updated = await provider.updateProfile(input)
      setProfile(updated)
      return updated
    },
    [provider],
  )

  // -------------------------------------------------------------------------
  // Render
  // -------------------------------------------------------------------------

  const value: AuthContextValue = {
    user,
    profile,
    isAuthenticated: user !== null,
    isLoading,
    login,
    register,
    logout,
    updateProfile: updateProfileFn,
  }

  return (
    <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
  )
}
