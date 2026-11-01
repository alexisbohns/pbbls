"use client"

import type { ReactNode } from "react"
import { Tag } from "lucide-react"
import { Collection as CollectionIcon, Heart } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
import { useTranslations } from "next-intl"
import type { Collection, MomentPebble } from "@/lib/types"
import { useDomains, type DomainRow } from "@/lib/data/useDomains"
import { useDomainLocalized } from "@/lib/i18n"
import { useSelectedEmotionDisplay } from "@/components/record/EmotionPicker"
import { cn } from "@/lib/utils"

type MomentReadTilesProps = {
  pebble: MomentPebble
  collections: Collection[]
}

function ReadTile({ icon, children }: { icon: ReactNode; children: ReactNode }) {
  return (
    <li className="flex flex-col items-center justify-center gap-2 rounded-2xl bg-surface px-3 py-4">
      <span className="text-muted-foreground" aria-hidden>
        {icon}
      </span>
      <span className="text-center text-sm font-medium text-foreground line-clamp-2">{children}</span>
    </li>
  )
}

function DomainName({ domain }: { domain: DomainRow }) {
  return <>{useDomainLocalized(domain).name}</>
}

/**
 * The pebble's emotion, domains and collections, read-only (PD-W2): emotion
 * always, domains or a muted "No domain", collections only when it has any.
 * Editing them is Edit pebble's job.
 */
export function MomentReadTiles({ pebble, collections }: MomentReadTilesProps) {
  const t = useTranslations("moment.read")
  const tEmotion = useTranslations("record.emotion")
  const emotion = useSelectedEmotionDisplay(pebble.emotion_id)
  const { rows } = useDomains()
  const domains = rows.filter((d) => pebble.domain_ids.includes(d.id))
  const inCollections = collections.filter((c) => pebble.collection_ids.includes(c.id))

  return (
    <ul className={cn("grid gap-3", inCollections.length > 0 ? "grid-cols-3" : "grid-cols-2")}>
      <ReadTile icon={emotion ? <span className="text-lg leading-none">{emotion.emoji}</span> : <Heart className={PBBLS_ICON_SIZE.row} />}>
        {emotion ? emotion.name : tEmotion("label")}
      </ReadTile>
      <ReadTile icon={<Tag className="size-5" />}>
        {domains.length > 0 ? (
          domains.map((d, i) => (
            <span key={d.id}>
              {i > 0 && ", "}
              <DomainName domain={d} />
            </span>
          ))
        ) : (
          <span className="text-muted-foreground">{t("noDomain")}</span>
        )}
      </ReadTile>
      {inCollections.length > 0 && (
        <ReadTile icon={<CollectionIcon className={PBBLS_ICON_SIZE.row} />}>{inCollections.map((c) => c.name).join(", ")}</ReadTile>
      )}
    </ul>
  )
}
