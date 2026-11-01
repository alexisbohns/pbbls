"use client"

import type { ReactNode } from "react"
import { useTranslations } from "next-intl"
import { ListItem } from "@/components/m3/ListItem"
import { PebbleFramed } from "@/components/pebble/PebbleFramed"
import { momentDisplayName, stoneLookOf } from "@/lib/data/moment-row"
import { useFormatDate } from "@/lib/i18n"
import type { CompletedMoment } from "@/lib/types"

/**
 * A completed moment as an M3 list item (#1384, two-area spec D6), in a
 * soul's or a collection's detail: its stone, its name over its day and time,
 * a link to the pebble.
 */
export function PebbleListItem({ moment, action }: { moment: CompletedMoment; action?: ReactNode }) {
  const tMoment = useTranslations("moment")
  const formatDate = useFormatDate()
  const name = momentDisplayName(moment.name) ?? tMoment("untitled")
  return (
    <ListItem
      href={`/pebble/${moment.id}`}
      leading={<PebbleFramed look={stoneLookOf(moment)} className="size-10" />}
      headline={name}
      supporting={formatDate(moment.happened_at, { dateStyle: "medium", timeStyle: "short" })}
      action={action}
    />
  )
}
