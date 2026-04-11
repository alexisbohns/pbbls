"use client"

import Link from "next/link"
import { CircleUser, CirclePile, Sparkle } from "lucide-react"
import { useAuth } from "@/lib/data/auth-context"
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
      <span className="text-xs text-muted-foreground hidden md:flex">{label}</span>
    </div>
  )
}

export function PathProfileCard() {
  const { user, profile, isAuthenticated, isLoading: authLoading } = useAuth()
  const { bounce, loading: bounceLoading } = useBounce()
  const { karma, loading: karmaLoading } = useKarma()

  const loading = authLoading || bounceLoading || karmaLoading

  if (loading || !isAuthenticated || !user || !profile) return null

  return (
    <Link
      href="/profile"
      className="block rounded-xl transition-colors bg-card hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none border"
      aria-label={`${profile.display_name}'s profile`}
    >
      <div className="flex gap-2 px-4 py-3 md:flex-col md:space-y-4 p-4 justify-between items-center md:items-stretch">
        <div className="flex items-center md:gap-3">
          <div className="hidden md:flex md:flex-col md:w-full">
            <p className="text-sm font-semibold truncate">{profile.display_name}</p>
            <p className="text-xs text-muted-foreground truncate">{user.email}</p>
          </div>
          <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-card">
            <CircleUser className="size-6 text-muted-foreground" aria-hidden />
          </div>
        </div>

        <div className="flex md:flex-col items-start justify-between gap-3">
          <StatItem icon={CirclePile} value={bounce} label="bounce" />
          <StatItem icon={Sparkle} value={karma} label="karma" />
        </div>
      </div>
    </Link>
  )
}
