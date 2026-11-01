"use client"

import { useState } from "react"
import { useTranslations } from "next-intl"
import { Button } from "@/components/ui/button"
import { DetailTopBar } from "@/components/layout/DetailTopBar"
import { ConsentSection } from "@/components/settings/ConsentSection"
import { DeleteAccountSection } from "@/components/settings/DeleteAccountSection"
import { SignOutSection } from "@/components/settings/SignOutSection"
import { GlyphHeader } from "@/components/settings/GlyphHeader"
import { InformationsSection } from "@/components/settings/InformationsSection"
import { LegalSection } from "@/components/settings/LegalSection"
import { PasswordSection } from "@/components/settings/PasswordSection"
import { ProvidersSection } from "@/components/settings/ProvidersSection"
import { PublicProfileSection } from "@/components/settings/PublicProfileSection"
import { useUsableGlyphs } from "@/lib/data/useUsableGlyphs"
import { YouView } from "./YouView"
import { DESIGN_ACCOUNT, DESIGN_CONSENT, DESIGN_PROFILE } from "@/lib/seed/design-fixtures"

/**
 * Settings open beside You (#1384, two-area spec D2, D7): the detail's tree
 * with local, throwaway state. AppearanceSection
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
    <YouView
      at="/settings"
      detail={
        <>
          <DetailTopBar
            title={t("title")}
            actions={
              <Button variant="ghost" disabled className="m3-type-label-large h-10 rounded-full px-3 text-m3-primary">
                {t("save")}
              </Button>
            }
          />
          <section className="mx-auto w-full max-w-2xl px-4 pt-2 pb-12">
            <div className="flex flex-col gap-7">
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
              <DeleteAccountSection onDeleted={() => {}} reauthReturnTo="/sandbox/design" />
              <SignOutSection onSignOut={async () => {}} />
            </div>
          </section>
        </>
      }
    />
  )
}
