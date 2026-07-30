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
  /** True while the initial session is being rehydrated. */
  isLoading: boolean
  /**
   * True while the profile row is being fetched after the session is known.
   * Profile fetching is fire-and-forget (to avoid a Supabase auth deadlock),
   * so callers that key off `profile` must wait on this flag too — otherwise
   * they'll see a transient `profile === null` and treat it as "no profile".
   */
  isProfileLoading: boolean
  login(input: LoginInput): Promise<void>
  register(input: RegisterInput): Promise<void>
  signInWithApple(): Promise<void>
  signInWithGoogle(): Promise<void>
  logout(): Promise<void>
  updateProfile(input: UpdateProfileInput): Promise<Profile>
  /**
   * Claim, change, or release (null) the public handle via the `set_handle`
   * RPC. Rejections carry a stable code in the error message —
   * `invalid_handle`, `handle_taken`, `handle_reserved`, or `not_found` when
   * the caller has no profile row. Callers must also expect transport
   * failures (timeout, network), which carry no code. Releasing the handle
   * also flips `public_profile` off server-side (DB invariant).
   */
  setHandle(handle: string | null): Promise<string | null>
  /** Change the signed-in user's password (email accounts). */
  updatePassword(password: string): Promise<void>
  /**
   * Permanently delete the signed-in user's account via the delete-account
   * edge function (server-side purge + storage + auth user), then clear the
   * local session. Irreversible; safe to retry on failure.
   */
  deleteAccount(): Promise<void>
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
