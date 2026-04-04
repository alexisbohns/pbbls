"use client"

import { useRouter } from "next/navigation"
import { useAuth } from "@/lib/data/auth-context"
import { ProfileCard } from "@/components/profile/ProfileCard"
import { LogoutButton } from "@/components/profile/LogoutButton"

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
        <p className="text-sm text-muted-foreground">Loading\u2026</p>
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
        <LogoutButton onLogout={handleLogout} />
      </div>
    </section>
  )
}
