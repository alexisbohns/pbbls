"use client"

import { useEffect, useRef, useState } from "react"
import { useRouter } from "next/navigation"
import { toast } from "sonner"
import { useTranslations } from "next-intl"
import { useAuth } from "@/lib/data/auth-context"
import { useUsableGlyphs } from "@/lib/data/useUsableGlyphs"
import { useConsents } from "@/lib/data/useConsents"
import type { UpdateProfileInput } from "@/lib/types"
import { isReauthRequired } from "@/lib/auth/recent-auth"
import {
  REAUTH_RETURN_PARAM,
  stashPendingReauth,
  takePendingReauth,
} from "@/lib/auth/pending-reauth"
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
import { ConsentSection } from "@/components/settings/ConsentSection"
import { AppearanceSection } from "@/components/settings/AppearanceSection"
import { DeleteAccountSection } from "@/components/settings/DeleteAccountSection"
import { ReauthDialog } from "@/components/settings/ReauthDialog"

/** Where a provider re-auth comes back to (the callback's validated `next`). */
const REAUTH_RETURN_TO = `/settings?${REAUTH_RETURN_PARAM}=1`

export default function SettingsPage() {
  const {
    user,
    profile,
    isAuthenticated,
    isLoading,
    updateProfile,
    setHandle,
    updatePassword,
    isSignInRecent,
    logout,
  } = useAuth()
  const router = useRouter()
  const { glyphs } = useUsableGlyphs()
  // Deliberately outside the staged-save model every other control on this
  // page follows: a consent act is not a draft edit. Granting or withdrawing
  // writes immediately, and `record_consent` is idempotent, so there is
  // nothing for the page-level Save to batch or replay.
  const {
    healthData: healthConsent,
    publicProfile: publicConsent,
    record: recordConsent,
    withdraw: withdrawConsent,
    refresh: refreshConsents,
  } = useConsents()
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
  const [reauthOpen, setReauthOpen] = useState(false)
  const [resumeDelete, setResumeDelete] = useState(false)

  // Back from a provider re-auth (#977): restore what was staged and reopen
  // the step, never run it. Runs once, as soon as the account is known. The
  // stash is always taken, so one left by an abandoned redirect is discarded.
  const resumeChecked = useRef(false)
  useEffect(() => {
    if (resumeChecked.current || !user || !profile) return
    resumeChecked.current = true
    const returning = new URLSearchParams(window.location.search).has(REAUTH_RETURN_PARAM)
    const pending = takePendingReauth()
    if (!returning) return
    router.replace("/settings")
    if (!pending) return
    if (pending.userId !== user.id) {
      // The provider came back as someone else, and the callback already
      // switched the session to them. Never act on that account.
      console.error("[settings] re-auth returned a different account; signing out")
      toast.error(t("reauthMismatch"))
      void logout().catch((err) => console.error("[settings] sign-out after mismatch failed:", err))
      return
    }
    if (pending.purpose === "delete") {
      setResumeDelete(true)
      return
    }
    const { form } = pending
    setNameInput(form.name)
    setStagedGlyphId(form.glyphId.unchanged ? undefined : form.glyphId.value)
    setHandleInput(form.handle)
    setStagedPublic(form.isPublic)
    toast(t("reauthResumeSave"))
  }, [user, profile, router, logout, t])

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
  // A password change and going public need a recent sign-in (#976). Every
  // other edit saves without a prompt.
  const saveNeedsRecentAuth = passwordChanged || (publicChanged && isPublic)

  const handleSave = async () => {
    if (saving || reauthOpen) return
    setSaving(true)
    if (saveNeedsRecentAuth && !(await isSignInRecent())) {
      setSaving(false)
      setReauthOpen(true)
      return
    }
    await runSave()
  }

  const runSave = async () => {
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
      // Releasing the handle already dropped the flag server-side; only touch
      // the toggle when a handle exists to satisfy the CHECK.
      //
      // `public_profile` IS the consent to publish, so it is written by the
      // consent RPCs rather than as a plain column update: record_consent on
      // the way in, withdraw_consent on the way out — the latter flips the
      // column off itself, in the same transaction as the ledger row.
      if (publicChanged && effectiveHandle) {
        if (isPublic) {
          // Ledger first: a consent row without the flag exposes nothing,
          // whereas a published profile without a consent row is the defect
          // this change exists to close.
          await recordConsent("public_profile", "web_settings")
          updates.public_profile = true
        } else {
          try {
            await withdrawConsent("public_profile")
          } catch (err) {
            const message = err instanceof Error ? err.message : String(err)
            if (!message.includes("no_active_consent")) throw err
            if (publicConsent) {
              // The ledger we rendered says there is a live consent and the
              // database disagrees: a double submit, a stale view, or the rare
              // race where a record_consent at a bumped version commits
              // between this withdraw's snapshot and its row lock. For an
              // accountability record that mismatch is loud, not absorbed.
              console.error(
                "[settings] withdraw_consent(public_profile) found no active consent",
              )
              await refreshConsents()
              toast.error(t("saveError"))
              return
            }
            // Accounts that went public before the ledger existed have the
            // flag set and no row to withdraw. Their intent still wins —
            // unpublish through the ordinary profile update.
            updates.public_profile = false
          }
        }
      }
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
      if (isReauthRequired(err)) {
        // The server's clock disagrees with ours. Whatever already landed (a
        // handle, a consent row) is idempotent to resend, and the password is
        // written last, so re-running the whole save after the re-auth is safe.
        setReauthOpen(true)
        return
      }
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
          <ConsentSection
            consent={healthConsent}
            onGrant={() => recordConsent("health_data", "web_settings")}
            onWithdrawn={() => router.push("/")}
          />
          <AppearanceSection />
          <DeleteAccountSection
            key={resumeDelete ? "resume" : "idle"}
            onDeleted={() => router.push("/")}
            resumeAfterReauth={resumeDelete}
            reauthReturnTo={REAUTH_RETURN_TO}
          />
        </div>
        <ReauthDialog
          open={reauthOpen}
          returnTo={REAUTH_RETURN_TO}
          onBeforeRedirect={() =>
            stashPendingReauth({
              purpose: "save",
              userId: user.id,
              savedAt: Date.now(),
              // No password: provider-only accounts have no password field.
              form: {
                name: nameInput,
                glyphId:
                  stagedGlyphId === undefined
                    ? { unchanged: true }
                    : { unchanged: false, value: stagedGlyphId },
                handle: handleInput,
                isPublic: stagedPublic,
              },
            })
          }
          onCancel={() => setReauthOpen(false)}
          onConfirmed={() => {
            setReauthOpen(false)
            void runSave()
          }}
        />
      </section>
    </PageLayout>
  )
}
