import type { ReactNode } from "react"
import type { EntryType } from "@/lib/utils/spine/entry-type"

type SpineEntryProps = {
  titleId: string
  /** The pebble's name; empty for a moment with none (shown as `untitled`). */
  name: string
  untitled: string
  type: Pick<EntryType, "fontWeight" | "italic" | "letterSpacing">
  time: string
  dateTime: string
  emotion: string
  people: ReactNode
  openLabel: string
  onOpen: () => void
}

/**
 * A pebble with no photo, set as type (#1052): no box, the name at the rank
 * its intensity earns and the cut its valence earns, then the time and the
 * emotion in small caps, then who was there. The name's size is `--fs`, set
 * on the pebble so the stone can centre on its first line.
 */
export function SpineEntry({
  titleId,
  name,
  untitled,
  type,
  time,
  dateTime,
  emotion,
  people,
  openLabel,
  onOpen,
}: SpineEntryProps) {
  return (
    <div className="relative z-2">
      <h3
        id={titleId}
        className={
          name
            ? "max-w-[22ch] font-spine text-(length:--fs) leading-[1.12] text-balance text-(--spine-ink)"
            : "max-w-[22ch] font-spine text-(length:--fs) leading-[1.12] italic text-(--spine-ink-3)"
        }
        style={
          name
            ? { fontWeight: type.fontWeight, fontStyle: type.italic ? "italic" : undefined, letterSpacing: `${type.letterSpacing}em` }
            : { fontWeight: 340 }
        }
      >
        {name || untitled}
      </h3>
      <p className="mt-2 font-spine-sc text-xs leading-[1.2] font-semibold tracking-[.12em] text-(--spine-ink-2) tabular-nums spread:text-[12.5px]">
        <time
          dateTime={dateTime}
          className="font-spine-office text-[13px] font-[560] tracking-[.06em] text-(--spine-ink) spread:text-sm"
        >
          {time}
        </time>
        <span aria-hidden className="text-(--spine-ink-3)">
          {" · "}
        </span>
        <span className="lowercase text-(--spine-spot)">{emotion}</span>
      </p>
      {people}
      {/* The whole entry opens the pebble. An overlay rather than a wrapping
          button, which could not hold the heading. */}
      <button
        type="button"
        onClick={onOpen}
        aria-label={openLabel}
        className="absolute -inset-1.5 cursor-pointer rounded-md focus-visible:outline-[1.5px] focus-visible:outline-(--spine-ink)"
      />
    </div>
  )
}
