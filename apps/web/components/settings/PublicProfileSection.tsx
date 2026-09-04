"use client"

import { Copy, Share2 } from "lucide-react"
import { useTranslations } from "next-intl"
import { toast } from "sonner"
import { SectionLabel } from "@/components/ui/SectionLabel"
import { SettingsGroup } from "@/components/settings/SettingsGroup"
import { SettingsRow } from "@/components/settings/SettingsRow"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"

/** Stable error codes raised by the set_handle RPC. */
export type HandleErrorCode = "invalid_handle" | "handle_taken" | "handle_reserved"

type PublicProfileSectionProps = {
  /** Effective handle in the input (staged edit falls back to persisted). */
  handle: string
  onHandleChange: (value: string) => void
  /** Inline error from the last failed set_handle call, cleared on edit. */
  handleError: HandleErrorCode | null
  /** Effective public flag (staged falls back to persisted). */
  isPublic: boolean
  onPublicChange: (value: boolean) => void
  /** The persisted (saved) handle — drives the share row and toggle gating. */
  savedHandle: string | null
  savedPublic: boolean
}

/**
 * Settings "Public profile" section (M50): handle claim/edit, the public
 * toggle, and the share row. Staged like every other settings edit — nothing
 * writes until the page-level Save runs (set_handle RPC for the handle, a
 * direct public_profile update for the toggle).
 */
export function PublicProfileSection({
  handle,
  onHandleChange,
  handleError,
  isPublic,
  onPublicChange,
  savedHandle,
  savedPublic,
}: PublicProfileSectionProps) {
  const t = useTranslations("settings")

  // Client-rendered, but keep the SSR pass deterministic: fall back to the
  // configured site URL rather than a hardcoded host.
  const origin =
    typeof window !== "undefined"
      ? window.location.origin
      : (process.env.NEXT_PUBLIC_SITE_URL ?? "https://www.pbbls.app")
  const shareUrl = savedPublic && savedHandle ? `${origin}/u/${savedHandle}` : null

  const copyLink = async () => {
    if (!shareUrl) return
    try {
      await navigator.clipboard.writeText(shareUrl)
      toast.success(t("publicProfileLinkCopied"))
    } catch {
      toast.error(t("publicProfileLinkCopyError"))
    }
  }

  const shareLink = async () => {
    if (!shareUrl) return
    // navigator.share needs a secure context and user gesture; the copy
    // button next to it is the universal fallback.
    try {
      await navigator.share({ url: shareUrl })
    } catch {
      // User dismissed the sheet or share is unavailable — nothing to report.
    }
  }

  return (
    <section className="flex flex-col gap-2">
      <SectionLabel id="settings-public-profile">{t("publicProfile")}</SectionLabel>
      <SettingsGroup aria-labelledby="settings-public-profile">
        {/* SettingsRow wraps children in a <span>, so everything here stays
            inline-level — a block <div> inside a <span> is invalid HTML. */}
        <SettingsRow>
          <span className="flex flex-col gap-1">
            <label htmlFor="settings-handle" className="sr-only">
              {t("handle")}
            </label>
            <span className="flex items-center gap-1">
              <span className="text-muted-foreground" aria-hidden>
                @
              </span>
              <Input
                id="settings-handle"
                value={handle}
                onChange={(e) => onHandleChange(e.target.value)}
                placeholder={t("handlePlaceholder")}
                autoCapitalize="none"
                autoCorrect="off"
                spellCheck={false}
                aria-invalid={handleError !== null}
                aria-describedby="settings-handle-description"
                className="h-auto border-0 bg-transparent p-0 shadow-none focus-visible:ring-0"
              />
            </span>
            {handleError ? (
              <span
                id="settings-handle-description"
                role="alert"
                className="text-xs font-normal text-destructive"
              >
                {t(`handleError.${handleError}`)}
              </span>
            ) : (
              <span
                id="settings-handle-description"
                className="text-xs font-normal text-muted-foreground"
              >
                {t("handleHint")}
              </span>
            )}
          </span>
        </SettingsRow>
        <SettingsRow
          trailing={
            <Checkbox
              checked={isPublic}
              disabled={!savedHandle}
              onCheckedChange={(checked) => onPublicChange(checked === true)}
              aria-label={t("publicToggle")}
            />
          }
        >
          <span className={savedHandle ? undefined : "text-muted-foreground"}>
            {t("publicToggle")}
          </span>
          {!savedHandle && (
            <span className="block text-xs font-normal text-muted-foreground">
              {t("publicToggleNeedsHandle")}
            </span>
          )}
        </SettingsRow>
        {shareUrl && (
          <SettingsRow
            trailing={
              <span className="flex items-center gap-1">
                <Button
                  variant="ghost"
                  size="icon"
                  onClick={copyLink}
                  aria-label={t("publicProfileCopy")}
                >
                  <Copy />
                </Button>
                <Button
                  variant="ghost"
                  size="icon"
                  onClick={shareLink}
                  aria-label={t("publicProfileShare")}
                >
                  <Share2 />
                </Button>
              </span>
            }
          >
            <span className="block truncate font-normal text-muted-foreground">
              {shareUrl}
            </span>
          </SettingsRow>
        )}
      </SettingsGroup>
    </section>
  )
}
