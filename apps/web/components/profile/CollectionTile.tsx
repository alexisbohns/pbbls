"use client"

import { useTranslations } from "next-intl"
import { Layers, Plus } from "lucide-react"
import { cn } from "@/lib/utils"

type CollectionTileProps =
  | { variant: "filled"; name: string; count: number }
  | { variant: "empty" }

/**
 * A tile in the Profile Collections carousel — web port of the iOS
 * `ProfileCollectionCard`. `filled` shows a real collection (solid border);
 * `empty` is the dashed-border "New collection" placeholder. Presentational
 * only — the parent wraps it in a link (filled) or create dialog (empty).
 */
export function CollectionTile(props: CollectionTileProps) {
  const t = useTranslations("profile")
  const tCollections = useTranslations("collections")
  const empty = props.variant === "empty"

  return (
    <div
      className={cn(
        "flex h-full w-36 flex-col gap-2.5 rounded-2xl border p-4",
        empty ? "border-dashed border-border" : "border-border",
      )}
    >
      <div className="flex size-[34px] items-center justify-center rounded-[10px] bg-accent">
        {empty ? (
          <Plus className="size-3.5 text-primary" aria-hidden />
        ) : (
          <Layers className="size-3.5 text-primary" aria-hidden />
        )}
      </div>
      <div className="flex min-w-0 flex-col gap-1">
        <span className="truncate text-base font-semibold text-foreground">
          {empty ? t("newCollection") : props.name}
        </span>
        {!empty && (
          <span className="text-sm text-muted-foreground">
            {tCollections("pebbleCount", { count: props.count })}
          </span>
        )}
      </div>
    </div>
  )
}
