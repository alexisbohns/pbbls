"use client"

import Link from "next/link"
import { CircleUser, Stone, CirclePile, Sparkle } from "lucide-react"
import { useAuth } from "@/lib/data/auth-context"
import { usePebblesCount } from "@/lib/data/usePebblesCount"
import { useBounce } from "@/lib/data/useBounce"
import { useKarma } from "@/lib/data/useKarma"

function StatItem({
  icon: Icon,
  value,
  label,
}: {
  icon: React.ComponentType<{ className?: string }>
  value: string | number
  label: string
}) {
  return (
    <div className="flex items-center gap-1.5">
      <Icon className="size-3.5 text-muted-foreground" aria-hidden />
      <span className="text-sm font-semibold">{value}</span>
      <span className="text-xs text-muted-foreground hidden md:inline">{label}</span>
    </div>
  )
}

export function PathProfileCard() {
  const { user, profile, isAuthenticated, isLoading: authLoading } = useAuth()
  const { pebblesCount, loading: countLoading } = usePebblesCount()
  const { bounce, loading: bounceLoading } = useBounce()
  const { karma, loading: karmaLoading } = useKarma()

  const loading = authLoading || countLoading || bounceLoading || karmaLoading

  if (loading || !isAuthenticated || !user || !profile) return null

  return (
    <Link
      href="/profile"
      className="block rounded-xl transition-colors hover:bg-muted/50 focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
      aria-label={`${profile.display_name}'s profile`}
    >
      {/* Mobile: horizontal compact bar */}
      <div className="flex items-center gap-3 px-4 py-3 md:hidden">
        <div className="flex size-9 shrink-0 items-center justify-center rounded-full bg-muted">
          <CircleUser className="size-5 text-muted-foreground" aria-hidden />
        </div>
        <div className="flex flex-1 items-center justify-between gap-3">
          <StatItem icon={Stone} value={pebblesCount} label="pebbles" />
          <StatItem icon={CirclePile} value={bounce} label="bounce" />
          <StatItem icon={Sparkle} value={karma} label="karma" />
        </div>
      </div>

      {/* Desktop: vertical card */}
      <div className="hidden md:block space-y-4 p-4">
        <div className="flex items-center gap-3">
          <div className="flex size-10 shrink-0 items-center justify-center rounded-full bg-muted">
            <CircleUser className="size-6 text-muted-foreground" aria-hidden />
          </div>
          <div className="min-w-0">
            <p className="text-sm font-semibold truncate">{profile.display_name}</p>
            <p className="text-xs text-muted-foreground truncate">@{user.username}</p>
          </div>
        </div>

        <div className="flex items-center justify-between gap-2">
          <StatItem icon={Stone} value={pebblesCount} label={pebblesCount === 1 ? "pebble" : "pebbles"} />
          <StatItem icon={CirclePile} value={bounce} label="bounce" />
          <StatItem icon={Sparkle} value={karma} label="karma" />
        </div>
      </div>
    </Link>
  )
}
