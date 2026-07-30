"use client"

import { useEffect, useState } from "react"
import { toDataURL } from "qrcode"
import { Copy, Loader2, RotateCcw, Share2 } from "lucide-react"
import { toast } from "sonner"
import { useTranslations } from "next-intl"
import type { ConnectionInvite } from "@/lib/data/data-provider"
import { ConfirmDialog } from "@/components/ui/ConfirmDialog"
import { Button } from "@/components/ui/button"
import { useFormatDate } from "@/lib/i18n"

type InviteSectionProps = {
  /** True once the data provider exists — invite creation is a write. */
  ready: boolean
  createInvite: (rotate?: boolean) => Promise<ConnectionInvite>
}

/**
 * The caller's invite link + QR (M49, design D3/D13). Created on open:
 * `create_connection_invite` returns the LIVE invite rather than minting —
 * yesterday's link pasted into a chat keeps working, and the QR survives an
 * app restart. "New link" is the explicit rotation affordance (the old link
 * dies immediately). The URL text and copy/share buttons are always present —
 * the QR is never the sole affordance.
 */
export function InviteSection({ ready, createInvite }: InviteSectionProps) {
  const t = useTranslations("connections")
  const formatDate = useFormatDate()
  const [invite, setInvite] = useState<ConnectionInvite | null>(null)
  const [failed, setFailed] = useState(false)
  const [rotating, setRotating] = useState(false)
  const [qr, setQr] = useState<string | null>(null)

  useEffect(() => {
    if (!ready) return
    let cancelled = false

    // `await Promise.resolve()` defers state updates past the synchronous
    // render boundary (react-hooks/set-state-in-effect, usePebbleDrafts style).
    void (async () => {
      await Promise.resolve()
      if (cancelled) return
      try {
        const inv = await createInvite(false)
        if (cancelled) return
        setInvite(inv)
        setFailed(false)
      } catch (err) {
        if (cancelled) return
        console.error("[connections] invite create failed", err)
        setFailed(true)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [ready, createInvite])

  // `invite` is only ever set client-side (in the effect above), so this never
  // touches `window` during SSR. The client composes the URL from the origin —
  // there is no canonical-URL config in the web app (design D11).
  const url = invite ? `${window.location.origin}/invite/${invite.token}` : null

  useEffect(() => {
    if (!url) return
    let cancelled = false
    toDataURL(url, { width: 240, margin: 1 })
      .then((dataUrl) => {
        if (!cancelled) setQr(dataUrl)
      })
      .catch((err: unknown) => {
        // The link + copy/share remain fully functional without the QR.
        console.error("[connections] QR render failed", err)
      })
    return () => {
      cancelled = true
    }
  }, [url])

  const handleCopy = async () => {
    if (!url) return
    try {
      await navigator.clipboard.writeText(url)
      toast.success(t("copied"))
    } catch (err) {
      console.error("[connections] copy failed", err)
      toast.error(t("copyError"))
    }
  }

  const handleShare = async () => {
    if (!url) return
    try {
      await navigator.share({ url })
    } catch (err) {
      // AbortError = the user dismissed the share sheet — not a failure.
      if (err instanceof DOMException && err.name === "AbortError") return
      console.error("[connections] share failed", err)
      toast.error(t("shareError"))
    }
  }

  const handleRotate = () => {
    setRotating(true)
    setQr(null)
    void createInvite(true)
      .then((inv) => {
        setInvite(inv)
      })
      .catch((err: unknown) => {
        console.error("[connections] invite rotate failed", err)
        toast.error(t("rotateError"))
      })
      .finally(() => {
        setRotating(false)
      })
  }

  const canShare = typeof navigator !== "undefined" && typeof navigator.share === "function"

  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-sm font-medium text-muted-foreground">{t("inviteTitle")}</h2>

      {failed ? (
        <p role="alert" className="py-6 text-center text-sm text-destructive">
          {t("inviteError")}
        </p>
      ) : !invite || !url ? (
        <div className="flex justify-center py-6" aria-live="polite">
          <Loader2
            className="size-5 animate-spin text-muted-foreground"
            aria-label={t("inviteLoading")}
          />
        </div>
      ) : (
        <div className="flex flex-col items-center gap-3 rounded-2xl bg-accent p-4">
          {/* White backing keeps the QR scannable in dark mode. */}
          {qr && (
            // eslint-disable-next-line @next/next/no-img-element -- data: URL, no optimizer benefit
            <img src={qr} alt={t("qrAlt")} className="size-40 rounded-lg bg-white p-2" />
          )}
          <p className="w-full rounded-lg border border-muted bg-background px-3 py-2 text-center text-xs break-all text-muted-foreground select-all">
            <span className="sr-only">{t("inviteUrlAria")}: </span>
            {url}
          </p>
          <p className="text-xs text-muted-foreground">
            {t("inviteExpires", {
              date: formatDate(invite.expiresAt, { day: "numeric", month: "long" }),
            })}
          </p>
          <div className="flex w-full flex-wrap justify-center gap-2">
            <Button variant="outline" size="sm" onClick={handleCopy}>
              <Copy className="size-4" aria-hidden />
              {t("copy")}
            </Button>
            {canShare && (
              <Button variant="outline" size="sm" onClick={handleShare}>
                <Share2 className="size-4" aria-hidden />
                {t("share")}
              </Button>
            )}
            <ConfirmDialog
              trigger={
                <Button variant="ghost" size="sm" disabled={rotating}>
                  <RotateCcw className="size-4" aria-hidden />
                  {t("newLink")}
                </Button>
              }
              title={t("newLinkTitle")}
              description={t("newLinkDescription")}
              confirmLabel={t("newLinkConfirm")}
              cancelLabel={t("newLinkCancel")}
              onConfirm={handleRotate}
            />
          </div>
        </div>
      )}
    </section>
  )
}
