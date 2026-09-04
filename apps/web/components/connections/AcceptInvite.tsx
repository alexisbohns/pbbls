"use client"

import { useEffect, useState } from "react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { useTranslations } from "next-intl"
import { toast } from "sonner"
import { useAuth } from "@/lib/data/auth-context"
import { useConnections } from "@/lib/data/useConnections"
import { usePendingInvite } from "@/lib/hooks/usePendingInvite"
import type { ConnectionInvitePreview } from "@/lib/data/invite-api"
import { PeerGlyphIcon } from "@/components/connections/PeerGlyphIcon"
import { Button } from "@/components/ui/button"

type AcceptInviteProps = {
  preview: ConnectionInvitePreview
  token: string
}

/**
 * The `/invite/[token]` accept flow (M49, design D12). Server-rendered preview
 * comes in as a prop; everything stateful (auth, pending token, the explicit
 * accept tap) happens here. Accept is never fired implicitly — consent is a
 * deliberate act on both funnels (fresh sign-up and signed-in).
 */
export function AcceptInvite({ preview, token }: AcceptInviteProps) {
  const router = useRouter()
  const t = useTranslations("connections")
  const { isAuthenticated, isLoading, isProfileLoading, profile } = useAuth()
  const { acceptInvite } = useConnections()
  const { setToken, clear } = usePendingInvite()
  const [accepting, setAccepting] = useState(false)
  // Accept-time terminal errors override the (stale) server preview: the
  // invite can die between SSR and the tap.
  const [terminal, setTerminal] = useState<"dead" | "own" | null>(null)

  useEffect(() => {
    if (preview.status !== "valid") {
      // A dead link is terminal — drop any parked token so the post-auth
      // redirect can't bounce the user back to a dead page.
      clear()
      return
    }
    if (isLoading || isProfileLoading) return
    if (!isAuthenticated) {
      // Park the token so it survives the sign-up/sign-in round-trip (D12).
      setToken(token)
      return
    }
    if (profile?.onboarding_completed) {
      // The parked token has done its job once an onboarded user is looking
      // at this page — accepting still requires the explicit tap below.
      // Clearing here (never in the redirect) keeps navigating away from this
      // page from bouncing straight back. An authed-but-un-onboarded visitor
      // keeps the token: OnboardingGate is about to route them away, and the
      // token is what brings them back afterwards.
      clear()
    }
  }, [preview.status, isLoading, isProfileLoading, isAuthenticated, profile, token, setToken, clear])

  const handleAccept = async () => {
    setAccepting(true)
    try {
      const result = await acceptInvite(token)
      clear()
      // Repeat accept SUCCEEDS with alreadyConnected — a success state, never
      // an error (re-scans and double-taps are the normal case, D5).
      toast.success(
        result.alreadyConnected
          ? t("alreadyConnectedToast", { name: result.peer.displayName })
          : t("acceptedToast", { name: result.peer.displayName }),
      )
      router.replace("/connections")
    } catch (err) {
      console.error("[connections] accept failed", err)
      const message = err instanceof Error ? err.message.toLowerCase() : ""
      if (message.includes("cannot_accept_own_invite")) {
        clear()
        setTerminal("own")
      } else if (message.includes("invite_not_found") || message.includes("invite_expired")) {
        // A block in either direction also surfaces as invite_expired — the
        // dead state is deliberately indistinguishable (D5).
        clear()
        setTerminal("dead")
      } else {
        toast.error(t("acceptError"))
        setAccepting(false)
      }
    }
  }

  // Expired and not_found share one dark state: a withdrawn token goes fully
  // dark, with no inviter data to show (D4).
  if (preview.status !== "valid" || terminal === "dead") {
    return (
      <section className="flex flex-col items-center justify-center gap-3 px-6 py-24 text-center">
        <h1 className="text-2xl font-semibold tracking-tight">{t("deadTitle")}</h1>
        <p className="max-w-sm text-sm text-muted-foreground">{t("deadBody")}</p>
      </section>
    )
  }

  if (terminal === "own") {
    return (
      <section className="flex flex-col items-center justify-center gap-3 px-6 py-24 text-center">
        <h1 className="text-2xl font-semibold tracking-tight">{t("ownInviteTitle")}</h1>
        <p className="max-w-sm text-sm text-muted-foreground">{t("ownInviteBody")}</p>
        <Button variant="outline" className="mt-3" render={<Link href="/connections" />}>
          {t("title")}
        </Button>
      </section>
    )
  }

  const { inviter } = preview
  const name = inviter.displayName

  return (
    <section className="flex flex-col items-center justify-center px-6 py-24 text-center">
      {inviter.glyph ? (
        <PeerGlyphIcon glyph={inviter.glyph} className="size-20 text-foreground" />
      ) : (
        <span
          aria-hidden
          className="flex size-20 items-center justify-center rounded-full bg-accent text-2xl font-semibold text-muted-foreground"
        >
          {name.charAt(0).toUpperCase() || "?"}
        </span>
      )}
      <h1 className="mt-6 text-3xl font-bold tracking-tight">
        {t("acceptTitle", { name })}
      </h1>
      <p className="mt-3 max-w-sm text-muted-foreground">{t("acceptSubtitle")}</p>

      {/* Hold the affordances until the session state is known — otherwise the
          sign-in CTAs flash before swapping to the Accept button. */}
      {isLoading || isProfileLoading ? null : isAuthenticated ? (
        <Button size="lg" className="mt-8" onClick={handleAccept} disabled={accepting}>
          {accepting ? t("accepting") : t("acceptButton")}
        </Button>
      ) : (
        <div className="mt-8 flex w-full max-w-xs flex-col gap-3">
          <p className="text-sm text-muted-foreground">{t("signInPrompt")}</p>
          <Button
            size="lg"
            render={<Link href={`/register?next=${encodeURIComponent(`/invite/${token}`)}`} />}
          >
            {t("signUp")}
          </Button>
          <Button
            variant="outline"
            size="lg"
            render={<Link href={`/login?next=${encodeURIComponent(`/invite/${token}`)}`} />}
          >
            {t("logIn")}
          </Button>
        </div>
      )}
    </section>
  )
}
