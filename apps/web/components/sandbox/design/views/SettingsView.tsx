"use client"

import { useState } from "react"
import { useTranslations } from "next-intl"
import { Button } from "@/components/ui/button"
import { PageHeader } from "@/components/layout/PageHeader"
import { PageLayout } from "@/components/layout/PageLayout"
import { ConsentSection } from "@/components/settings/ConsentSection"
import { DeleteAccountSection } from "@/components/settings/DeleteAccountSection"
import { GlyphHeader } from "@/components/settings/GlyphHeader"
import { InformationsSection } from "@/components/settings/InformationsSection"
import { LegalSection } from "@/components/settings/LegalSection"
import { PasswordSection } from "@/components/settings/PasswordSection"
import { ProvidersSection } from "@/components/settings/ProvidersSection"
import { PublicProfileSection } from "@/components/settings/PublicProfileSection"
import { useUsableGlyphs } from "@/lib/data/useUsableGlyphs"
import { DESIGN_ACCOUNT, DESIGN_CONSENT, DESIGN_PROFILE } from "@/lib/seed/design-fixtures"

/**
 * app/settings/page.tsx's tree with local, throwaway state. AppearanceSection
 * is left out on purpose: its controls write the viewer's real theme, locale
 * and color world.
 */
export function SettingsView() {
  const t = useTranslations("settings")
  const { glyphs } = useUsableGlyphs()
  const [glyphId, setGlyphId] = useState<string | null>(DESIGN_PROFILE.glyph_id)
  const [name, setName] = useState(DESIGN_PROFILE.display_name)
  const [handle, setHandle] = useState(DESIGN_PROFILE.handle ?? "")
  const [isPublic, setIsPublic] = useState(DESIGN_PROFILE.public_profile)
  const [password, setPassword] = useState("")
  const glyph = glyphs.find((g) => g.id === glyphId) ?? null

  return (
    <PageLayout>
      <section>
        <PageHeader
          title={t("title")}
          backHref="/profile"
          rightSlot={
            <Button size="sm" disabled>
              {t("save")}
            </Button>
          }
        />
        <div className="flex flex-col gap-6">
          <GlyphHeader glyph={glyph} glyphs={glyphs} selectedGlyphId={glyphId} onSelect={setGlyphId} />
          <InformationsSection name={name} onNameChange={setName} email={DESIGN_ACCOUNT.email} />
          <PublicProfileSection
            handle={handle}
            onHandleChange={setHandle}
            handleError={null}
            isPublic={isPublic}
            onPublicChange={setIsPublic}
            savedHandle={DESIGN_PROFILE.handle}
            savedPublic={DESIGN_PROFILE.public_profile}
          />
          <ProvidersSection providers={DESIGN_ACCOUNT.providers ?? []} />
          <PasswordSection value={password} onChange={setPassword} />
          <LegalSection />
          <ConsentSection consent={DESIGN_CONSENT} onGrant={async () => {}} onWithdrawn={() => {}} />
          <DeleteAccountSection onDeleted={() => {}} />
        </div>
      </section>
    </PageLayout>
  )
}
