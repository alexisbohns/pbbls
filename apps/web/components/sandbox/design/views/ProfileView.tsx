"use client"

import Link from "next/link"
import { Settings } from "lucide-react"
import { useTranslations } from "next-intl"
import { Button } from "@/components/ui/button"
import { PageHeader } from "@/components/layout/PageHeader"
import { PageLayout } from "@/components/layout/PageLayout"
import { AchievementsShelf } from "@/components/profile/AchievementsShelf"
import { CollectionsCard } from "@/components/profile/CollectionsCard"
import { LabCard } from "@/components/profile/LabCard"
import { LogoutButton } from "@/components/profile/LogoutButton"
import { ProfileBanner } from "@/components/profile/ProfileBanner"
import { ShortcutsRow } from "@/components/profile/ShortcutsRow"
import { StatsCard } from "@/components/profile/StatsCard"
import { usePebbles } from "@/lib/data/usePebbles"
import { useUsableGlyphs } from "@/lib/data/useUsableGlyphs"
import { useFormatDate } from "@/lib/i18n"
import {
  DESIGN_ACCOUNT,
  DESIGN_ASSIDUITY,
  DESIGN_PROFILE,
  DESIGN_RIPPLE,
} from "@/lib/seed/design-fixtures"

/** app/profile/page.tsx's tree, with fixture values where the page reads the network. */
export function ProfileView() {
  const t = useTranslations("profile")
  const formatDate = useFormatDate()
  const { glyphs } = useUsableGlyphs()
  const { pebbles } = usePebbles()
  const glyph = glyphs.find((g) => g.id === DESIGN_PROFILE.glyph_id) ?? null

  return (
    <PageLayout>
      <section>
        <PageHeader
          title={t("title")}
          backHref="/path"
          rightSlot={
            <Button variant="outline" size="icon" aria-label={t("settingsAria")} render={<Link href="/settings" />}>
              <Settings />
            </Button>
          }
        />
        <div className="flex flex-col gap-6">
          <ProfileBanner
            displayName={DESIGN_PROFILE.display_name}
            memberSince={formatDate(DESIGN_ACCOUNT.created_at, { dateStyle: "medium" })}
            glyph={glyph}
          />
          <ShortcutsRow />
          <StatsCard
            ripple={DESIGN_RIPPLE}
            assiduity={DESIGN_ASSIDUITY}
            daysPracticed={19}
            pebbles={pebbles.length}
            karma={42}
          />
          <AchievementsShelf />
          <CollectionsCard />
          <LabCard />
          <LogoutButton onLogout={async () => {}} />
        </div>
      </section>
    </PageLayout>
  )
}
