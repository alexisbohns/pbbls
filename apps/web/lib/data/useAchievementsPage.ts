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
import { CEREMONY_SLUG } from "@/lib/achievements/copy"
import { presentCeremony } from "@/lib/activity/present-ceremony"
import type { AchievementCard, CeremonyStack } from "@/lib/types"

/** The reader's zone, so every local day is the reader's (D12, D17). */
function readerTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC"
}

const SIGNED_OUT: AchievementsPageState = { page: null, loading: false, failed: false, pulling: null }
const noSubscription = () => () => {}
const signedOut = () => SIGNED_OUT

/**
 * The achievements page's data (#1308), over `AchievementsPageStore`: the
 * user's kept copy at once, then the grant and the read (stale-while-
 * revalidate, spec §4). The sheets read the same page, so opening one costs no
 * call (D14). A store belongs to one user: a new user gets a new store, and
 * the old one is closed, so nothing it reads afterwards is shown or kept.
 * `shelfMaxAgeMs` opens it for the Profile shelf instead (D18): no grant, and
 * no read while the kept copy is younger than that.
 */
export function useAchievementsPage(shelfMaxAgeMs?: number) {
  const { provider } = useDataProvider()
  const { user } = useAuth()
  const userId = user?.id ?? null

  const store = useMemo(
    () =>
      provider && userId
        ? new AchievementsPageStore({
            provider,
            owner: userId,
            zone: readerTimeZone,
            storage: browserStorage(),
          })
        : null,
    [provider, userId],
  )

  useEffect(() => {
    if (!store) return
    void store.open(shelfMaxAgeMs === undefined ? { kind: "page" } : { kind: "shelf", maxAgeMs: shelfMaxAgeMs })
    return () => store.close()
  }, [store, shelfMaxAgeMs])

  const state = useSyncExternalStore(store?.subscribe ?? noSubscription, store?.getSnapshot ?? signedOut, signedOut)

  const markSeen = useCallback(
    (card: AchievementCard) => {
      void store?.markSeen(card)
    },
    [store],
  )

  /**
   * Pulls a ready ceremony through the store (decided on this session's read)
   * and, once it landed, plays it in the unlock moment; `words` name it.
   */
  const pull = useCallback(
    async (stack: CeremonyStack, words: { title: string; description: string }): Promise<CeremonyPullOutcome> => {
      if (!store || !provider) {
        console.error(`[achievements] pull_ceremony(${stack}) asked while signed out`)
        return { kind: "failed" }
      }
      const outcome = await store.pull(stack)
      if (outcome.kind === "pulled" || outcome.kind === "pulledWithoutKarma") {
        void presentCeremony(provider, CEREMONY_SLUG[stack], outcome.kind === "pulled" ? outcome.karmaGranted : 0, words)
      }
      return outcome
    },
    [store, provider],
  )

  const retry = useCallback(() => {
    void store?.load()
  }, [store])

  return { ...state, markSeen, pull, retry }
}

/** The Profile shelf's read is skipped while the kept copy is younger than this (D18). */
export const SHELF_MAX_AGE_MS = 15 * 60 * 1000

/** The Profile shelf (D18): the served `shelf` and `shelf_count`, from the kept copy at once. */
export function useAchievementsShelf() {
  const { page, loading } = useAchievementsPage(SHELF_MAX_AGE_MS)
  return { shelf: page?.shelf ?? null, count: page?.shelfCount ?? null, loading }
}
