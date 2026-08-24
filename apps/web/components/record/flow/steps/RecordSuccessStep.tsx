"use client"

import { Sparkle } from "lucide-react"
import { useTranslations } from "next-intl"
import type { Mark, Pebble } from "@/lib/types"
import { Button } from "@/components/ui/button"
import { PebbleFramed } from "@/components/pebble/PebbleFramed"

type RecordSuccessStepProps = {
  pebble: Pebble
  mark: Mark | null
  karmaDelta: number
  onExit: () => void
}

/**
 * Step 10 — the pebble, drawn on.
 *
 * Reuses `PebbleFramed` with its animate-in, the same composition the detail
 * view uses: the outline silhouette behind the composed render, which already
 * honours reduced motion.
 *
 * On soft success (`render_svg` null) the framed pebble falls back to the
 * client engine rather than blocking — the pebble exists and the user should be
 * told so.
 *
 * The karma pill is suppressed on this path (see `RecordFlow`): the amount is
 * already on screen and a capsule over it is redundant.
 */
export function RecordSuccessStep({ pebble, mark, karmaDelta, onExit }: RecordSuccessStepProps) {
  const t = useTranslations("record.flow.success")

  return (
    <div className="flex min-h-0 flex-1 flex-col items-center justify-center gap-8 py-8">
      <PebbleFramed pebble={pebble} mark={mark} tier="detail" animateIn className="w-56" />

      <div className="flex flex-col items-center gap-2 text-center">
        {/* User-authored, so never localized. */}
        <h2 className="font-heading text-xl font-semibold text-balance">{pebble.name}</h2>
        {karmaDelta > 0 && (
          <p className="flex items-center gap-1.5 text-sm font-medium">
            <Sparkle className="size-4 text-primary" aria-hidden />
            {t("karma", { count: karmaDelta })}
          </p>
        )}
      </div>

      <Button onClick={onExit} className="w-full">
        {t("exit")}
      </Button>
    </div>
  )
}
