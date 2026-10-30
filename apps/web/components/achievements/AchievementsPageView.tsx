"use client"

import { useCallback } from "react"
import { useTranslations } from "next-intl"
import { toast } from "sonner"
import { PageHeader } from "@/components/layout/PageHeader"
import { Button } from "@/components/ui/button"
import { useAchievementPalettes } from "@/lib/data/useAchievementPalettes"
import { useAchievementsPage } from "@/lib/data/useAchievementsPage"
import { useAchievementsWords } from "@/lib/i18n/useAchievementsWords"
import type { CeremonyStack } from "@/lib/types"
import { AchievementsPageContent } from "./page/AchievementsPageContent"

/**
 * You → Achievements (#1308, spec 2026-10-29 D1): the days kept, then
 * Explorer, Emotions, Domains, Pebbles and one card per year, drawn from
 * `get_achievements`. The kept copy shows at once and the fresh read replaces
 * it; a failed read keeps what is shown, and only a first visit with nothing
 * kept and no answer shows the error. Every number, order and flag is the
 * server's. A ready mark pulls its ceremony, which plays in the unlock moment;
 * the screen-open check never celebrates.
 */
export function AchievementsPageView() {
  const t = useTranslations("achievements")
  const words = useAchievementsWords()
  const { page, loading, failed, pulling, pull, retry } = useAchievementsPage()
  const palettes = useAchievementPalettes()

  const onPull = useCallback(
    (stack: CeremonyStack) => {
      void pull(stack, { title: words.ceremonyTitle(stack), description: words.ceremonyBody(stack) }).then((outcome) => {
        // A pull that failed says so briefly; one already pulled, not ready or busy says nothing.
        if (outcome.kind === "failed") toast.error(words.ceremonyError())
      })
    },
    [pull, words],
  )

  return (
    <div className="min-h-dvh bg-spine-ground font-spine text-spine-ink">
      <div className="mx-auto w-full max-w-md px-4 pt-4 pb-24 md:max-w-3xl md:px-6 md:pt-8">
        <PageHeader title={t("title")} backHref="/profile" />
        {page ? (
          <AchievementsPageContent page={page} palettes={palettes} pulling={pulling} onPull={onPull} />
        ) : loading ? (
          <p aria-live="polite" className="text-sm text-spine-ink-2">
            {t("loading")}
          </p>
        ) : failed ? (
          <div role="alert" className="flex flex-col items-start gap-2 rounded-[11px] bg-spine-stock p-4">
            <p className="font-semibold">{words.error.title()}</p>
            <p className="text-sm text-spine-ink-2">{words.error.body()}</p>
            <Button variant="outline" onClick={retry}>
              {words.error.retry()}
            </Button>
          </div>
        ) : null}
      </div>
    </div>
  )
}
