"use client"

import { useCallback, useEffect, useMemo, useSyncExternalStore } from "react"
import { useAuth } from "@/lib/data/auth-context"
import { useDataProvider } from "@/lib/data/provider-context"
import { browserStorage } from "@/lib/data/achievements-cache"
import {
  AchievementsPageStore,
  type AchievementsPageState,
  type CeremonyPullOutcome,
} from "@/lib/data/achievements-page-store"
import type { AchievementCard, CeremonyStack } from "@/lib/types"

const SIGNED_OUT: AchievementsPageState = { page: null, loading: false, failed: false, pulling: null }
const noSubscription = () => () => {}
const signedOut = () => SIGNED_OUT

/**
 * The achievements page's data (#1308), over `AchievementsPageStore`: the
 * user's kept copy at once, then the grant and the read (stale-while-
 * revalidate, spec §4). The sheets read the same page, so opening one costs no
 * call (D14). A store belongs to one user: a new user gets a new store, and
 * the old one is closed, so nothing it reads afterwards is shown or kept.
 */
export function useAchievementsPage() {
  const { provider } = useDataProvider()
  const { user } = useAuth()
  const userId = user?.id ?? null

  const store = useMemo(
    () =>
      provider && userId
        ? new AchievementsPageStore({
            provider,
            owner: userId,
            // The reader's zone, so every local day is the reader's (D12, D17).
            zone: Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC",
            storage: browserStorage(),
          })
        : null,
    [provider, userId],
  )

  useEffect(() => {
    if (!store) return
    void store.open()
    return () => store.close()
  }, [store])

  const state = useSyncExternalStore(store?.subscribe ?? noSubscription, store?.getSnapshot ?? signedOut, signedOut)

  const markSeen = useCallback(
    (card: AchievementCard) => {
      void store?.markSeen(card)
    },
    [store],
  )

  const pull = useCallback(
    async (stack: CeremonyStack): Promise<CeremonyPullOutcome> => {
      if (!store) {
        console.error(`[achievements] pull_ceremony(${stack}) asked while signed out`)
        return { kind: "failed" }
      }
      return store.pull(stack)
    },
    [store],
  )

  const retry = useCallback(() => {
    void store?.load()
  }, [store])

  return { ...state, markSeen, pull, retry }
}
