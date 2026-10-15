"use client"

import { Sparkle } from "lucide-react"
import { useTranslations } from "next-intl"
import type { CompleteDraft } from "@/lib/data/moment-payloads"
import { momentDisplayName } from "@/lib/data/moment-row"
import { successLook } from "@/lib/record/complete"
import type { Moment } from "@/lib/types"
import { Button } from "@/components/ui/button"
import { PebbleFramed } from "@/components/pebble/PebbleFramed"

type CompleteSuccessStepProps = {
  /** The moment as the store holds it now, after the reload. */
  moment: Moment | undefined
  /** The moment as it was when the write went out: the fallback if the reload failed. */
  before: Moment
  draft: CompleteDraft
  karmaDelta: number
  onExit: () => void
}

/**
 * The pebble, drawn on (R12): the stone with its derived glyph (a bare stone
 * when none), the moment's name or "Untitled", and what it earned. The karma
 * pill is suppressed on this path (`onKarmaEarned`): the amount is on screen.
 */
export function CompleteSuccessStep({
  moment,
  before,
  draft,
  karmaDelta,
  onExit,
}: CompleteSuccessStepProps) {
  const t = useTranslations("record.flow.success")
  const tComplete = useTranslations("record.complete")
  const tMoment = useTranslations("moment")

  const { look, reloaded } = successLook(moment, before, draft)
  const name = momentDisplayName((moment ?? before).name)
  const karma = reloaded ? karmaDelta : 0

  return (
    <div className="flex min-h-0 flex-1 flex-col items-center justify-center gap-8 py-8">
      <PebbleFramed look={look} animateIn className="w-56" />

      <div className="flex flex-col items-center gap-2 text-center">
        {name ? (
          // User-authored, so never localized.
          <h2 className="font-heading text-xl font-semibold text-balance">{name}</h2>
        ) : (
          <h2 className="font-heading text-xl font-semibold italic text-muted-foreground">
            {tMoment("untitled")}
          </h2>
        )}
        {karma > 0 && (
          <p className="flex items-center gap-1.5 text-sm font-medium">
            <Sparkle className="size-4 text-primary" aria-hidden />
            <span aria-hidden>{t("karma", { count: karma })}</span>
            <span className="sr-only">{tComplete("karmaAria", { count: karma })}</span>
          </p>
        )}
      </div>

      <Button onClick={onExit} className="w-full">
        {t("exit")}
      </Button>
    </div>
  )
}
