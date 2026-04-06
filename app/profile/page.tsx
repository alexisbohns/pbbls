"use client"

import Link from "next/link"
import { useRouter } from "next/navigation"
import { ChevronRight } from "lucide-react"
import { useAuth } from "@/lib/data/auth-context"
import { ProfileCard } from "@/components/profile/ProfileCard"
import { LogoutButton } from "@/components/profile/LogoutButton"
import { NAV_ITEMS } from "@/lib/config/navigation"

const PROFILE_NAV = NAV_ITEMS.filter(
  (item) => item.href !== "/path",
)

export default function ProfilePage() {
  const { user, profile, isAuthenticated, isLoading, logout } = useAuth()
  const router = useRouter()

  const handleLogout = async () => {
    await logout()
    router.push("/")
  }

  if (isLoading) {
    return (
      <section>
        <h1 className="mb-6 text-2xl font-semibold">Profile</h1>
        <p className="text-sm text-muted-foreground">Loading…</p>
      </section>
    )
  }

  if (!isAuthenticated || !user || !profile) {
    return (
      <section>
        <h1 className="mb-6 text-2xl font-semibold">Profile</h1>
        <p className="text-sm text-muted-foreground">
          Sign in to view your profile.
        </p>
      </section>
    )
  }

  return (
    <section>
      <h1 className="mb-6 text-2xl font-semibold">Profile</h1>
      <div className="space-y-6">
        <ProfileCard user={user} profile={profile} />

        <nav aria-label="App sections">
          <ul className="divide-y divide-border rounded-xl border border-border">
            {PROFILE_NAV.map((item) => {
              const Icon = item.icon
              return (
                <li key={item.href}>
                  <Link
                    href={item.href}
                    className="flex items-center gap-3 px-4 py-3 transition-colors hover:bg-muted/50 first:rounded-t-xl last:rounded-b-xl"
                  >
                    <Icon className="size-5 text-muted-foreground" aria-hidden />
                    <span className="flex-1 text-sm font-medium">{item.label}</span>
                    <ChevronRight className="size-4 text-muted-foreground" aria-hidden />
                  </Link>
                </li>
              )
            })}
          </ul>
        </nav>

        <LogoutButton onLogout={handleLogout} />
      </div>
    </section>
  )
}
