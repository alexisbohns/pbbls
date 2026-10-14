"use client"

import { useState, type ReactNode } from "react"
import { useTranslations } from "next-intl"
import type { Collection, Mark, Moment, Soul } from "@/lib/types"
import { stoneLookOf } from "@/lib/data/moment-row"
import { useFormatPeekDate } from "@/lib/i18n"
import { Button } from "@/components/ui/button"
import { VISIBILITY_ICONS } from "@/components/record/VisibilityMenu"
import { MomentDeleteDialog } from "@/components/pebble/MomentDeleteDialog"
import { MomentName } from "@/components/pebble/MomentName"
import { MomentReadTiles } from "@/components/pebble/MomentReadTiles"
import { MomentReadToolbar } from "@/components/pebble/MomentReadToolbar"
import { PebbleDetailSoulsGrid } from "@/components/pebble/PebbleDetailSoulsGrid"
import { PebbleFramed } from "@/components/pebble/PebbleFramed"

type MomentReadProps = {
  moment: Moment
  souls: Soul[]
  collections: Collection[]
  marks: Mark[]
  /** Leaves to another screen: the page pushes, the peek closes first. */
  onNavigate: (href: string) => void
  /** The peek's Close, or the page's way back. */
  onClose?: () => void
  /** The moment was deleted: the page leaves for the Path, the peek closes. */
  onDeleted: () => void
  /** The id of the moment's name heading, for a host that names itself after it. */
  titleId?: string
}

/**
 * The read page of a moment (#1050, R7, PD-W2), on `/pebble/[id]` and in the
 * Path's peek. Read-only: every edit goes through Edit moment or Edit pebble.
 * A moment carrying the caller's pebble shows its privacy, tiles and souls; a
 * bare one shows a blank stone and "Make it a pebble".
 */
export function MomentRead({
  moment,
  souls,
  collections,
  marks,
  onNavigate,
  onClose,
  onDeleted,
  titleId,
}: MomentReadProps) {
  const t = useTranslations("moment.read")
  const tVisibility = useTranslations("record.visibility")
  const formatPeekDate = useFormatPeekDate()
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [deleting, setDeleting] = useState(false)

  const pebble = moment.pebble
  const photo = moment.instants[0]
  const description = moment.description?.trim()
  const matchedSouls = (pebble?.soul_ids ?? [])
    .map((id) => souls.find((s) => s.id === id))
    .filter((s): s is Soul => s !== undefined)

  const stone = (className: string, animateIn: boolean): ReactNode => (
    <PebbleFramed look={stoneLookOf(moment)} className={className} animateIn={animateIn} />
  )

  const VisibilityIcon = pebble ? VISIBILITY_ICONS[pebble.visibility] : null

  return (
    <article>
      <MomentReadToolbar
        moment={moment}
        busy={deleting}
        onEditMoment={() => onNavigate(`/pebble/${moment.id}/edit`)}
        onEditPebble={() => onNavigate(`/pebble/${moment.id}/edit-pebble`)}
        onDelete={() => setDeleteOpen(true)}
        onClose={onClose}
      />

      <div className="mt-8 mb-4">
        {photo ? (
          <div className="relative mx-auto w-[240px]">
            {/* eslint-disable-next-line @next/next/no-img-element -- signed storage URL, next/image optimization not applicable */}
            <img src={photo} alt={t("photoAlt")} className="aspect-square w-full scale-90 -rotate-4 rounded-2xl object-cover" />
            {stone("absolute -right-4 -top-4 size-[100px] rotate-7", true)}
          </div>
        ) : (
          stone("mx-auto size-[100px]", true)
        )}
      </div>

      <MomentName
        as="h1"
        id={titleId}
        name={moment.name}
        className="block text-center font-heading text-2xl font-semibold text-foreground"
      />

      <p className="mt-2 text-center text-xs font-medium uppercase tracking-wider text-muted-foreground">
        <time dateTime={moment.happened_at}>{formatPeekDate(moment.happened_at)}</time>
      </p>

      {pebble && VisibilityIcon ? (
        <>
          <p className="mt-4 flex justify-center">
            <span className="inline-flex items-center gap-1.5 rounded-full bg-surface px-3 py-1 text-xs font-medium text-muted-foreground">
              <VisibilityIcon className="size-3.5" aria-hidden />
              <span aria-hidden>{tVisibility(pebble.visibility)}</span>
              <span className="sr-only">{tVisibility("aria", { grade: tVisibility(pebble.visibility) })}</span>
            </span>
          </p>
          <div className="mt-6">
            <MomentReadTiles pebble={pebble} collections={collections} />
          </div>
        </>
      ) : (
        <div className="mt-6 flex justify-center">
          <Button onClick={() => onNavigate(`/record/complete/${moment.id}`)}>{t("makeItAPebble")}</Button>
        </div>
      )}

      {description && (
        <p className="mt-6 whitespace-pre-wrap text-base leading-[1.4] text-foreground">{description}</p>
      )}

      {matchedSouls.length > 0 && (
        <div className="mt-6">
          <PebbleDetailSoulsGrid souls={matchedSouls} marks={marks} />
        </div>
      )}

      <MomentDeleteDialog
        moment={moment}
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        onDeleted={onDeleted}
        onPendingChange={setDeleting}
      />
    </article>
  )
}
