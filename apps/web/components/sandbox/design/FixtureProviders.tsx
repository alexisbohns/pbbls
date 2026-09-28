"use client"

import { useMemo, useState, type ReactNode } from "react"
import { AuthContext, type AuthContextValue } from "@/lib/data/auth-context"
import { DataContext, type DataContextValue } from "@/lib/data/provider-context"
import { DESIGN_ACCOUNT, DESIGN_PROFILE, designStore } from "@/lib/seed/design-fixtures"

const noop = async () => {}

// Every write resolves without effect: the design page must never reach Supabase.
const FIXTURE_AUTH: AuthContextValue = {
  user: DESIGN_ACCOUNT,
  profile: DESIGN_PROFILE,
  isAuthenticated: true,
  isLoading: false,
  isProfileLoading: false,
  login: noop,
  register: noop,
  signInWithApple: noop,
  signInWithGoogle: noop,
  logout: noop,
  updateProfile: async () => DESIGN_PROFILE,
  setHandle: async (handle) => handle,
  updatePassword: noop,
  deleteAccount: noop,
}

/**
 * Shadows the root AuthProvider and DataProvider for everything the design
 * page renders. `provider: null` makes the data hooks read the fixture store;
 * their provider-backed reads (achievements, ripple, draft count) fall back to
 * their empty values.
 */
export function FixtureProviders({ children }: { children: ReactNode }) {
  const [store, setStore] = useState(() => designStore(new Date()))
  const data = useMemo<DataContextValue>(
    () => ({ provider: null, store, setStore, loading: false, error: null, refreshStore: () => {} }),
    [store],
  )
  return (
    <AuthContext.Provider value={FIXTURE_AUTH}>
      <DataContext.Provider value={data}>{children}</DataContext.Provider>
    </AuthContext.Provider>
  )
}
