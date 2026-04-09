import { Users, Image as ImageIcon, PenLine } from "lucide-react"
import type { Pebble, Soul } from "@/lib/types"

type PeekBottomBarProps = {
  pebble: Pebble
  souls: Soul[]
  onOpenSoulsSheet: () => void
  onTriggerPhotoUpload: () => void
}

export function PeekBottomBar({
  pebble,
  souls,
  onOpenSoulsSheet,
  onTriggerPhotoUpload,
}: PeekBottomBarProps) {
  const matchedSouls = pebble.soul_ids
    .map((id) => souls.find((s) => s.id === id))
    .filter((s): s is Soul => s !== undefined)

  return (
    <footer className="mt-6 flex items-center gap-4 border-t border-border pt-4">
      {matchedSouls.length > 0 ? (
        matchedSouls.map((soul) => (
          <button
            key={soul.id}
            type="button"
            onClick={onOpenSoulsSheet}
            className="inline-flex items-center gap-1.5 text-sm text-muted-foreground transition-colors hover:text-foreground"
          >
            <Users className="size-4" aria-hidden />
            {soul.name}
          </button>
        ))
      ) : (
        <button
          type="button"
          onClick={onOpenSoulsSheet}
          className="inline-flex items-center gap-1.5 text-sm text-muted-foreground transition-colors hover:text-foreground"
        >
          <Users className="size-4" aria-hidden />
          Souls
        </button>
      )}

      {pebble.instants.length === 0 && (
        <button
          type="button"
          onClick={onTriggerPhotoUpload}
          className="inline-flex items-center gap-1.5 text-sm text-muted-foreground transition-colors hover:text-foreground"
        >
          <ImageIcon className="size-4" aria-hidden />
          Picture
        </button>
      )}

      <button
        type="button"
        disabled
        className="inline-flex items-center gap-1.5 text-sm text-muted-foreground opacity-50"
        aria-label="Write a card"
      >
        <PenLine className="size-4" aria-hidden />
        Write a card
      </button>
    </footer>
  )
}
