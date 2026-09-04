"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
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
import {
  PublicProfileSection,
  type HandleErrorCode,
} from "@/components/settings/PublicProfileSection"
import { ProvidersSection } from "@/components/settings/ProvidersSection"
import { PasswordSection } from "@/components/settings/PasswordSection"
import { LegalSection } from "@/components/settings/LegalSection"
import { AppearanceSection } from "@/components/settings/AppearanceSection"
import { DeleteAccountSection } from "@/components/settings/DeleteAccountSection"

export default function SettingsPage() {
  const { user, profile, isAuthenticated, isLoading, updateProfile, setHandle, updatePassword } = useAuth()
  const router = useRouter()
  const { glyphs } = useUsableGlyphs()
  const t = useTranslations("settings")
  const tProfile = useTranslations("profile")

  // Staged edits — `null`/`undefined` means "unchanged" so the effective value
  // falls back to the persisted profile.
  const [nameInput, setNameInput] = useState<string | null>(null)
  const [stagedGlyphId, setStagedGlyphId] = useState<string | null | undefined>(undefined)
  const [password, setPassword] = useState("")
  const [handleInput, setHandleInput] = useState<string | null>(null)
  const [stagedPublic, setStagedPublic] = useState<boolean | null>(null)
  const [handleError, setHandleError] = useState<HandleErrorCode | null>(null)
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

  // Handle edits are staged as raw input; comparison happens on the
  // normalized form the set_handle RPC stores (lowercase, trimmed).
  const savedHandle = profile.handle
  const handleValue = handleInput ?? savedHandle ?? ""
  const normalizedHandle = handleValue.trim().toLowerCase()
  const isPublic = stagedPublic ?? profile.public_profile

  const nameChanged = name.trim().length > 0 && name.trim() !== profile.display_name
  const glyphChanged = stagedGlyphId !== undefined && stagedGlyphId !== profile.glyph_id
  const passwordChanged = password.trim().length > 0
  const handleChanged = normalizedHandle !== (savedHandle ?? "")
  const publicChanged = isPublic !== profile.public_profile
  const dirty = nameChanged || glyphChanged || passwordChanged || handleChanged || publicChanged

  const handleSave = async () => {
    setSaving(true)
    try {
      // Handle first: a same-save "claim + go public" needs the handle stored
      // before the public_profile update passes the DB CHECK. A failed claim
      // aborts the whole save so nothing half-applies.
      let effectiveHandle = savedHandle
      if (handleChanged) {
        try {
          effectiveHandle = await setHandle(normalizedHandle || null)
        } catch (err) {
          const message = err instanceof Error ? err.message : String(err)
          const code = (["invalid_handle", "handle_taken", "handle_reserved"] as const).find(
            (c) => message.includes(c),
          )
          // Only a recognized rejection earns the inline field error. Timeouts,
          // network failures and `not_found` are not "your handle is invalid" —
          // they get the generic toast and a logged cause.
          if (code) {
            setHandleError(code)
          } else {
            console.error("[settings] set_handle failed:", message)
          }
          toast.error(t("saveError"))
          return
        }
      }

      const updates: UpdateProfileInput = {}
      if (nameChanged) updates.display_name = name.trim()
      if (glyphChanged && stagedGlyphId) updates.glyph_id = stagedGlyphId
      // Releasing the handle already dropped the flag server-side; only write
      // the toggle when a handle exists to satisfy the CHECK.
      if (publicChanged && effectiveHandle) updates.public_profile = isPublic
      if (Object.keys(updates).length > 0) await updateProfile(updates)
      if (passwordChanged) await updatePassword(password)
      setNameInput(null)
      setStagedGlyphId(undefined)
      setPassword("")
      setHandleInput(null)
      setStagedPublic(null)
      setHandleError(null)
      toast.success(t("saved"))
    } catch (err) {
      console.error("[settings] save failed:", err instanceof Error ? err.message : err)
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
          <PublicProfileSection
            handle={handleValue}
            onHandleChange={(value) => {
              setHandleInput(value)
              setHandleError(null)
              // Emptying the field means "release my handle", which the server
              // pairs with dropping the public flag. Mirror that here so the
              // save can never carry a contradictory "public, no handle".
              if (value.trim() === "") setStagedPublic(false)
            }}
            handleError={handleError}
            isPublic={isPublic}
            onPublicChange={setStagedPublic}
            savedHandle={savedHandle}
            savedPublic={profile.public_profile}
          />
          <ProvidersSection providers={providers} />
          {showPassword && <PasswordSection value={password} onChange={setPassword} />}
          <LegalSection />
          <AppearanceSection />
          <DeleteAccountSection onDeleted={() => router.push("/")} />
        </div>
      </section>
    </PageLayout>
  )
}
