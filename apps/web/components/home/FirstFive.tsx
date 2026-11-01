"use client"

import { useMemo, useState } from "react"
import { useAchievements } from "@/lib/data/useAchievements"
import { useClaimBluePebble } from "@/lib/data/useClaimBluePebble"
import { useMoments } from "@/lib/data/useMoments"
import { firstFiveState } from "@/lib/home/first-five"
import type { FirstFiveTask } from "@/lib/types"
import { FirstFiveBox } from "./FirstFiveBox"

const LINKS: Record<Exclude<FirstFiveTask, "first-moment" | "first-pebble">, string> = {
  "first-collection": "/collections",
  "first-soul": "/souls",
  "first-glyph": "/carve",
}

/**
 * Home's first five (#1384, spec D12): read through `useAchievements`, which
 * runs the check first (Home's "check on appear"). Shown while in progress or
 * claimable; a granted claim ends it, at once here and on every later read.
 */
export function FirstFive({ onQuickMoment }: { onQuickMoment: () => void }) {
  const { achievements, unlockedAt, loading } = useAchievements()
  const { moments } = useMoments()
  const { claim } = useClaimBluePebble()
  const [claiming, setClaiming] = useState(false)
  const [claimFailed, setClaimFailed] = useState(false)
  const [claimed, setClaimed] = useState(false)

  const state = useMemo(() => {
    const catalog = new Set(achievements.filter((a) => a.isActive).map((a) => a.slug))
    const unlocked = new Set(achievements.filter((a) => unlockedAt.has(a.id)).map((a) => a.slug))
    return firstFiveState(catalog, unlocked)
  }, [achievements, unlockedAt])

  // The latest moment that is not a pebble yet: completing it is the pebble task.
  const bare = useMemo(
    () => moments.filter((m) => m.pebble === null).sort((a, b) => b.happened_at.localeCompare(a.happened_at))[0] ?? null,
    [moments],
  )

  if (loading || claimed || (state.state !== "inProgress" && state.state !== "claimable")) return null

  const hrefFor = (task: FirstFiveTask) =>
    task === "first-pebble" ? (bare ? `/record/complete/${bare.id.toLowerCase()}` : "/record") : task === "first-moment" ? "/record" : LINKS[task]

  const onClaim = async () => {
    setClaiming(true)
    setClaimFailed(false)
    try {
      const granted = await claim()
      if (granted) setClaimed(true)
      else setClaimFailed(true)
    } catch {
      setClaimFailed(true)
    } finally {
      setClaiming(false)
    }
  }

  return (
    <FirstFiveBox
      state={state}
      hrefFor={hrefFor}
      onQuickMoment={onQuickMoment}
      onClaim={() => void onClaim()}
      claiming={claiming}
      claimFailed={claimFailed}
    />
  )
}
