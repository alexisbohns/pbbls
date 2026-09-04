"use client"

import { useCallback, useEffect, useState } from "react"
import { useRouter } from "next/navigation"
import { useTranslations } from "next-intl"
import { Trophy } from "lucide-react"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { useAchievementCopy } from "@/lib/i18n"
import { subscribeAchievementMoment, type AchievementMoment } from "@/lib/activity/achievement-moment"
import type { Achievement } from "@/lib/types"

type Card = {
  achievement: Achievement | undefined
  slug: string
  karmaGranted: number
}

/**
 * The unlock moment (D13): one card per newly unlocked badge, chained in
 * catalog order, advanced by tapping through. Mounted once in the root layout;
 * `fireAchievementCheck` pushes into it from the mutation paths.
 *
 * The karma each badge paid rides in its own card, so this and the karma
 * pastille never announce the same number twice — the pill keeps firing
 * independently for the pebble's own karma.
 */
export function AchievementMomentHost() {
  const router = useRouter()
  const t = useTranslations("activity")
  const resolveCopy = useAchievementCopy()
  const [cards, setCards] = useState<Card[]>([])
  const [index, setIndex] = useState(0)

  useEffect(() => {
    return subscribeAchievementMoment((moment: AchievementMoment) => {
      const bySlug = new Map(moment.catalog.map((a) => [a.slug, a]))
      const next = moment.results
        .map((r) => ({
          achievement: bySlug.get(r.slug),
          slug: r.slug,
          karmaGranted: r.karmaGranted,
        }))
        // Chain in the order the ladder reads, so a multi-tier unlock walks up
        // rather than arriving shuffled. Unknown slugs (a check racing a
        // catalog change) sort last and render from the slug alone.
        .sort((a, b) => (a.achievement?.sortOrder ?? Infinity) - (b.achievement?.sortOrder ?? Infinity))
      setCards(next)
      setIndex(0)
    })
  }, [])

  const close = useCallback(() => {
    setCards([])
    setIndex(0)
  }, [])

  const advance = useCallback(() => {
    setIndex((i) => {
      if (i + 1 >= cards.length) {
        setCards([])
        return 0
      }
      return i + 1
    })
  }, [cards.length])

  const card = cards[index]
  if (!card) return null

  const copy = card.achievement ? resolveCopy(card.achievement) : undefined
  const title = copy?.title ?? card.slug
  const isLast = index + 1 >= cards.length

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        // Dismissal is never blocking: tapping outside or pressing Escape
        // skips the rest of the queue.
        if (!open) close()
      }}
    >
      <DialogContent className="text-center">
        <DialogHeader className="place-items-center">
          <span
            aria-hidden
            className="mb-2 grid size-20 place-items-center rounded-full bg-amber-100 text-amber-600 dark:bg-amber-950 dark:text-amber-300"
          >
            <Trophy className="size-10" />
          </span>
          <p className="text-sm font-medium text-muted-foreground">
            {t("achievementUnlockedEyebrow")}
          </p>
          <DialogTitle>{title}</DialogTitle>
          {copy?.description ? (
            <DialogDescription>{copy.description}</DialogDescription>
          ) : null}
        </DialogHeader>

        {card.karmaGranted > 0 ? (
          <p className="text-base font-semibold text-amber-600 dark:text-amber-300">
            {t("amount", { amount: card.karmaGranted })}
          </p>
        ) : null}

        {cards.length > 1 ? (
          <p className="text-xs text-muted-foreground">
            {t("achievementMomentProgress", { current: index + 1, total: cards.length })}
          </p>
        ) : null}

        <DialogFooter className="sm:justify-center">
          {isLast ? (
            <>
              <Button variant="outline" onClick={close}>
                {t("achievementMomentDone")}
              </Button>
              <Button
                onClick={() => {
                  close()
                  router.push("/achievements")
                }}
              >
                {t("viewAchievements")}
              </Button>
            </>
          ) : (
            <Button onClick={advance}>{t("achievementMomentNext")}</Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
