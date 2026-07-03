"use client"

import Link from "next/link"
import { useRouter } from "next/navigation"
import { Settings } from "lucide-react"
import { useTranslations } from "next-intl"
import { useAuth } from "@/lib/data/auth-context"
import { useUsableGlyphs } from "@/lib/data/useUsableGlyphs"
import { useRipple } from "@/lib/data/useRipple"
import { useProfileEngagement } from "@/lib/data/useProfileEngagement"
import { usePebblesCount } from "@/lib/data/usePebblesCount"
import { useKarma } from "@/lib/data/useKarma"
import { useFormatDate } from "@/lib/i18n"
import { ProfileBanner } from "@/components/profile/ProfileBanner"
import { ShortcutsRow } from "@/components/profile/ShortcutsRow"
import { StatsCard } from "@/components/profile/StatsCard"
import { CollectionsCard } from "@/components/profile/CollectionsCard"
import { LabCard } from "@/components/profile/LabCard"
import { LogoutButton } from "@/components/profile/LogoutButton"
import { PageLayout } from "@/components/layout/PageLayout"
import { PageHeader } from "@/components/layout/PageHeader"
import { Button } from "@/components/ui/button"

export default function ProfilePage() {
  const { user, profile, isAuthenticated, isLoading, logout } = useAuth()
  const router = useRouter()
  const t = useTranslations("profile")
  const formatDate = useFormatDate()
  const { glyphs } = useUsableGlyphs()
  const { ripple } = useRipple()
  const { engagement } = useProfileEngagement()
  const { pebblesCount } = usePebblesCount()
  const { karma } = useKarma()

  const handleLogout = async () => {
    await logout()
    router.push("/")
  }

  const settingsButton = (
    <Button
      variant="outline"
      size="icon"
      aria-label={t("settingsAria")}
      render={<Link href="/settings" />}
    >
      <Settings />
    </Button>
  )

  if (isLoading) {
    return (
      <PageLayout>
        <section>
          <PageHeader title={t("title")} backHref="/path" />
          <p className="text-sm text-muted-foreground">{t("loading")}</p>
        </section>
      </PageLayout>
    )
  }

  if (!isAuthenticated || !user || !profile) {
    return (
      <PageLayout>
        <section>
          <PageHeader title={t("title")} backHref="/path" />
          <p className="text-sm text-muted-foreground">{t("signedOut")}</p>
        </section>
      </PageLayout>
    )
  }

  const glyph = profile.glyph_id
    ? glyphs.find((g) => g.id === profile.glyph_id) ?? null
    : null

  return (
    <PageLayout>
      <section>
        <PageHeader title={t("title")} backHref="/path" rightSlot={settingsButton} />
        <div className="flex flex-col gap-6">
          <ProfileBanner
            displayName={profile.display_name}
            memberSince={formatDate(user.created_at, { dateStyle: "medium" })}
            glyph={glyph}
          />
          <ShortcutsRow />
          <StatsCard
            ripple={ripple}
            assiduity={engagement.assiduity}
            daysPracticed={engagement.daysPracticed}
            pebbles={pebblesCount}
            karma={karma}
          />
          <CollectionsCard />
          <LabCard />
          <LogoutButton onLogout={handleLogout} />
        </div>
      </section>
    </PageLayout>
  )
}
