"use client"

import { createContext, useContext } from "react"
import type {
  Account,
  Profile,
  LoginInput,
  RegisterInput,
  UpdateProfileInput,
} from "@/lib/types"

// ---------------------------------------------------------------------------
// Context value — exposes auth state and actions to the React tree.
// Separate from DataContext so auth concerns stay decoupled from content data.
// ---------------------------------------------------------------------------

export type AuthContextValue = {
  /** The logged-in account, or null when unauthenticated. */
  user: Account | null
  /** The logged-in user's profile, or null when unauthenticated. */
  profile: Profile | null
  /** Convenience flag — equivalent to `user !== null`. */
  isAuthenticated: boolean
  /** True while the initial session is being rehydrated from localStorage. */
  isLoading: boolean
  login(input: LoginInput): Promise<void>
  register(input: RegisterInput): Promise<void>
  logout(): Promise<void>
  updateProfile(input: UpdateProfileInput): Promise<Profile>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

/**
 * Consume the auth context.
 * Must be called inside a component tree wrapped by <AuthProvider>.
 */
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error("useAuth must be used within <AuthProvider>")
  return ctx
}
