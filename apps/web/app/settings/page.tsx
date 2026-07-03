"use client"

import { useState } from "react"
import { toast } from "sonner"
import { useTranslations } from "next-intl"
import { useAuth } from "@/lib/data/auth-context"
import { useUsableGlyphs } from "@/lib/data/useUsableGlyphs"
import type { UpdateProfileInput } from "@/lib/types"
import { PageLayout } from "@/components/layout/PageLayout"
import { PageHeader } from "@/components/layout/PageHeader"
import { Button } from "@/components/ui/button"
import { GlyphHeader } from "@/components/settings/GlyphHeader"
import { InformationsSection } from "@/components/settings/InformationsSection"
import { ProvidersSection } from "@/components/settings/ProvidersSection"
import { PasswordSection } from "@/components/settings/PasswordSection"
import { LegalSection } from "@/components/settings/LegalSection"
import { AppearanceSection } from "@/components/settings/AppearanceSection"

export default function SettingsPage() {
  const { user, profile, isAuthenticated, isLoading, updateProfile, updatePassword } = useAuth()
  const { glyphs } = useUsableGlyphs()
  const t = useTranslations("settings")
  const tProfile = useTranslations("profile")

  // Staged edits — `null`/`undefined` means "unchanged" so the effective value
  // falls back to the persisted profile.
  const [nameInput, setNameInput] = useState<string | null>(null)
  const [stagedGlyphId, setStagedGlyphId] = useState<string | null | undefined>(undefined)
  const [password, setPassword] = useState("")
  const [saving, setSaving] = useState(false)

  if (isLoading) {
    return (
      <PageLayout>
        <section>
          <PageHeader title={t("title")} backHref="/profile" />
          <p className="text-sm text-muted-foreground">{tProfile("loading")}</p>
        </section>
      </PageLayout>
    )
  }

  if (!isAuthenticated || !user || !profile) {
    return (
      <PageLayout>
        <section>
          <PageHeader title={t("title")} backHref="/profile" />
          <p className="text-sm text-muted-foreground">{tProfile("signedOut")}</p>
        </section>
      </PageLayout>
    )
  }

  const name = nameInput ?? profile.display_name
  const glyphId = stagedGlyphId !== undefined ? stagedGlyphId : profile.glyph_id
  const glyph = glyphId ? glyphs.find((g) => g.id === glyphId) ?? null : null
  const providers = user.providers ?? []
  const showPassword = providers.length === 0 || providers.includes("email")

  const nameChanged = name.trim().length > 0 && name.trim() !== profile.display_name
  const glyphChanged = stagedGlyphId !== undefined && stagedGlyphId !== profile.glyph_id
  const passwordChanged = password.trim().length > 0
  const dirty = nameChanged || glyphChanged || passwordChanged

  const handleSave = async () => {
    setSaving(true)
    try {
      const updates: UpdateProfileInput = {}
      if (nameChanged) updates.display_name = name.trim()
      if (glyphChanged && stagedGlyphId) updates.glyph_id = stagedGlyphId
      if (Object.keys(updates).length > 0) await updateProfile(updates)
      if (passwordChanged) await updatePassword(password)
      setNameInput(null)
      setStagedGlyphId(undefined)
      setPassword("")
      toast.success(t("saved"))
    } catch {
      toast.error(t("saveError"))
    } finally {
      setSaving(false)
    }
  }

  const saveButton = (
    <Button size="sm" onClick={handleSave} disabled={!dirty || saving}>
      {saving ? t("saving") : t("save")}
    </Button>
  )

  return (
    <PageLayout>
      <section>
        <PageHeader title={t("title")} backHref="/profile" rightSlot={saveButton} />
        <div className="flex flex-col gap-6">
          <GlyphHeader
            glyph={glyph}
            glyphs={glyphs}
            selectedGlyphId={glyphId}
            onSelect={(id) => setStagedGlyphId(id)}
          />
          <InformationsSection
            name={name}
            onNameChange={setNameInput}
            email={user.email}
          />
          <ProvidersSection providers={providers} />
          {showPassword && <PasswordSection value={password} onChange={setPassword} />}
          <LegalSection />
          <AppearanceSection />
        </div>
      </section>
    </PageLayout>
  )
}
